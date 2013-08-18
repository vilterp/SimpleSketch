package com.github.vilterp

import javafx.beans.binding.{BooleanBinding, Bindings}
import javafx.event.EventHandler
import javafx.scene.input.{KeyEvent, MouseEvent}
import javafx.scene.paint.Color
import javafx.scene.{Group, Cursor}
import java.util.logging.Logger
import javafx.scene.control.TreeCell
import javafx.beans.property.SimpleBooleanProperty
import java.util.concurrent.Callable
import javafx.beans.value.{ObservableValue, ChangeListener}
import java.lang
import javafx.collections.ObservableSet

class HandleView(bindTo:ObsPoint) extends javafx.scene.shape.Circle {

  val logger = Logger.getLogger(classOf[HandleView].getName)

  setFill(Color.DODGERBLUE)
  setRadius(5)
  setCursor(Cursor.OPEN_HAND)
  layoutXProperty().bindBidirectional(bindTo.x)
  layoutYProperty().bindBidirectional(bindTo.y)

  val node = this
  node.setOnMousePressed(new EventHandler[MouseEvent] {
      def handle(evt: MouseEvent) {
        logger.info("pressed")
        evt.consume()
        val deltaX = node.getLayoutX - evt.getSceneX
        val deltaY = node.getLayoutY - evt.getSceneY
        // really want a mouseX/Y signal here so I can just
        // create a binding
        node.setOnMouseDragged(new EventHandler[MouseEvent] {
          def handle(evt1: MouseEvent) {
            evt.consume()
            node.setLayoutX(evt1.getSceneX + deltaX)
            node.setLayoutY(evt1.getSceneY + deltaY)
          }
        })
        node.setOnMouseReleased(new EventHandler[MouseEvent] {
          def handle(evt1: MouseEvent) {
            logger.info("released")
            evt.consume()
            node.setOnMouseDragged(null)
            node.setOnMouseReleased(null)
          }
        })
      }
    })

}

object MoarBindings {

  def setContains[A](set:ObservableSet[A], obj:A):BooleanBinding =
    Bindings.createBooleanBinding(new Callable[lang.Boolean] {
      def call():lang.Boolean = set.contains(obj)
    }, set)

}

abstract class ShapeView(editorController:EditorController, model:ShapeModel) extends Group {

  def maybeAddToSelected(evt:MouseEvent) {
    if(!evt.isShiftDown) {
      editorController.selectedShapes.clear()
    }
    editorController.selectedShapes.add(model)
  }

}

class LineView(editorController:EditorController, lineModel:LineModel) extends ShapeView(editorController, lineModel) {

  val isSelected = MoarBindings.setContains(editorController.selectedShapes, lineModel)
  val isHovered = Bindings.equal(editorController.hoveredShape, lineModel)

  import javafx.scene._
  
  // TODO: line properties
  val line = new shape.Line()
  line.startXProperty().bind(lineModel.from.x)
  line.startYProperty().bind(lineModel.from.y)
  line.endXProperty().bind(lineModel.to.x)
  line.endYProperty().bind(lineModel.to.y)
  getChildren.add(line)
  line.setOnMouseClicked(new EventHandler[MouseEvent] {
    def handle(evt: MouseEvent) {
      evt.consume()
      maybeAddToSelected(evt)
    }
  })

  // backing, bigger line for hovering and clicking
  val backingLine = new shape.Line()
  backingLine.startXProperty().bindBidirectional(lineModel.from.x)
  backingLine.startYProperty().bindBidirectional(lineModel.from.y)
  backingLine.endXProperty().bindBidirectional(lineModel.to.x)
  backingLine.endYProperty().bindBidirectional(lineModel.to.y)
  backingLine.setStrokeWidth(5)
  backingLine.setCursor(Cursor.OPEN_HAND)
  getChildren.add(backingLine)
  backingLine.setOnMousePressed(new EventHandler[MouseEvent] {
    def handle(p1: MouseEvent) {
      p1.consume()
      // TODO: make draggable
    }
  })
  backingLine.setOnMouseClicked(new EventHandler[MouseEvent] {
    def handle(evt: MouseEvent) {
      evt.consume()
      maybeAddToSelected(evt)
    }
  })
  backingLine.setOnMouseEntered(new EventHandler[MouseEvent] {
    def handle(evt: MouseEvent) {
      evt.consume()
      editorController.hoveredShape.set(lineModel)
    }
  })
  backingLine.setOnMouseExited(new EventHandler[MouseEvent] {
    def handle(evt: MouseEvent) {
      evt.consume()
      editorController.hoveredShape.set(null)
    }
  })
  backingLine.strokeProperty().bind(Bindings.createObjectBinding[Color](
    new Callable[Color] {
      def call():Color =
        if(editorController.hoveredShape.get() == lineModel)
          Color.RED
        else
          Color.TRANSPARENT
    }, editorController.hoveredShape))

  val handle1 = new HandleView(lineModel.to)
  val handle2 = new HandleView(lineModel.from)
  getChildren.add(handle1)
  getChildren.add(handle2)
  handle1.visibleProperty().bind(isSelected)
  handle2.visibleProperty().bind(isSelected)

}

class ElementTreeCell(editorController:EditorController) extends TreeCell[ShapeModel] {

  var item:ShapeModel = null
  val elementIsHovered = Bindings.equal(editorController.hoveredShape, item)

  setOnMouseEntered(new EventHandler[MouseEvent] {
    def handle(p1: MouseEvent) {
      editorController.hoveredShape.set(item)
    }
  })
  setOnMouseExited(new EventHandler[MouseEvent] {
    def handle(p1: MouseEvent) {
      editorController.hoveredShape.set(null)
    }
  })
  setOnMouseClicked(new EventHandler[MouseEvent] {
    def handle(evt: MouseEvent) {
      evt.consume()
      if(!evt.isShiftDown) {
        editorController.selectedShapes.clear()
      }
      editorController.selectedShapes.add(item)
    }
  })
  setOnKeyTyped(new EventHandler[KeyEvent] {
    def handle(p1: KeyEvent) {
      println(p1)
    }
  })

  // TODO: delete when you press delete
  // drag & drop...

  override def updateItem(item:ShapeModel, empty:Boolean) {
    if(empty) {
      setText(null)
      setGraphic(null)
    } else {
      this.item = item
      val text = item match {
        case _:LineModel => "Line"
        case _:CircleModel => "Circle"
      }
      setText(text)
      setGraphic(null)
    }
  }

}
