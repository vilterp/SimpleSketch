import sbtprotobuf.{ProtobufPlugin=>PB}

seq(PB.protobufSettings: _*)

name := "SimpleSketch"

scalaVersion := "2.10.2"

fork in run := true
