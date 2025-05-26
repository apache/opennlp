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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.ml.AbstractMLModelWriter;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.AbstractModelWriter;
import opennlp.tools.ml.model.ComparablePredicate;
import opennlp.tools.ml.model.Context;

/**
 * The base class for {@link PerceptronModel} writers.
 * <p>
 * It provides the {@link #persist()} method which takes care of the structure
 * of a stored document, and requires an extending class to define precisely
 * how the data should be stored.
 *
 * @see PerceptronModel
 * @see AbstractModelWriter
 * @see AbstractMLModelWriter
 */
public abstract class PerceptronModelWriter extends AbstractMLModelWriter {

  private static final Logger logger = LoggerFactory.getLogger(PerceptronModelWriter.class);

  /**
   * Initializes a {@link PerceptronModelWriter} for a
   * {@link AbstractModel perceptron model}.
   *
   * @param model The {@link AbstractModel perceptron model} to be written.
   */
  public PerceptronModelWriter(AbstractModel model) {
    super();
    Object[] data = model.getDataStructures();
    numOutcomes = model.getNumOutcomes();
    PARAMS = (Context[]) data[0];

    @SuppressWarnings("unchecked")
    Map<String, Context> pmap = (Map<String, Context>) data[1];

    OUTCOME_LABELS = (String[]) data[2];
    PARAMS = new Context[pmap.size()];
    PRED_LABELS = new String[pmap.size()];

    int i = 0;
    for (Map.Entry<String, Context> pred : pmap.entrySet()) {
      PRED_LABELS[i] = pred.getKey();
      PARAMS[i] = pred.getValue();
      i++;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected ComparablePredicate[] sortValues() {
    ComparablePredicate[] sortPreds;
    ComparablePredicate[] tmpPreds = new ComparablePredicate[PARAMS.length];
    int[] tmpOutcomes = new int[numOutcomes];
    double[] tmpParams = new double[numOutcomes];
    int numPreds = 0;
    // remove parameters with 0 weight and predicates with no parameters
    for (int pid = 0; pid < PARAMS.length; pid++) {
      int numParams = 0;
      double[] predParams = PARAMS[pid].getParameters();
      int[] outcomePattern = PARAMS[pid].getOutcomes();
      for (int pi = 0; pi < predParams.length; pi++) {
        if (predParams[pi] != 0d) {
          tmpOutcomes[numParams] = outcomePattern[pi];
          tmpParams[numParams] = predParams[pi];
          numParams++;
        }
      }

      int[] activeOutcomes = new int[numParams];
      double[] activeParams = new double[numParams];

      for (int pi = 0; pi < numParams; pi++) {
        activeOutcomes[pi] = tmpOutcomes[pi];
        activeParams[pi] = tmpParams[pi];
      }
      if (numParams != 0) {
        tmpPreds[numPreds] = new ComparablePredicate(PRED_LABELS[pid],activeOutcomes,activeParams);
        numPreds++;
      }
    }
    logger.info("Compressed {} parameters to {}", PARAMS.length , numPreds);
    sortPreds = new ComparablePredicate[numPreds];
    System.arraycopy(tmpPreds, 0, sortPreds, 0, numPreds);
    Arrays.sort(sortPreds);
    return sortPreds;
  }

  /**
   * Writes the {@link AbstractModel perceptron model}, using the
   * {@link #writeUTF(String)}, {@link #writeDouble(double)}, or {@link #writeInt(int)}}
   * methods implemented by extending classes.
   *
   * <p>If you wish to create a {@link PerceptronModelWriter} which uses a different
   * structure, it will be necessary to override the {@link #persist()} method in
   * addition to implementing the {@code writeX(..)} methods.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  @Override
  public void persist() throws IOException {
    // the type of model (Perceptron)
    writeUTF("Perceptron");
    super.persist();
  }
}
