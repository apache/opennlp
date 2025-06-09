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
import java.util.List;

import opennlp.tools.ml.maxent.quasinewton.QNModel;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.ComparablePredicate;

/**
 * The base class for writers of {@link QNModel models}.
 * <p>
 * It provides the {@link #persist()} method which takes care of the structure of a
 * stored document, and requires an extending class to define precisely how
 * the data should be stored.
 */
public abstract class QNModelWriter extends GISModelWriter {

  public QNModelWriter(AbstractModel model) {
    super(model);
  }

  /**
   * Writes the {@link AbstractModel QN model}, using the
   * {@link #writeUTF(String)}, {@link #writeDouble(double)}, or {@link #writeInt(int)}}
   * methods implemented by extending classes.
   *
   * <p>If you wish to create a {@link QNModelWriter} which uses a different
   * structure, it will be necessary to override the {@code #persist()} method in
   * addition to implementing the {@code writeX(..)} methods.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  @Override
  public void persist() throws IOException {
    // the type of model (QN)
    writeUTF("QN");

    // the mapping from outcomes to their integer indexes
    writeInt(OUTCOME_LABELS.length);

    for (String outcomeLabel : OUTCOME_LABELS) writeUTF(outcomeLabel);

    // the mapping from predicates to the outcomes they contributed to.
    // The sorting is done so that we actually can write this out more
    // compactly than as the entire list.
    ComparablePredicate[] sorted = sortValues();
    List<List<ComparablePredicate>> compressed = compressOutcomes(sorted);

    writeInt(compressed.size());

    for (List<ComparablePredicate> a : compressed) {
      writeUTF(a.size() + a.get(0).toString());
    }

    // the mapping from predicate names to their integer indexes
    writeInt(PARAMS.length);

    for (ComparablePredicate predicate : sorted) writeUTF(predicate.name);

    // write out the parameters
    for (ComparablePredicate comparablePredicate : sorted)
      for (int j = 0; j < comparablePredicate.params.length; j++)
        writeDouble(comparablePredicate.params[j]);

    close();
  }
}

