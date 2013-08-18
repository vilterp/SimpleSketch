package com.github.vilterp

import javafx.beans.value.ObservableDoubleValue
import javafx.beans.property.{SimpleObjectProperty, SimpleStringProperty, DoubleProperty, SimpleDoubleProperty}
import javafx.concurrent.Task
import javafx.scene.control.TreeItem
import javafx.event.EventHandler
import java.io.{FileInputStream, FileOutputStream, File}
import com.github.vilterp.proto.Sketch
import scala.collection.convert.Wrappers.JListWrapper
import com.github.vilterp.proto.Sketch.SketchElement

case class ObsPoint(x:DoubleProperty, y:DoubleProperty) {
  override def toString = s"ObsPoint(${x.getValue}, ${y.getValue})"
}
object ObsPoint {
  def create(x:Double, y:Double) = this(new SimpleDoubleProperty(x), new SimpleDoubleProperty(y))
}

abstract class ShapeModel

case class LineModel(from:ObsPoint, to:ObsPoint) extends ShapeModel {
  override def toString = s"LineModel($from, $to)"
}

case class CircleModel(center:ObsPoint, radius:ObservableDoubleValue) extends ShapeModel
object EmptyShapeModel extends ShapeModel // TODO: hack?

object SketchDocument {

  val DESC = "Sketch Document"
  val EXTENSION = "skt"

  def createEmpty():SketchDocument = new SketchDocument(None, new TreeItem[ShapeModel](EmptyShapeModel))

  def createFromFile(file:File):SketchDocument = {
    val document = Sketch.SketchDocument.parseFrom(new FileInputStream(file))
    def extractModelFromProto(el:SketchElement):TreeItem[ShapeModel] = {
      import Sketch.SketchElement._
      el.getType match {
        case SketchElementType.LINE =>
          val lineEl = el.getLineElement
          new TreeItem(LineModel(ObsPoint.create(lineEl.getFromX, lineEl.getFromY),
                                 ObsPoint.create(lineEl.getToX, lineEl.getToY)))
        case SketchElementType.GROUP =>
          val item = new TreeItem[ShapeModel](EmptyShapeModel)
          val group = el.getGroupElement
          // TODO: map-fu
          for(child <- JListWrapper(group.getChildList)) {
            item.getChildren.add(extractModelFromProto(child))
          }
          item
      }
    }
    new SketchDocument(Some(file), extractModelFromProto(document.getRoot))
  }

}

class SketchDocument(savedAtPath:Option[File], model:TreeItem[ShapeModel])
  extends Document[TreeItem[ShapeModel]](savedAtPath, model) {

  val extension = SketchDocument.EXTENSION

  def writeToDisk(path:File) {
    def buildElementFromTreeItem(treeItem:TreeItem[ShapeModel]):Sketch.SketchElement = {
      treeItem.getValue match {
        case EmptyShapeModel => {
          val group = Sketch.GroupElement.newBuilder()
          for(child <- JListWrapper(treeItem.getChildren)) {
            group.addChild(buildElementFromTreeItem(child))
          }
          Sketch.SketchElement.newBuilder()
            .setType(Sketch.SketchElement.SketchElementType.GROUP)
            .setGroupElement(group)
            .build()
        }
        case LineModel(ObsPoint(fromX, fromY), ObsPoint(toX, toY)) => {
          val line = Sketch.LineElement.newBuilder()
            .setFromX(fromX.get())
            .setFromY(fromY.get())
            .setToX(toX.get())
            .setToY(toY.get())
          Sketch.SketchElement.newBuilder()
            .setType(Sketch.SketchElement.SketchElementType.LINE)
            .setLineElement(line)
            .build()
        }

      }
    }
    val sketchDoc = Sketch.SketchDocument.newBuilder()
      .setRoot(buildElementFromTreeItem(model))
      .build()
    // TODO: file creation / truncation behavior?
    // TODO: add extension...
    sketchDoc.writeTo(new FileOutputStream(path))
  }
}


abstract class SketchAction(document:SketchDocument) extends UndoableAction[TreeItem[ShapeModel]](document)
case class AddShapeAction(document:SketchDocument, path:List[Int], shape:ShapeModel) extends SketchAction(document)
case class RemoveShapeAction(document:SketchDocument, path:List[Int]) extends SketchAction(document)
