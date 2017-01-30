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
 * Abstract parent class for GISModel writers.  It provides the persist method
 * which takes care of the structure of a stored document, and requires an
 * extending class to define precisely how the data should be stored.
 */
public abstract class GISModelWriter extends AbstractModelWriter {
  protected Context[] PARAMS;
  protected String[] OUTCOME_LABELS;
  protected String[] PRED_LABELS;

  public GISModelWriter(AbstractModel model) {

    Object[] data = model.getDataStructures();

    PARAMS = (Context[]) data[0];

    @SuppressWarnings("unchecked")
    Map<String, Integer> pmap = (Map<String, Integer>) data[1];
    OUTCOME_LABELS = (String[]) data[2];
    PRED_LABELS = new String[pmap.size()];
    for (String pred : pmap.keySet()) {
      PRED_LABELS[pmap.get(pred)] = pred;
    }
  }


  /**
   * Writes the model to disk, using the <code>writeX()</code> methods provided
   * by extending classes.
   *
   * <p>
   * If you wish to create a GISModelWriter which uses a different structure, it
   * will be necessary to override the persist method in addition to
   * implementing the <code>writeX()</code> methods.
   */
  public void persist() throws IOException {

    // the type of model (GIS)
    writeUTF("GIS");

    // the value of the correction constant (not used anymore)
    writeInt(1);

    // the value of the correction params (not used anymore)
    writeDouble(1);

    // the mapping from outcomes to their integer indexes
    writeInt(OUTCOME_LABELS.length);

    for (String OUTCOME_LABEL : OUTCOME_LABELS) {
      writeUTF(OUTCOME_LABEL);
    }

    // the mapping from predicates to the outcomes they contributed to.
    // The sorting is done so that we actually can write this out more
    // compactly than as the entire list.
    ComparablePredicate[] sorted = sortValues();
    List<List<ComparablePredicate>> compressed = compressOutcomes(sorted);

    writeInt(compressed.size());

    for (List<ComparablePredicate> aCompressed : compressed) {
      writeUTF(aCompressed.size() + ((List) aCompressed).get(0).toString());
    }

    // the mapping from predicate names to their integer indexes
    writeInt(PARAMS.length);

    for (ComparablePredicate aSorted : sorted) {
      writeUTF(aSorted.name);
    }

    // write out the parameters
    for (ComparablePredicate aSorted : sorted) {
      for (int j = 0; j < aSorted.params.length; j++) {
        writeDouble(aSorted.params[j]);
      }
    }

    close();
  }

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

  protected List<List<ComparablePredicate>> compressOutcomes(ComparablePredicate[] sorted) {
    List<List<ComparablePredicate>> outcomePatterns = new ArrayList<>();
    if (sorted.length > 0) {
      ComparablePredicate cp = sorted[0];
      List<ComparablePredicate> newGroup = new ArrayList<>();
      for (int i = 0; i < sorted.length; i++) {
        if (cp.compareTo(sorted[i]) == 0) {
          newGroup.add(sorted[i]);
        } else {
          cp = sorted[i];
          outcomePatterns.add(newGroup);
          newGroup = new ArrayList<>();
          newGroup.add(sorted[i]);
        }
      }
      outcomePatterns.add(newGroup);
    }
    return outcomePatterns;
  }
}
