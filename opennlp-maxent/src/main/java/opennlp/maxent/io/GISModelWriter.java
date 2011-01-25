/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.maxent.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.model.AbstractModel;
import opennlp.model.AbstractModelWriter;
import opennlp.model.ComparablePredicate;
import opennlp.model.Context;
import opennlp.model.IndexHashTable;

/**
 * Abstract parent class for GISModel writers.  It provides the persist method
 * which takes care of the structure of a stored document, and requires an
 * extending class to define precisely how the data should be stored.
 */
public abstract class GISModelWriter extends AbstractModelWriter {
  protected Context[] PARAMS;
  protected String[] OUTCOME_LABELS;
  protected int CORRECTION_CONSTANT;
  protected double CORRECTION_PARAM;
  protected String[] PRED_LABELS;

  public GISModelWriter(AbstractModel model) {

    Object[] data = model.getDataStructures();

    PARAMS = (Context[]) data[0];
    IndexHashTable<String> pmap = (IndexHashTable<String>) data[1];
    OUTCOME_LABELS = (String[]) data[2];
    CORRECTION_CONSTANT = ((Integer) data[3]).intValue();
    CORRECTION_PARAM = ((Double) data[4]).doubleValue();

    PRED_LABELS = new String[pmap.size()];
    pmap.toArray(PRED_LABELS);
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

    // the value of the correction constant
    writeInt(CORRECTION_CONSTANT);

    // the value of the correction constant
    writeDouble(CORRECTION_PARAM);

    // the mapping from outcomes to their integer indexes
    writeInt(OUTCOME_LABELS.length);

    for (int i = 0; i < OUTCOME_LABELS.length; i++)
      writeUTF(OUTCOME_LABELS[i]);

    // the mapping from predicates to the outcomes they contributed to.
    // The sorting is done so that we actually can write this out more
    // compactly than as the entire list.
    ComparablePredicate[] sorted = sortValues();
    List compressed = compressOutcomes(sorted);

    writeInt(compressed.size());

    for (int i = 0; i < compressed.size(); i++) {
      List a = (List) compressed.get(i);
      writeUTF(a.size() + ((ComparablePredicate) a.get(0)).toString());
    }

    // the mapping from predicate names to their integer indexes
    writeInt(PARAMS.length);

    for (int i = 0; i < sorted.length; i++)
      writeUTF(sorted[i].name);

    // write out the parameters
    for (int i = 0; i < sorted.length; i++)
      for (int j = 0; j < sorted[i].params.length; j++)
        writeDouble(sorted[i].params[j]);

    close();
  }

  protected ComparablePredicate[] sortValues() {

    ComparablePredicate[] sortPreds = new ComparablePredicate[PARAMS.length];

    int numParams = 0;
    for (int pid = 0; pid < PARAMS.length; pid++) {
      int[] predkeys = PARAMS[pid].getOutcomes();
      // Arrays.sort(predkeys);
      int numActive = predkeys.length;
      int[] activeOutcomes = predkeys;
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
          activeOutcomes, activeParams);
    }

    Arrays.sort(sortPreds);
    return sortPreds;
  }

  protected List compressOutcomes(ComparablePredicate[] sorted) {
    ComparablePredicate cp = sorted[0];
    List outcomePatterns = new ArrayList();
    List newGroup = new ArrayList();
    for (int i = 0; i < sorted.length; i++) {
      if (cp.compareTo(sorted[i]) == 0) {
        newGroup.add(sorted[i]);
      } else {
        cp = sorted[i];
        outcomePatterns.add(newGroup);
        newGroup = new ArrayList();
        newGroup.add(sorted[i]);
      }
    }
    outcomePatterns.add(newGroup);
    return outcomePatterns;
  }
}
