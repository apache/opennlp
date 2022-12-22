/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.ml.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

/**
 * An abstract, basic implementation of a model reader.
 */
public abstract class AbstractModelReader {

  /**
   * The number of predicates contained in a model.
   */
  protected int NUM_PREDS;
  protected DataReader dataReader;

  /**
   * Initializes a {@link AbstractModelReader} via a {@link File}.
   *
   * @param f The {@link File} that references the model to be read.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public AbstractModelReader(File f) throws IOException {
    String filename = f.getName();
    InputStream input;
    // handle the zipped/not zipped distinction
    if (filename.endsWith(".gz")) {
      input = new GZIPInputStream(new FileInputStream(f));
      filename = filename.substring(0,filename.length() - 3);
    }
    else {
      input = new FileInputStream(f);
    }

    // handle the different formats
    if (filename.endsWith(".bin")) {
      this.dataReader = new BinaryFileDataReader(input);
    }
    else {  // filename ends with ".txt"
      this.dataReader = new PlainTextFileDataReader(input);
    }
  }

  /**
   * Initializes a {@link AbstractModelReader} via a {@link DataReader}.
   *
   * @param dataReader The {@link DataReader} that references the model to be read.
   */
  public AbstractModelReader(DataReader dataReader) {
    super();
    this.dataReader = dataReader;
  }

  /**
   * Implement as needed for the format the model is stored in.
   *
   * @return Reads in an {@code int} value from the underlying {@link DataReader}.
   */
  public int readInt() throws IOException {
    return dataReader.readInt();
  }

  /**
   * Implement as needed for the format the model is stored in.
   *
   * @return Reads in a {@code double} value from the underlying {@link DataReader}.
   */
  public double readDouble() throws IOException {
    return dataReader.readDouble();
  }

  /**
   * Implement as needed for the format the model is stored in.
   *
   * @return Reads in an {@code UTF-encoded String}
   *         value from the underlying {@link DataReader}.
   */
  public String readUTF() throws IOException {
    return dataReader.readUTF();
  }

  /**
   * @return Retrieves the read {@link AbstractModel} instance.
   * @throws IOException Thrown if IO errors occurred constructing the model.
   */
  public AbstractModel getModel() throws IOException {
    checkModelType();
    return constructModel();
  }

  /**
   * Checks the model type via the the underlying {@link DataReader}.
   * 
   * @throws IOException Thrown if IO errors occurred checking the model type.
   */
  public abstract void checkModelType() throws IOException;

  /**
   * Constructs a {@link AbstractModel model}.
   *
   * @return A {@link AbstractModel} reconstructed from a model's (read) attributes.
   * @throws IOException Thrown if IO errors occurred during (re-)construction.
   */
  public abstract AbstractModel constructModel() throws IOException;

  /**
   * @return Reads and retrieves the {@code outcome labels} from the model.
   * @throws IOException Thrown if IO errors occurred.
   */
  protected String[] getOutcomes() throws IOException {
    int numOutcomes = readInt();
    String[] outcomeLabels = new String[numOutcomes];
    for (int i = 0; i < numOutcomes; i++) outcomeLabels[i] = readUTF();
    return outcomeLabels;
  }

  /**
   * @return Reads and retrieves the {@code outcome patterns} from the model.
   * @throws IOException Thrown if IO errors occurred.
   */
  protected int[][] getOutcomePatterns() throws IOException {
    int numOCTypes = readInt();
    int[][] outcomePatterns = new int[numOCTypes][];
    for (int i = 0; i < numOCTypes; i++) {
      StringTokenizer tok = new StringTokenizer(readUTF(), " ");
      int[] infoInts = new int[tok.countTokens()];
      for (int j = 0; tok.hasMoreTokens(); j++) {
        infoInts[j] = Integer.parseInt(tok.nextToken());
      }
      outcomePatterns[i] = infoInts;
    }
    return outcomePatterns;
  }

  /**
   * @return Reads and retrieves the {@code predicates} from the model.
   * @throws IOException Thrown if IO errors occurred.
   */
  protected String[] getPredicates() throws IOException {
    NUM_PREDS = readInt();
    String[] predLabels = new String[NUM_PREDS];
    for (int i = 0; i < NUM_PREDS; i++)
        predLabels[i] = readUTF();
    return predLabels;
  }

  /**
   * Reads the parameters from a file and populates an array of {@link Context} objects.
   * 
   * @param outcomePatterns The outcome patterns for the model. The first index refers to which
   *     outcome pattern (a set of outcomes that occurs with a context) is being specified. The
   *     second index specifies the number of contexts which use this pattern at index {@code 0},
   *     and the index of each outcome which make up this pattern in indices {@code 1-n}.
   * @return An array of {@link Context} objects.
   * @throws IOException Thrown when the model file does not match the outcome patterns or can not be read.
   */
  protected Context[] getParameters(int[][] outcomePatterns) throws IOException {
    Context[] params = new Context[NUM_PREDS];
    int pid = 0;
    for (int[] pattern : outcomePatterns) {
      //construct outcome pattern
      int[] outcomePattern = new int[pattern.length - 1];
      System.arraycopy(pattern, 1, outcomePattern, 0, pattern.length - 1);

      //populate parameters for each context which uses this outcome pattern.
      for (int j = 0; j < pattern[0]; j++) {
        double[] contextParameters = new double[pattern.length - 1];
        for (int k = 1; k < pattern.length; k++) {
          contextParameters[k - 1] = readDouble();
        }
        params[pid] = new Context(outcomePattern, contextParameters);
        pid++;
      }
    }
    return params;
  }

}
