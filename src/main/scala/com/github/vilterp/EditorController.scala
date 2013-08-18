package com.github.vilterp

import javafx.fxml.{FXMLLoader, FXML, Initializable}
import java.net.URL
import java.util.ResourceBundle
import javafx.scene.control._
import javafx.beans.property.{SimpleObjectProperty, ObjectProperty}
import java.util.logging.Logger
import javafx.scene.layout.{AnchorPane, Pane}
import javafx.event.EventHandler
import javafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import javafx.scene.Cursor
import javafx.beans.binding.Bindings
import java.util.concurrent.Callable
import javafx.collections.{FXCollections, ListChangeListener}
import javafx.collections.ListChangeListener.Change
import javafx.util.Callback
import javafx.stage.{Stage, Window}
import javafx.beans.value.{ObservableValue, ChangeListener}

object Tool extends Enumeration {
  type Tool = Value
  val MOUSE, LINE, CIRCLE, RECT = Value
}
import Tool._

object EditorController {

  val tools:Map[String,Tool] = Map("mouseButton" -> MOUSE,
                                   "lineButton" -> LINE,
                                   "circleButton" -> CIRCLE,
                                   "rectButton" -> RECT)
  val toolsReversed:Map[Tool,String] = tools.map(_.swap)

}

class EditorController(val document:SketchDocument, val window:Stage) extends AnchorPane {

  val logger = Logger.getLogger(classOf[EditorController].getName)

  @FXML
  var mouseButton:ToggleButton = null

  @FXML
  var toolbar:ToolBar = null

  @FXML
  var toolbarGroup:ToggleGroup = null

  @FXML
  var drawingPane:Pane = null

  @FXML
  var elementTree:TreeView[ShapeModel] = null

  // initialize view model (?)

  val currentTool:ObjectProperty[Tool] = new SimpleObjectProperty[Tool](Tool.MOUSE)
  var kbChangedTool = false

  val selectedShapes = FXCollections.observableSet[ShapeModel]()
  val hoveredShape = new SimpleObjectProperty[ShapeModel](null)

  // TODO: view model distinct from model
  private val model = document.model
  model.setExpanded(true)

  // load view hierarchy
  val loader = new FXMLLoader(getClass.getResource("/editor.fxml"))
  loader.setRoot(this)
  loader.setController(this)
  loader.load()

  def initialize() {
    // the loader should do this for you
    assert(toolbar != null)
    assert(toolbarGroup != null)
    // bind currentTool to selected button
    // currentTool.bind(
    //   Bindings.createObjectBinding[Tool](new Callable[Tool] {
    //     def call():Tool = tools(toolbarGroup.getSelectedToggle
    //                             .asInstanceOf[ToggleButton].getId)
    //   }, toolbarGroup.selectedToggleProperty()))
    currentTool.addListener(new ChangeListener[Tool] {
      def changed(prop:ObservableValue[_ <:Tool], oldval:Tool, newval:Tool) {
        if(kbChangedTool) {
          val id = EditorController.toolsReversed(newval)
          val button = toolbar.lookup(s"#$id")
          toolbarGroup.selectToggle(button.asInstanceOf[Toggle])
        }
      }
    })
    // wow ^^ this is incredibly verbose.
    toolbarGroup.selectedToggleProperty().addListener(new ChangeListener[Toggle] {
      def changed(cur: ObservableValue[_ <: Toggle], oldval: Toggle, newval: Toggle) {
        kbChangedTool = false
        currentTool.set(EditorController.tools(newval.asInstanceOf[ToggleButton].getId))
      }
    })
    toolbarGroup.selectToggle(mouseButton)
    setupTree()
    setupMouse()
    bindModelToCanvas()
    setupKeybindings()
    setupUndo()
  }

