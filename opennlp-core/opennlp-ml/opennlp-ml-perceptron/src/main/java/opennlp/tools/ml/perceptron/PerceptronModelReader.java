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

package opennlp.tools.ml.perceptron;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.AbstractModelReader;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.DataReader;

/**
 * The base class for readers of {@link PerceptronModel models}.
 * It assumes that models are saved in the following sequence:
 *
 * <br>Perceptron (model type identifier)
 * <br>1. # of parameters ({@code int})
 * <br>2. # of outcomes ({@code int})
 * <br>   * list of outcome names ({@code String})
 * <br>3. # of different types of outcome patterns ({@code int})
 * <br>   * list of ({@code int} {@code int[]})
 * <br>   [# of predicates for which outcome pattern is true] [outcome pattern]
 * <br>4. # of predicates ({@code int})
 * <br>   * list of predicate names ({@code String})
 *
 * @see PerceptronModel
 * @see AbstractModelReader
 */
public class PerceptronModelReader extends AbstractModelReader {

  private static final Logger logger = LoggerFactory.getLogger(PerceptronModelReader.class);

  /**
   * Initializes a {@link PerceptronModelReader} via a {@link File}.
   *
   * @param file The {@link File} that references the model to be read.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public PerceptronModelReader(File file) throws IOException {
    super(file);
  }

  /**
   * Initializes a {@link PerceptronModelReader} via a {@link DataReader}.
   *
   * @param dataReader The {@link DataReader} that references the model to be read.
   */
  public PerceptronModelReader(DataReader dataReader) {
    super(dataReader);
  }

  /**
   * Constructs a {@link AbstractModel model}.
   * <p>
   * If you are creating a reader for a format which won't work with this
   * (perhaps a database or {@code xml} file), override this method and ignore the
   * other methods provided in this abstract class.
   *
   * @return A {@link PerceptronModel} reconstructed from a model's (read) attributes.
   * @throws IOException Thrown if IO errors occurred during (re-)construction.
   */
  @Override
  public AbstractModel constructModel() throws IOException {
    String[] outcomeLabels = getOutcomes();
    int[][] outcomePatterns = getOutcomePatterns();
    String[] predLabels = getPredicates();
    Context[] params = getParameters(outcomePatterns);

    return new PerceptronModel(params, predLabels, outcomeLabels);
  }

  /**
   * Reads the mode type from the underlying reader and informs if it not a
   * {@code Perceptron} model.
   * 
   * @throws IOException Thrown if IO errors occurred.
   */
  @Override
  public void checkModelType() throws IOException {
    String modelType = readUTF();
    if (!modelType.equals("Perceptron"))
      logger.error("Attempting to load a {} " +
              " model as a Perceptron model." +
              " You should expect problems.", modelType);
  }
}
