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

package opennlp.tools.ml.naivebayes;

import java.io.File;
import java.io.IOException;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.AbstractModelReader;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.DataReader;

/**
 * Abstract parent class for readers of NaiveBayes.
 */
public class NaiveBayesModelReader extends AbstractModelReader {

  public NaiveBayesModelReader(File file) throws IOException {
    super(file);
  }

  public NaiveBayesModelReader(DataReader dataReader) {
    super(dataReader);
  }

  /**
   * Retrieve a model from disk. It assumes that models are saved in the
   * following sequence:
   *
   * <br>NaiveBayes (model type identifier)
   * <br>1. # of parameters (int)
   * <br>2. # of outcomes (int)
   * <br>  * list of outcome names (String)
   * <br>3. # of different types of outcome patterns (int)
   * <br>   * list of (int int[])
   * <br>   [# of predicates for which outcome pattern is true] [outcome pattern]
   * <br>4. # of predicates (int)
   * <br>   * list of predicate names (String)
   *
   * <p>If you are creating a reader for a format which won't work with this
   * (perhaps a database or xml file), override this method and ignore the
   * other methods provided in this abstract class.
   *
   * @return The NaiveBayesModel stored in the format and location specified to
   *     this NaiveBayesModelReader (usually via its the constructor).
   */
  public AbstractModel constructModel() throws IOException {
    String[] outcomeLabels = getOutcomes();
    int[][] outcomePatterns = getOutcomePatterns();
    String[] predLabels = getPredicates();
    Context[] params = getParameters(outcomePatterns);

    return new NaiveBayesModel(params,
        predLabels,
        outcomeLabels);
  }

  public void checkModelType() throws java.io.IOException {
    String modelType = readUTF();
    if (!modelType.equals("NaiveBayes"))
      System.out.println("Error: attempting to load a " + modelType +
          " model as a NaiveBayes model." +
          " You should expect problems.");
  }
}
