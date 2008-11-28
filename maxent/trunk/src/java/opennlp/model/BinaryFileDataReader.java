package opennlp.model;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class BinaryFileDataReader implements DataReader {

  private DataInputStream input;
  
  public BinaryFileDataReader(File f) throws IOException {
    if (f.getName().endsWith(".gz")) {
      input = new DataInputStream(new BufferedInputStream(
          new GZIPInputStream(new BufferedInputStream(new FileInputStream(f)))));
    }
    else {
      input = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
    }
  }
  
  public BinaryFileDataReader(InputStream in) {
    input = new DataInputStream(in);
  }
  
  public BinaryFileDataReader(DataInputStream in) {
    input = in;
  }
  
  public double readDouble() throws IOException {
    return input.readDouble();
  }

  public int readInt() throws IOException {
    return input.readInt();
  }

  public String readUTF() throws IOException {
    return input.readUTF();
  }

}