  private def setupMouse() {
    // bind cursor
    drawingPane.cursorProperty().bind(
      Bindings.createObjectBinding[Cursor](new Callable[Cursor] {
        def call():Cursor = currentTool.getValue match {
          case MOUSE => Cursor.DEFAULT
          case _ => Cursor.CROSSHAIR
        }
      }, currentTool))
    // handle click & drag
    drawingPane.setOnMousePressed(new EventHandler[MouseEvent] {
      def handle(evt: MouseEvent) {
        drawingPane.requestFocus()
        currentTool.get match {
          case LINE =>
            val newLine = new LineModel(ObsPoint.create(evt.getX, evt.getY),
                               ObsPoint.create(evt.getX, evt.getY))
            model.getChildren.add(new TreeItem[ShapeModel](newLine))
            selectedShapes.clear()
            selectedShapes.add(newLine)
            drawingPane.setOnMouseDragged(new EventHandler[MouseEvent] {
              def handle(evt1: MouseEvent) {
                val toPoint = newLine.to
                toPoint.x.set(evt1.getX)
                toPoint.y.set(evt1.getY)
              }
            })
            drawingPane.setOnMouseReleased(new EventHandler[MouseEvent] {
              def handle(p1: MouseEvent) {
                drawingPane.setOnMouseDragged(null)
                drawingPane.setOnMouseReleased(null)
                // emit undoableaction
                val insertedInd = model.getChildren.size() - 1
                val path = List(insertedInd)
                val action = new AddShapeAction(document, path, newLine)
                val undoAction = new RemoveShapeAction(document, path)
                document.accept(action, undoAction)
              }
            })
          case MOUSE =>
            selectedShapes.clear()
          case t =>
            logger.severe(s"tool $t not implemented yet")
        }
      }
    })
  }

  import scala.collection.mutable
  private val viewForModel = mutable.Map[ShapeModel,ShapeView]()

  private def bindModelToCanvas() {
    bindTreeItemToCanvas(model)
  }

  private def bindTreeItemToCanvas(node:TreeItem[ShapeModel]) {
    // TODO: make recursive (bind child nodes with this function as well)
    node.getChildren.addListener(new ListChangeListener[TreeItem[ShapeModel]] {
      def onChanged(evt: Change[_ <: TreeItem[ShapeModel]]) {
        import scala.collection.convert.Wrappers._
        while(evt.next()) {
          if(evt.wasAdded()) {
            for(addedTreeItem <- JListWrapper(evt.getAddedSubList)) {
              bindTreeItemToCanvas(addedTreeItem)
              addedTreeItem.getValue match {
                case lineModel:LineModel =>
                  val linePane = new LineView(EditorController.this, lineModel)
                  drawingPane.getChildren.add(linePane)
                  viewForModel.+=((addedTreeItem.getValue, linePane))
              }
            }
          } else if(evt.wasRemoved()) {
            for(removedTreeItem <- JListWrapper(evt.getRemoved)) {
              drawingPane.getChildren.remove(viewForModel(removedTreeItem.getValue))
            }
          }
        }
      }
    })
  }

  private def setupTree() {
    elementTree.setRoot(model)
    elementTree.setCellFactory(new Callback[TreeView[ShapeModel], TreeCell[ShapeModel]] {
      def call(view: TreeView[ShapeModel]): TreeCell[ShapeModel] =
        new ElementTreeCell(EditorController.this)
    })
  }

  val keyBindings = Map(
    KeyCode.M -> MOUSE,
    KeyCode.L -> LINE,
    KeyCode.R -> RECT,
    KeyCode.C -> CIRCLE
  )

  private def setupKeybindings() {
    drawingPane.addEventFilter[KeyEvent](KeyEvent.KEY_PRESSED, new EventHandler[KeyEvent] {
      def handle(evt: KeyEvent) {
        keyBindings.get(evt.getCode) match {
          case Some(tool) =>
            kbChangedTool = true
            currentTool.set(tool)
            evt.consume()
          case None =>
        }
      }
    })
  }

  private def setupUndo() {
    document.onUndoProperty.set(new EventHandler[UndoEvent[TreeItem[ShapeModel]]] {
      def handle(evt: UndoEvent[TreeItem[ShapeModel]]) {
        evt.undoAction match {
          case RemoveShapeAction(_, path) =>
            document.model.getChildren.remove(path(0))
          case AddShapeAction(_, path, shape) =>
            document.model.getChildren.add(path(0), new TreeItem[ShapeModel](shape))
        }
      }
    })
  }

}
