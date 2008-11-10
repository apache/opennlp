package opennlp.model;


public abstract class AbstractModelWriter {

  public AbstractModelWriter() {
    super();
  }

  public abstract void writeUTF(String s) throws java.io.IOException;

  public abstract void writeInt(int i) throws java.io.IOException;

  public abstract void writeDouble(double d) throws java.io.IOException;

  public abstract void close() throws java.io.IOException;

  public abstract void persist() throws java.io.IOException;

}
