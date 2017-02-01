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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.util.ObjectStream;

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

    Map<String, Integer> predicateIndex = new HashMap<>();
    List<Event> events;
    List<ComparableEvent> eventsToCompare;

    display("Indexing events using cutoff of " + cutoff + "\n\n");

    display("\tComputing event counts...  ");
    events = computeEventCounts(eventStream, predicateIndex, cutoff);
    display("done. " + events.size() + " events\n");

    display("\tIndexing...  ");
    eventsToCompare = index(events, predicateIndex);
    // done with event list
    events = null;
    // done with predicates
    predicateIndex = null;

    display("done.\n");

    display("Sorting and merging events... ");
    sortAndMerge(eventsToCompare, sort);
    display("Done indexing.\n");
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
    Set<String> predicateSet = new HashSet<>();
    Map<String, Integer> counter = new HashMap<>();
    List<Event> events = new LinkedList<>();
    Event ev;
    while ((ev = eventStream.read()) != null) {
      events.add(ev);
      update(ev.getContext(), predicateSet, counter, cutoff);
    }
    predCounts = new int[predicateSet.size()];
    int index = 0;
    for (Iterator<String> pi = predicateSet.iterator(); pi.hasNext(); index++) {
      String predicate = pi.next();
      predCounts[index] = counter.get(predicate);
      predicatesInOut.put(predicate, index);
    }
    return events;
  }

  protected List<ComparableEvent> index(List<Event> events,
      Map<String, Integer> predicateIndex) {
    Map<String, Integer> omap = new HashMap<>();

    int numEvents = events.size();
    int outcomeCount = 0;
    List<ComparableEvent> eventsToCompare = new ArrayList<>(numEvents);
    List<Integer> indexedContext = new ArrayList<>();

    for (Event ev:events) {
      String[] econtext = ev.getContext();
      ComparableEvent ce;

      int ocID;
      String oc = ev.getOutcome();

      if (omap.containsKey(oc)) {
        ocID = omap.get(oc);
      } else {
        ocID = outcomeCount++;
        omap.put(oc, ocID);
      }

      for (String pred : econtext) {
        if (predicateIndex.containsKey(pred)) {
          indexedContext.add(predicateIndex.get(pred));
        }
      }

      // drop events with no active features
      if (indexedContext.size() > 0) {
        int[] cons = new int[indexedContext.size()];
        for (int ci = 0; ci < cons.length; ci++) {
          cons[ci] = indexedContext.get(ci);
        }
        ce = new ComparableEvent(ocID, cons);
        eventsToCompare.add(ce);
      } else {
        display("Dropped event " + ev.getOutcome() + ":"
            + Arrays.asList(ev.getContext()) + "\n");
      }
      // recycle the TIntArrayList
      indexedContext.clear();
    }
    outcomeLabels = toIndexedStringArray(omap);
    predLabels = toIndexedStringArray(predicateIndex);
    return eventsToCompare;
  }

}
