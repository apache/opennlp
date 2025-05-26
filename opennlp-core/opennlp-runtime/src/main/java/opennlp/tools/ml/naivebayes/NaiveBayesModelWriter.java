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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import opennlp.tools.ml.AbstractMLModelWriter;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.AbstractModelWriter;
import opennlp.tools.ml.model.ComparablePredicate;
import opennlp.tools.ml.model.Context;

/**
 * The base class for {@link NaiveBayesModel} writers.
 * <p>
 * It provides the {@link #persist()} method which takes care of the structure
 * of a stored document, and requires an extending class to define precisely
 * how the data should be stored.
 *
 * @see NaiveBayesModel
 * @see AbstractModelWriter
 * @see AbstractMLModelWriter
 */
public abstract class NaiveBayesModelWriter extends AbstractMLModelWriter {

  /**
   * Initializes a {@link NaiveBayesModelWriter} for a
   * {@link AbstractModel NaiveBayes model}.
   *
   * @param model The {@link AbstractModel NaiveBayes model} to be written.
   */
  public NaiveBayesModelWriter(AbstractModel model) {
    super();
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
   * {@inheritDoc}
   */
  @Override
  protected ComparablePredicate[] sortValues() {

    ComparablePredicate[] sortPreds = new ComparablePredicate[PARAMS.length];

    int numParams = 0;
    for (int pid = 0; pid < PARAMS.length; pid++) {
      int[] predkeys = PARAMS[pid].getOutcomes();
      // Arrays.sort(predkeys);
      int numActive = predkeys.length;
      double[] activeParams = PARAMS[pid].getParameters();

      numParams += numActive;
      /*
       * double[] activeParams = new double[numActive];
       *
       * int id = 0; for (int i=0; i < predkeys.length; i++) { int oid =
       * predkeys[i]; activeOutcomes[id] = oid; activeParams[id] =
       * PARAMS[pid].getParams(oid); id++; }
       */
      sortPreds[pid] = new ComparablePredicate(PRED_LABELS[pid],
          predkeys, activeParams);
    }

    Arrays.sort(sortPreds);
    return sortPreds;
  }

  /**
   * Writes the {@link AbstractModel perceptron model}, using the
   * {@link #writeUTF(String)}, {@link #writeDouble(double)}, or {@link #writeInt(int)}}
   * methods implemented by extending classes.
   *
   * <p>If you wish to create a {@link NaiveBayesModelWriter} which uses a different
   * structure, it will be necessary to override the {@link #persist()} method in
   * addition to implementing the {@code writeX(..)} methods.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  @Override
  public void persist() throws IOException {
    // the type of model (NaiveBayes)
    writeUTF("NaiveBayes");
    super.persist();
  }
}
