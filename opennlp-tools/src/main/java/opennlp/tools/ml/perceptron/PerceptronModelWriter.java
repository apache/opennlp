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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public abstract class PerceptronModelWriter extends AbstractModelWriter {

  private static final Logger logger = LoggerFactory.getLogger(PerceptronModelWriter.class);
  protected Context[] PARAMS;
  protected String[] OUTCOME_LABELS;
  protected String[] PRED_LABELS;
  private final int numOutcomes;

  /**
   * Initializes a {@link PerceptronModelWriter} for a
   * {@link AbstractModel perceptron model}.
   *
   * @param model The {@link AbstractModel perceptron model} to be written.
   */
  public PerceptronModelWriter(AbstractModel model) {

    Object[] data = model.getDataStructures();
    this.numOutcomes = model.getNumOutcomes();
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
   * Sorts and optimizes the model parameters. Thereby, parameters with
   * {@code 0} weight and predicates with no parameters are removed.
   *
   * @return A {@link ComparablePredicate[]}.
   */
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
   * Computes outcome patterns via {@link ComparablePredicate[] predicates}.
   *
   * @return A {@link List} of {@link List<ComparablePredicate>} that represent
   *         the outcomes patterns.
   */
  protected List<List<ComparablePredicate>> computeOutcomePatterns(ComparablePredicate[] sorted) {
    ComparablePredicate cp = sorted[0];
    List<List<ComparablePredicate>> outcomePatterns = new ArrayList<>();
    List<ComparablePredicate> newGroup = new ArrayList<>();
    for (ComparablePredicate predicate : sorted) {
      if (cp.compareTo(predicate) == 0) {
        newGroup.add(predicate);
      } else {
        cp = predicate;
        outcomePatterns.add(newGroup);
        newGroup = new ArrayList<>();
        newGroup.add(predicate);
      }
    }
    outcomePatterns.add(newGroup);
    logger.info("{} outcome patterns", outcomePatterns.size());
    return outcomePatterns;
  }

  /**
   * Writes the {@link AbstractModel perceptron model}, using the
   * {@link #writeUTF(String)}, {@link #writeDouble(double)}, or {@link #writeInt(int)}}
   * methods implemented by extending classes.
   *
   * <p>If you wish to create a {@link PerceptronModelWriter} which uses a different
   * structure, it will be necessary to override the {@code #persist()} method in
   * addition to implementing the {@code writeX(..)} methods.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  @Override
  public void persist() throws IOException {

    // the type of model (Perceptron)
    writeUTF("Perceptron");

    // the mapping from outcomes to their integer indexes
    writeInt(OUTCOME_LABELS.length);

    for (String label : OUTCOME_LABELS) {
      writeUTF(label);
    }

    // the mapping from predicates to the outcomes they contributed to.
    // The sorting is done so that we actually can write this out more
    // compactly than as the entire list.
    ComparablePredicate[] sorted = sortValues();
    List<List<ComparablePredicate>> compressed = computeOutcomePatterns(sorted);

    writeInt(compressed.size());

    for (List<ComparablePredicate> a : compressed) {
      writeUTF(a.size() + a.get(0).toString());
    }

    // the mapping from predicate names to their integer indexes
    writeInt(sorted.length);

    for (ComparablePredicate s : sorted) {
      writeUTF(s.name);
    }

    // write out the parameters
    for (ComparablePredicate comparablePredicate : sorted)
      for (int j = 0; j < comparablePredicate.params.length; j++)
        writeDouble(comparablePredicate.params[j]);

    close();
  }
}
