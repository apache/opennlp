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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

/**
 * An indexer for maxent model data which handles cutoffs for uncommon
 * contextual predicates and provides a unique integer index for each of the
 * predicates.
 */
public class OnePassDataIndexer extends AbstractDataIndexer {

  public OnePassDataIndexer(){}

  @Override
  public void index(ObjectStream<Event> eventStream) throws IOException {
    int cutoff = trainingParameters.getIntParameter(CUTOFF_PARAM, CUTOFF_DEFAULT);
    boolean sort = trainingParameters.getBooleanParameter(SORT_PARAM, SORT_DEFAULT);

    long start = System.currentTimeMillis();

    display("Indexing events with OnePass using cutoff of " + cutoff + "\n\n");

    display("\tComputing event counts...  ");
    Map<String, Integer> predicateIndex = new HashMap<>();
    List<Event> events = computeEventCounts(eventStream, predicateIndex, cutoff);
    display("done. " + events.size() + " events\n");

    display("\tIndexing...  ");
    List<ComparableEvent> eventsToCompare =
        index(ObjectStreamUtils.createObjectStream(events), predicateIndex);

    display("done.\n");

    display("Sorting and merging events... ");
    sortAndMerge(eventsToCompare, sort);
    display(String.format("Done indexing in %.2f s.\n", (System.currentTimeMillis() - start) / 1000d));
  }

  /**
   * Reads events from <tt>eventStream</tt> into a linked list. The predicates
   * associated with each event are counted and any which occur at least
   * <tt>cutoff</tt> times are added to the <tt>predicatesInOut</tt> map along
   * with a unique integer index.
   *
   * @param eventStream
   *          an <code>EventStream</code> value
   * @param predicatesInOut
   *          a <code>TObjectIntHashMap</code> value
   * @param cutoff
   *          an <code>int</code> value
   * @return a <code>TLinkedList</code> value
   */
  private List<Event> computeEventCounts(ObjectStream<Event> eventStream,
      Map<String, Integer> predicatesInOut, int cutoff) throws IOException {

    Map<String, Integer> counter = new HashMap<>();
    List<Event> events = new LinkedList<>();
    Event ev;
    while ((ev = eventStream.read()) != null) {
      events.add(ev);
      update(ev.getContext(), counter);
    }

    String[] predicateSet = counter.entrySet().stream()
        .filter(entry -> entry.getValue() >= cutoff)
        .map(Map.Entry::getKey).sorted()
        .toArray(String[]::new);

    predCounts = new int[predicateSet.length];
    for (int i = 0; i < predicateSet.length; i++) {
      predCounts[i] = counter.get(predicateSet[i]);
      predicatesInOut.put(predicateSet[i], i);
    }

    return events;
  }
}
