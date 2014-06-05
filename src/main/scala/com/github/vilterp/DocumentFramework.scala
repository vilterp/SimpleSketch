package com.github.vilterp

import javafx.collections.{FXCollections, ObservableList}
import javafx.beans.property.{StringProperty, SimpleObjectProperty, ObjectProperty}
import java.util.Date
import javafx.beans.binding.Bindings
import javafx.concurrent.{WorkerStateEvent, Task}
import javafx.event.{ActionEvent, EventType, Event, EventHandler}
import java.util.concurrent.Callable
import javafx.stage.{Modality, Stage, Window, FileChooser}
import java.io.File
import javafx.stage.FileChooser.ExtensionFilter
import javafx.scene.control.{Button, Label}
import javafx.geometry.Pos
import javafx.scene.layout.{VBox, HBox}
import javafx.scene.Scene

object DocumentPromptUtils {

  def promptForSavePath(parentWindow:Window, promptWindowTitle:String):Callable[Option[File]] =
    new Callable[Option[File]] {
      def call():Option[File] = {
        val chooser = new FileChooser
        chooser.setTitle(promptWindowTitle)
        chooser.showSaveDialog(parentWindow) match {
          case null => None
          case file => Some(file)
        }
      }
  }

  def promptForOpenPath(promptWindowTitle:String, extensionDesc:String,
                        extension:String):Option[File] = {
    val chooser = new FileChooser()
    chooser.setTitle(promptWindowTitle)
    chooser.getExtensionFilters.add(new ExtensionFilter(extensionDesc, "*." + extension))
    chooser.showOpenDialog(null) match {
      case null => None
      case file => Some(file)
    }
  }

  def showOnCloseDialog(callback:Function1[Boolean,Unit]) {
    // wtf, why don't dialogs come withthe SDK?
    // TODO: define this in FXML, make it not look like crap
    // TODO: move this out to a lib
    val dialogStage = new Stage()
    dialogStage.initModality(Modality.WINDOW_MODAL)

    val exitLabel = new Label("This document has unsaved changes.")
    exitLabel.setAlignment(Pos.BASELINE_CENTER)

    val saveBtn = new Button("Save")
    saveBtn.setOnAction(new EventHandler[ActionEvent]() {
      def handle(evt:ActionEvent) {
        dialogStage.close()
        callback(true)
      }
    })

    val dontSaveButton = new Button("Don't Save")
    dontSaveButton.setOnAction(new EventHandler[ActionEvent]() {
      def handle(evt:ActionEvent) {
        // TODO: don't save
        dialogStage.close()
        callback(false)
      }
    })

    val cancelBtn = new Button("Cancel")
    cancelBtn.setOnAction(new EventHandler[ActionEvent] {
      def handle(evt:ActionEvent) {
        dialogStage.close()
      }
    })

    val hBox = new HBox()
    hBox.setAlignment(Pos.BASELINE_CENTER)
    hBox.setSpacing(40.0)
    hBox.getChildren().addAll(saveBtn, dontSaveButton, cancelBtn)

    val vBox = new VBox()
    vBox.setSpacing(40.0)
    vBox.getChildren().addAll(exitLabel, hBox)

    dialogStage.setScene(new Scene(vBox))
    dialogStage.show()
  }

}

abstract class Document[T](savedAt:Option[File], val model:T) {

  val savedAtPath = new SimpleObjectProperty[Option[File]](savedAt)
  private val undoManager = new UndoManager[T]
  private val lastSavedState = new SimpleObjectProperty[UndoState[T]](undoManager.currentState.get())
  val isDirty = Bindings.equal(undoManager.currentState, lastSavedState).not()
  val canUndo = Bindings.equal(undoManager.currentState, undoManager.rootState).not()
  // TODO: canRedo...

  // wahhh I want EventStreams
  val onUndoProperty = new SimpleObjectProperty[EventHandler[UndoEvent[T]]]

  def extension:String

  // if canceled, returns false.
  // if not cancelled, saves to disk and sets undomanager's last saved state
  // to current state.
  def save(getPath:Callable[Option[File]]):Boolean = {
    if(savedAtPath.get().isEmpty) {
      getPath.call() match {
        case None => return false
        case Some(file) =>
          val withExt = if(file.getAbsolutePath.endsWith(extension))
            file
          else
            new File(file.getAbsolutePath + '.' + extension)
          savedAtPath.set(Some(withExt))
      }
    }
    saveToPath(savedAtPath.get().get)
    true
  }

  def saveAs(path:File) {
    savedAtPath.set(Some(path))
    saveToPath(savedAtPath.get().get)
  }

  private def saveToPath(toPath:File) {
    // actually save, in background thread
    val task = new Task[Unit]() {
      def call() {
        writeToDisk(toPath)
      }
    }
    task.setOnSucceeded(new EventHandler[WorkerStateEvent] {
      def handle(p1: WorkerStateEvent) {
        lastSavedState.set(undoManager.currentState.get())
      }
    })
    new Thread(task, "Saver").start()
  }

  def writeToDisk(toPath:File):Unit

  def accept(action:UndoableAction[T], undoAction:UndoableAction[T]) {
    undoManager.pushAction(action, undoAction)
  }

  def undo() {
    undoManager.popAction() match {
      case None => throw new Exception("can't undo")
      case Some(action) => onUndoProperty.get() match {
        case null =>
        case handler => handler.handle(new UndoEvent[T](action))
      }
    }
  }
}

class UndoManager[T] {

  // TODO: ObservableTree of undo states, maybe generalized jump-to-state? (path-finding problem)

  val rootState:UndoState[T] = new UndoRoot[T](new Date)
  val currentState:ObjectProperty[UndoState[T]] = new SimpleObjectProperty(rootState)

  def pushAction(action:UndoableAction[T], undoAction:UndoableAction[T]) {
    val latestState = currentState.get().addDescendant(action, undoAction)
    currentState.setValue(latestState)
  }

  /**
  * @return the undo action
  */
  def popAction():Option[UndoableAction[T]] =
    currentState.get() match {
      case _:UndoRoot[T] => None
      case cs:UndoNode[T] =>
        val popped = cs.undoAction
        currentState.setValue(cs.prev)
        Some(popped)
    }

}

case class UndoEvent[T](undoAction:UndoableAction[T]) extends Event(EventType.ROOT)

abstract class UndoableAction[T](document:Document[T])

abstract class UndoState[T](datetime:Date) {

  val descendants:ObservableList[UndoState[T]] = FXCollections.observableArrayList()

  def addDescendant(action:UndoableAction[T], undoAction:UndoableAction[T]):UndoNode[T] = {
    val newState = new UndoNode[T](new Date, this, action, undoAction)
    descendants.add(newState)
    newState
  }

}

case class UndoRoot[T](datetime:Date) extends UndoState[T](datetime)

case class UndoNode[T](datetime:Date,
                       prev:UndoState[T],
                       producingAction:UndoableAction[T],
                       undoAction:UndoableAction[T])
                       extends UndoState[T](datetime)
