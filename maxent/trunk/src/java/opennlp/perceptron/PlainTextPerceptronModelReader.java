package opennlp.perceptron;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import opennlp.model.PlainTextFileDataReader;

public class PlainTextPerceptronModelReader extends PerceptronModelReader {
  
  /**
   * Constructor which directly instantiates the BufferedReader containing
   * the model contents.
   *
   * @param br The BufferedReader containing the model information.
   */
  public PlainTextPerceptronModelReader(BufferedReader br) {
    super(new PlainTextFileDataReader(br));
  }

  /**
   * Constructor which takes a File and creates a reader for it. Detects
   * whether the file is gzipped or not based on whether the suffix contains
   * ".gz".
   *
   * @param f The File in which the model is stored.
   */
  public PlainTextPerceptronModelReader (File f) throws IOException {
    super(f);
  }
}
