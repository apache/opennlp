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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.AbstractModelWriter;
import opennlp.tools.ml.model.ComparablePredicate;
import opennlp.tools.ml.model.Context;

/**
 * Abstract parent class for NaiveBayes writers.  It provides the persist method
 * which takes care of the structure of a stored document, and requires an
 * extending class to define precisely how the data should be stored.
 */
public abstract class NaiveBayesModelWriter extends AbstractModelWriter {
  protected Context[] PARAMS;
  protected String[] OUTCOME_LABELS;
  protected String[] PRED_LABELS;
  int numOutcomes;

  public NaiveBayesModelWriter(AbstractModel model) {

    Object[] data = model.getDataStructures();
    this.numOutcomes = model.getNumOutcomes();
    PARAMS = (Context[]) data[0];

    @SuppressWarnings("unchecked")
    Map<String, Integer> pmap = (Map<String, Integer>) data[1];
    OUTCOME_LABELS = (String[]) data[2];

    PRED_LABELS = new String[pmap.size()];
    for (String pred : pmap.keySet()) {
      PRED_LABELS[pmap.get(pred)] = pred;
    }
  }

  protected ComparablePredicate[] sortValues() {
    ComparablePredicate[] sortPreds;
    ComparablePredicate[] tmpPreds = new ComparablePredicate[PARAMS.length];
    int[] tmpOutcomes = new int[numOutcomes];
    double[] tmpParams = new double[numOutcomes];
    int numPreds = 0;
    //remove parameters with 0 weight and predicates with no parameters
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
        tmpPreds[numPreds] = new ComparablePredicate(PRED_LABELS[pid], activeOutcomes, activeParams);
        numPreds++;
      }
    }
    System.err.println("Compressed " + PARAMS.length + " parameters to " + numPreds);
    sortPreds = new ComparablePredicate[numPreds];
    System.arraycopy(tmpPreds, 0, sortPreds, 0, numPreds);
    Arrays.sort(sortPreds);
    return sortPreds;
  }


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
    System.err.println(outcomePatterns.size() + " outcome patterns");
    return outcomePatterns;
  }

  /**
   * Writes the model to disk, using the <code>writeX()</code> methods
   * provided by extending classes.
   *
   * <p>If you wish to create a NaiveBayesModelWriter which uses a different
   * structure, it will be necessary to override the persist method in
   * addition to implementing the <code>writeX()</code> methods.
   */
  public void persist() throws IOException {

    // the type of model (NaiveBayes)
    writeUTF("NaiveBayes");

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
    for (int i = 0; i < sorted.length; i++)
      for (int j = 0; j < sorted[i].params.length; j++)
        writeDouble(sorted[i].params[j]);

    close();
  }
}
