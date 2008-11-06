package opennlp.model;

import java.io.IOException;
import java.io.ObjectInputStream;

public class ObjectDataReader implements DataReader {

  protected ObjectInputStream ois;
  
  public ObjectDataReader(ObjectInputStream ois) {
    this.ois = ois;
  }
  
  public double readDouble() throws IOException {
    return ois.readDouble();
  }

  public int readInt() throws IOException {
    return ois.readInt();
  }

  public String readUTF() throws IOException {
    return ois.readUTF();
  }

}
