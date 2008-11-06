package opennlp.perceptron;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import opennlp.model.BinaryFileDataReader;

public class BinaryPerceptronModelReader extends PerceptronModelReader {
  

  /**
   * Constructor which directly instantiates the DataInputStream containing
   * the model contents.
   *
   * @param dis The DataInputStream containing the model information.
   */
  public BinaryPerceptronModelReader(DataInputStream dis) {
    super(new BinaryFileDataReader(dis));
  }

  /**
   * Constructor which takes a File and creates a reader for it. Detects
   * whether the file is gzipped or not based on whether the suffix contains
   * ".gz" 
   *
   * @param f The File in which the model is stored.
   */
  public BinaryPerceptronModelReader (File f) throws IOException {
    super(f);
  }  
}
