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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.ObjectStream;


/**
 * Collecting event and context counts by making two passes over the events.  The
 * first pass determines which contexts will be used by the model, and the
 * second pass creates the events in memory containing only the contexts which
 * will be used.  This greatly reduces the amount of memory required for storing
 * the events.  During the first pass a temporary event file is created which
 * is read during the second pass.
 */
public class TwoPassDataIndexer extends AbstractDataIndexer{

  private static final Logger LOGGER = LoggerFactory.getLogger(TwoPassDataIndexer.class);
			
  /**
   * One argument constructor for DataIndexer which calls the two argument
   * constructor assuming no cutoff.
   *
   * @param eventStream An Event[] which contains the a list of all the Events
   *               seen in the training data.
   */
  public TwoPassDataIndexer(ObjectStream<Event> eventStream) throws IOException {
    this(eventStream, 0);
  }

  public TwoPassDataIndexer(ObjectStream<Event> eventStream, int cutoff) throws IOException {
    this(eventStream,cutoff,true);
  }
  /**
   * Two argument constructor for DataIndexer.
   *
   * @param eventStream An Event[] which contains the a list of all the Events
   *               seen in the training data.
   * @param cutoff The minimum number of times a predicate must have been
   *               observed in order to be included in the model.
   */
  public TwoPassDataIndexer(ObjectStream<Event> eventStream, int cutoff, boolean sort) throws IOException {
    Map<String,Integer> predicateIndex = new HashMap<String,Integer>();
    List<ComparableEvent> eventsToCompare;

    LOGGER.info("Indexing events using cutoff of " + cutoff + "\n");

    LOGGER.info("\tComputing event counts...  ");
    try {
      File tmp = File.createTempFile("events", null);
      tmp.deleteOnExit();
      Writer osw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp),"UTF8"));
      int numEvents = computeEventCounts(eventStream, osw, predicateIndex, cutoff);
      LOGGER.info("done. " + numEvents + " events");

      LOGGER.info("\tIndexing...  ");

      FileEventStream fes = new FileEventStream(tmp);
      try {
        eventsToCompare = index(numEvents, fes, predicateIndex);
      } finally {
        fes.close();
      }
      // done with predicates
      predicateIndex = null;
      tmp.delete();
      LOGGER.info("done.");

      if (sort) {
        LOGGER.info("Sorting and merging events... ");
      }
      else {
        LOGGER.info("Collecting events... ");
      }
      sortAndMerge(eventsToCompare,sort);
      LOGGER.info("Done indexing.");
    }
    catch(IOException e) {
      LOGGER.error("Unable to index.", e);
    }
  }

  /**
      * Reads events from <tt>eventStream</tt> into a linked list.  The
      * predicates associated with each event are counted and any which
      * occur at least <tt>cutoff</tt> times are added to the
      * <tt>predicatesInOut</tt> map along with a unique integer index.
      *
      * @param eventStream an <code>EventStream</code> value
      * @param eventStore a writer to which the events are written to for later processing.
      * @param predicatesInOut a <code>TObjectIntHashMap</code> value
      * @param cutoff an <code>int</code> value
      */
  private int computeEventCounts(ObjectStream<Event> eventStream, Writer eventStore, Map<String,Integer> predicatesInOut, int cutoff) throws IOException {
    Map<String,Integer> counter = new HashMap<String,Integer>();
    int eventCount = 0;
    Set<String> predicateSet = new HashSet<String>();

    Event ev;
    while ((ev = eventStream.read()) != null) {
      eventCount++;
      eventStore.write(FileEventStream.toLine(ev));
      String[] ec = ev.getContext();
      update(ec,predicateSet,counter,cutoff);
    }
    predCounts = new int[predicateSet.size()];
    int index = 0;
    for (Iterator<String> pi=predicateSet.iterator();pi.hasNext();index++) {
      String predicate = pi.next();
      predCounts[index] = counter.get(predicate);
      predicatesInOut.put(predicate,index);
    }
    eventStore.close();
    return eventCount;
  }

  private List<ComparableEvent> index(int numEvents, ObjectStream<Event> es, Map<String,Integer> predicateIndex) throws IOException {
    Map<String,Integer> omap = new HashMap<String,Integer>();
    int outcomeCount = 0;
    List<ComparableEvent> eventsToCompare = new ArrayList<ComparableEvent>(numEvents);
    List<Integer> indexedContext = new ArrayList<Integer>();

    Event ev;
    while ((ev = es.read()) != null) {
      String[] econtext = ev.getContext();
      ComparableEvent ce;

      int ocID;
      String oc = ev.getOutcome();

      if (omap.containsKey(oc)) {
        ocID = omap.get(oc);
      }
      else {
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
        for (int ci=0;ci<cons.length;ci++) {
          cons[ci] = indexedContext.get(ci);
        }
        ce = new ComparableEvent(ocID, cons);
        eventsToCompare.add(ce);
      }
      else {
        LOGGER.warn("Dropped event " + ev.getOutcome() + ":" + Arrays.asList(ev.getContext()));
      }
      // recycle the TIntArrayList
      indexedContext.clear();
    }
    outcomeLabels = toIndexedStringArray(omap);
    predLabels = toIndexedStringArray(predicateIndex);
    return eventsToCompare;
  }

}

