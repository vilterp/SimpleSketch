package com.github.vilterp

import javafx.application.{ConditionalFeature, Platform, Application}
import javafx.stage.{WindowEvent, StageStyle, Stage}
import javafx.scene.layout.AnchorPane
import javafx.fxml.FXMLLoader
import javafx.scene.{Group, Scene}
import java.util.logging.{Level, Logger}
import javafx.scene.control.{Menu, MenuBar}
import javafx.event.{EventType, EventHandler}
import javafx.scene.input.{KeyCode, KeyEvent}
import javafx.beans.binding.Bindings
import java.util.concurrent.Callable

object Main {

  def main(args:Array[String]) {
    Application.launch(classOf[Main], args:_*)
  }

  def createWindow(document:SketchDocument, stage:Stage) {
    val root = new Group()
    val editorController = new EditorController(document, stage)
    val menuController = MenuController.create(editorController)
    root.getChildren.add(menuController.node)
    root.getChildren.add(editorController)

    val scene: Scene = new Scene(root)
    stage.setScene(scene)
    // bind title
    val title = Bindings.createStringBinding(new Callable[String] {
      def call():String = {
        val pathPart = document.savedAtPath.get() match {
          case None => "Untitled"
          case Some(file) => file.getName // abs path? meh
        }
        pathPart + (if(document.isDirty.get()) "*" else "") +
          " | SimpleSketch"
      }
    }, document.savedAtPath, document.isDirty)
    stage.titleProperty().bind(title)
    // close button
    stage.setOnCloseRequest(new EventHandler[WindowEvent] {
      def handle(evt: WindowEvent) {
        evt.consume()
        editorController.onCloseRequest()
      }
    })
    stage.show()
  }

}

class Main extends Application {

  def start(primaryStage:Stage) {
    Platform.setImplicitExit(false)
    try {
      Main.createWindow(SketchDocument.createEmpty(), primaryStage)
    } catch {
      case ex: Exception => {
        Logger.getLogger(classOf[Main].getName).log(Level.SEVERE, null, ex)
      }
    }
  }

}
