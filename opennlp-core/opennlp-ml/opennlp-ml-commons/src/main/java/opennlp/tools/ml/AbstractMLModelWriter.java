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

package opennlp.tools.ml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.ml.model.AbstractModelWriter;
import opennlp.tools.ml.model.ComparablePredicate;
import opennlp.tools.ml.model.Context;

public abstract class AbstractMLModelWriter extends AbstractModelWriter {

  private static final Logger logger = LoggerFactory.getLogger(AbstractMLModelWriter.class);

  protected Context[] PARAMS;
  protected String[] OUTCOME_LABELS;
  protected String[] PRED_LABELS;
  
  protected int numOutcomes;

  /**
   * Sorts and optimizes the model parameters. Thereby, parameters with
   * {@code 0} weight and predicates with no parameters are removed.
   *
   * @return A {@link ComparablePredicate[]}.
   */
  protected abstract ComparablePredicate[] sortValues();

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
   * Compresses outcome patterns.
   *
   * @return A {@link List} of {@link List<ComparablePredicate>} that represent
   *         the remaining outcomes patterns.
   */
  protected List<List<ComparablePredicate>> compressOutcomes(ComparablePredicate[] sorted) {
    List<List<ComparablePredicate>> outcomePatterns = new ArrayList<>();
    if (sorted.length > 0) {
      ComparablePredicate cp = sorted[0];
      List<ComparablePredicate> newGroup = new ArrayList<>();
      for (ComparablePredicate comparablePredicate : sorted) {
        if (cp.compareTo(comparablePredicate) == 0) {
          newGroup.add(comparablePredicate);
        } else {
          cp = comparablePredicate;
          outcomePatterns.add(newGroup);
          newGroup = new ArrayList<>();
          newGroup.add(comparablePredicate);
        }
      }
      outcomePatterns.add(newGroup);
    }
    return outcomePatterns;
  }

  @Override
  public void persist() throws IOException {
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
