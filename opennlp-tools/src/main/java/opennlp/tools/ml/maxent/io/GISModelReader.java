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

package opennlp.tools.ml.maxent.io;

import java.io.File;
import java.io.IOException;

import opennlp.tools.ml.maxent.GISModel;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.AbstractModelReader;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.DataReader;

/**
 * Abstract parent class for readers of GISModels.
 */
public class GISModelReader extends AbstractModelReader {

  public GISModelReader(File file) throws IOException {
    super(file);
  }

  public GISModelReader(DataReader dataReader) {
    super(dataReader);
  }

  /**
   * Retrieve a model from disk. It assumes that models are saved in the
   * following sequence:
   *
   * <br>
   * GIS (model type identifier) <br>
   * 1. # of parameters (int) <br>
   * 2. the correction constant (int) <br>
   * 3. the correction constant parameter (double) <br>
   * 4. # of outcomes (int) <br>
   * * list of outcome names (String) <br>
   * 5. # of different types of outcome patterns (int) <br>
   * * list of (int int[]) <br>
   * [# of predicates for which outcome pattern is true] [outcome pattern] <br>
   * 6. # of predicates (int) <br>
   * * list of predicate names (String)
   *
   * <p>
   * If you are creating a reader for a format which won't work with this
   * (perhaps a database or xml file), override this method and ignore the other
   * methods provided in this abstract class.
   *
   * @return The GISModel stored in the format and location specified to this
   *         GISModelReader (usually via its the constructor).
   */
  public AbstractModel constructModel() throws IOException {

    // read correction constant (not used anymore)
    readInt();

    // read correction params (not used anymore)
    readDouble();

    String[] outcomeLabels = getOutcomes();
    int[][] outcomePatterns = getOutcomePatterns();
    String[] predLabels = getPredicates();
    Context[] params = getParameters(outcomePatterns);
    return new GISModel(params, predLabels, outcomeLabels);
  }

  public void checkModelType() throws java.io.IOException {
    String modelType = readUTF();
    if (!modelType.equals("GIS"))
      System.out.println("Error: attempting to load a " + modelType
          + " model as a GIS model." + " You should expect problems.");
  }
}
