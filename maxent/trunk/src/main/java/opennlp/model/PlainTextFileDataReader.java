package opennlp.model;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

public class PlainTextFileDataReader implements DataReader {

  private BufferedReader input;
  
  public PlainTextFileDataReader(File f) throws IOException {
    if (f.getName().endsWith(".gz")) {
      input = new BufferedReader(new InputStreamReader(new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(f))))));
    }
    else {
      input = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(f))));
    }
  }
  
  public PlainTextFileDataReader(InputStream in) {
    input = new BufferedReader(new InputStreamReader(in));
  }
  
  public PlainTextFileDataReader(BufferedReader in) {
    input = in;
  }
  
  public double readDouble() throws IOException {
    return Double.parseDouble(input.readLine());
  }

  public int readInt() throws IOException {
    return Integer.parseInt(input.readLine());
  }

  public String readUTF() throws IOException {
    return input.readLine();
  }

}
