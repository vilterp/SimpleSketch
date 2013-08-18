package com.github.vilterp

import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.{FXMLLoader, Initializable, FXML}
import javafx.scene.control.{MenuBar, MenuItem}
import java.net.URL
import java.util.ResourceBundle
import javafx.scene.input.{KeyCombination, KeyCharacterCombination}
import javafx.event.{ActionEvent, EventHandler}
import javafx.beans.value.{ObservableValue, ChangeListener}
import javafx.stage.Stage

object MenuController {

  def create(editorController:EditorController):MenuController = {
    val loader = new FXMLLoader()
    val menuBar = loader.load(
        classOf[MenuController].getResource("/menu.fxml").openStream()).asInstanceOf[MenuBar]
    val menuController = loader.getController.asInstanceOf[MenuController]
    menuController.node = menuBar
    menuController.editorController.set(editorController)
    menuController
  }

}

class MenuController extends Initializable {

  val editorController = new SimpleObjectProperty[EditorController](null)
  var node:MenuBar = null

  @FXML
  var mItemNew:MenuItem = null

  @FXML
  var mItemSave:MenuItem = null

  @FXML
  var mItemClose:MenuItem = null

  @FXML
  var mItemOpen:MenuItem = null

  @FXML
  var mItemUndo:MenuItem = null

  @FXML
  var mItemRedo:MenuItem = null

  @FXML
  var mItemCut:MenuItem = null

  @FXML
  var mItemCopy:MenuItem = null

  @FXML
  var mItemPaste:MenuItem = null

  @FXML
  var mItemLine:MenuItem = null

  @FXML
  var mItemCircle:MenuItem = null

  def initialize(p1: URL, p2: ResourceBundle) {
    mItemNew.setAccelerator(new KeyCharacterCombination("n", KeyCombination.META_DOWN))
    mItemSave.setAccelerator(new KeyCharacterCombination("s", KeyCombination.META_DOWN))
    mItemUndo.setAccelerator(new KeyCharacterCombination("z", KeyCombination.META_DOWN))
    mItemRedo.setAccelerator(new KeyCharacterCombination("z", KeyCombination.META_DOWN, KeyCombination.SHIFT_DOWN))
    mItemCopy.setAccelerator(new KeyCharacterCombination("c", KeyCombination.META_DOWN))
    mItemPaste.setAccelerator(new KeyCharacterCombination("v", KeyCombination.META_DOWN))
    mItemCut.setAccelerator(new KeyCharacterCombination("x", KeyCombination.META_DOWN))
    // new
    mItemNew.setOnAction(new EventHandler[ActionEvent] {
      def handle(p1: ActionEvent) {
        Main.createWindow(SketchDocument.createEmpty(), new Stage())
      }
    })
    // open
    mItemOpen.setAccelerator(new KeyCharacterCombination("o", KeyCombination.META_DOWN))
    mItemOpen.setOnAction(new EventHandler[ActionEvent] {
      def handle(evt: ActionEvent) {
        DocumentPromptUtils.promptForOpenPath("Open Sketch",
          SketchDocument.DESC, SketchDocument.EXTENSION) match {
          case Some(path) =>
            // TODO: handle IO & parse errors (what happened to checked exceptions? Scala??)
            val document = SketchDocument.createFromFile(path)
            Main.createWindow(document, new Stage())
          case _ =>
        }
      }
    })
    // close
    mItemClose.setAccelerator(new KeyCharacterCombination("w", KeyCombination.META_DOWN))
    mItemClose.setOnAction(new EventHandler[ActionEvent] {
      def handle(p1: ActionEvent) {
        println("command-w pressed...")
      }
    })
    // Tool
    mItemLine.setAccelerator(new KeyCharacterCombination("l"))
    mItemLine.setOnAction(new EventHandler[ActionEvent] {
      def handle(p1: ActionEvent) {
        editorController.get.currentTool.set(Tool.LINE)
      }
    })
    mItemCircle.setAccelerator(new KeyCharacterCombination("c"))
    mItemCircle.setOnAction(new EventHandler[ActionEvent] {
      def handle(p1: ActionEvent) {
        editorController.get.currentTool.set(Tool.CIRCLE)
      }
    })
    // set up bindings to document state
    editorController.addListener(new ChangeListener[EditorController] {
      def changed(prop:ObservableValue[_ <: EditorController], oldval: EditorController, newEditor: EditorController) {
        val document = newEditor.document
        // save
        mItemSave.disableProperty().bind(document.isDirty.not())
        mItemSave.setOnAction(new EventHandler[ActionEvent] {
          def handle(p1: ActionEvent) {
            document.save(DocumentPromptUtils.promptForSavePath(editorController.get().window, "Save Sketch"))
          }
        })
        // undo
        mItemUndo.disableProperty().bind(document.canUndo.not())
        mItemUndo.setOnAction(new EventHandler[ActionEvent] {
          def handle(p1: ActionEvent) {
            // should never be called when it would throw an exception
            // cuz of disable binding
            document.undo()
          }
        })
        // TODO: bind redo
      }
    })
  }

}
