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

package opennlp.tools.ml.model;

import java.util.List;

import opennlp.tools.util.InsufficientTrainingDataException;

/**
 * An indexer for maxent model data which handles cutoffs for uncommon
 * contextual predicates and provides a unique integer index for each of the
 * predicates and maintains event values.
 */
public class OnePassRealValueDataIndexer extends OnePassDataIndexer {

  float[][] values;

  public OnePassRealValueDataIndexer() {
  }

  public float[][] getValues() {
    return values;
  }

  protected int sortAndMerge(List<ComparableEvent> eventsToCompare, boolean sort)
      throws InsufficientTrainingDataException {
    int numUniqueEvents = super.sortAndMerge(eventsToCompare,sort);
    values = new float[numUniqueEvents][];
    int numEvents = eventsToCompare.size();
    for (int i = 0, j = 0; i < numEvents; i++) {
      ComparableEvent evt = eventsToCompare.get(i);
      if (null == evt) {
        continue; // this was a dupe, skip over it.
      }
      values[j++] = evt.values;
    }
    return numUniqueEvents;
  }
}
