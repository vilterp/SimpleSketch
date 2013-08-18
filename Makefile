SRCDIR=src/main/scala/com/github/vilterp
PROTODIR=src/main/protobuf
JAVADIR=src/main/java

$(SRCDIR)/proto/Sketch.java: $(PROTODIR)/sketch.proto
	protoc --java_out $(JAVADIR) $(PROTODIR)/sketch.proto
