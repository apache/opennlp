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

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import opennlp.tools.util.ObjectStream;

/**
 * An indexer for maxent model data which handles cutoffs for uncommon
 * contextual predicates and provides a unique integer index for each of the
 * predicates.
 */
public class OnePassDataIndexer extends AbstractDataIndexer {

  private static final Logger LOGGER = LoggerFactory.getLogger(OnePassDataIndexer.class);

  /**
   * One argument constructor for DataIndexer which calls the two argument
   * constructor assuming no cutoff.
   *
   * @param eventStream
   *          An Event[] which contains the a list of all the Events seen in the
   *          training data.
   */
  public OnePassDataIndexer(ObjectStream<Event> eventStream) throws IOException {
    this(eventStream, 0);
  }

  public OnePassDataIndexer(ObjectStream<Event> eventStream, int cutoff)
      throws IOException {
    this(eventStream, cutoff, true);
  }

  /**
   * Two argument constructor for DataIndexer.
   *
   * @param eventStream
   *          An Event[] which contains the a list of all the Events seen in the
   *          training data.
   * @param cutoff
   *          The minimum number of times a predicate must have been observed in
   *          order to be included in the model.
   */
  public OnePassDataIndexer(ObjectStream<Event> eventStream, int cutoff, boolean sort)
      throws IOException {
    Map<String, Integer> predicateIndex = new HashMap<String, Integer>();
    LinkedList<Event> events;
    List<ComparableEvent> eventsToCompare;

    LOGGER.info("Indexing events using cutoff of " + cutoff + "\n");

    LOGGER.info("\tComputing event counts...  ");
    events = computeEventCounts(eventStream, predicateIndex, cutoff);
    LOGGER.info("done. " + events.size() + " events");

    LOGGER.info("\tIndexing...  ");
    eventsToCompare = index(events, predicateIndex);
    // done with event list
    events = null;
    // done with predicates
    predicateIndex = null;

    LOGGER.info("done.");

    LOGGER.info("Sorting and merging events... ");
    sortAndMerge(eventsToCompare, sort);
    LOGGER.info("Done indexing.");
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
  private LinkedList<Event> computeEventCounts(ObjectStream<Event> eventStream,
      Map<String, Integer> predicatesInOut, int cutoff) throws IOException {
    Set<String> predicateSet = new HashSet<String>();
    Map<String, Integer> counter = new HashMap<String, Integer>();
    LinkedList<Event> events = new LinkedList<Event>();
    Event ev;
    while ((ev = eventStream.read()) != null) {
      events.addLast(ev);
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

  protected List<ComparableEvent> index(LinkedList<Event> events,
      Map<String, Integer> predicateIndex) {
    Map<String, Integer> omap = new HashMap<String, Integer>();

    int numEvents = events.size();
    int outcomeCount = 0;
    List<ComparableEvent> eventsToCompare = new ArrayList<ComparableEvent>(numEvents);
    List<Integer> indexedContext = new ArrayList<Integer>();

    for (int eventIndex = 0; eventIndex < numEvents; eventIndex++) {
      Event ev = events.removeFirst();
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
        LOGGER.warn("Dropped event " + ev.getOutcome() + ":"
            + Arrays.asList(ev.getContext()));
      }
      // recycle the TIntArrayList
      indexedContext.clear();
    }
    outcomeLabels = toIndexedStringArray(omap);
    predLabels = toIndexedStringArray(predicateIndex);
    return eventsToCompare;
  }

}
