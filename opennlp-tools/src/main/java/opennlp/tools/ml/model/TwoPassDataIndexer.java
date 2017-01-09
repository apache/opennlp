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

import opennlp.tools.util.ObjectStream;


/**
 * Collecting event and context counts by making two passes over the events.  The
 * first pass determines which contexts will be used by the model, and the
 * second pass creates the events in memory containing only the contexts which
 * will be used.  This greatly reduces the amount of memory required for storing
 * the events.  During the first pass a temporary event file is created which
 * is read during the second pass.
 */
public class TwoPassDataIndexer extends AbstractDataIndexer {

  /**
   * One argument constructor for DataIndexer which calls the two argument
   * constructor assuming no cutoff.
   *
   * @param eventStream An Event[] which contains the a list of all the Events
   *               seen in the training data.
   */
  @Deprecated
  public TwoPassDataIndexer(ObjectStream<Event> eventStream) throws IOException {
    this(eventStream, 0);
  }

  @Deprecated
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
  @Deprecated
  public TwoPassDataIndexer(ObjectStream<Event> eventStream, int cutoff, boolean sort) throws IOException {
    Map<String,Integer> predicateIndex = new HashMap<>();
    List<ComparableEvent> eventsToCompare;
  
    System.out.println("Indexing events using cutoff of " + cutoff + "\n");
  
    System.out.print("\tComputing event counts...  ");
    try {
      File tmp = File.createTempFile("events", null);
      tmp.deleteOnExit();
      Writer osw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp),"UTF8"));
      int numEvents = computeEventCounts(eventStream, osw, predicateIndex, cutoff);
      System.out.println("done. " + numEvents + " events");
  
      System.out.print("\tIndexing...  ");
  
      try (FileEventStream fes = new FileEventStream(tmp)) {
        eventsToCompare = index(numEvents, fes, predicateIndex);
      }
      // done with predicates
      predicateIndex = null;
      tmp.delete();
      System.out.println("done.");
  
      if (sort) {
        System.out.print("Sorting and merging events... ");
      }
      else {
        System.out.print("Collecting events... ");
      }
      sortAndMerge(eventsToCompare,sort);
      System.out.println("Done indexing.");
    }
    catch (IOException e) {
      System.err.println(e);
    }
  }

  public TwoPassDataIndexer() {}

  @Override
  public void index(ObjectStream<Event> eventStream) throws IOException {
    int cutoff = parameters.getIntParam(CUTOFF_PARAM, CUTOFF_DEFAULT);
    boolean sort = parameters.getBooleanParam(SORT_PARAM, SORT_DEFAULT);

    Map<String,Integer> predicateIndex = new HashMap<>();
    List<ComparableEvent> eventsToCompare;

    System.out.println("Indexing events using cutoff of " + cutoff + "\n");

    System.out.print("\tComputing event counts...  ");
    try {
      File tmp = File.createTempFile("events", null);
      tmp.deleteOnExit();
      Writer osw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp),"UTF8"));
      int numEvents = computeEventCounts(eventStream, osw, predicateIndex, cutoff);
      System.out.println("done. " + numEvents + " events");

      System.out.print("\tIndexing...  ");

      try (FileEventStream fes = new FileEventStream(tmp)) {
        eventsToCompare = index(numEvents, fes, predicateIndex);
      }
      // done with predicates
      predicateIndex = null;
      tmp.delete();
      System.out.println("done.");

      if (sort) {
        System.out.print("Sorting and merging events... ");
      }
      else {
        System.out.print("Collecting events... ");
      }
      sortAndMerge(eventsToCompare,sort);
      System.out.println("Done indexing.");
    }
    catch (IOException e) {
      System.err.println(e);
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
  private int computeEventCounts(ObjectStream<Event> eventStream, Writer eventStore,
      Map<String,Integer> predicatesInOut, int cutoff) throws IOException {
    Map<String,Integer> counter = new HashMap<>();
    int eventCount = 0;
    Set<String> predicateSet = new HashSet<>();

    Event ev;
    while ((ev = eventStream.read()) != null) {
      eventCount++;
      eventStore.write(FileEventStream.toLine(ev));
      String[] ec = ev.getContext();
      update(ec,predicateSet,counter,cutoff);
    }
    predCounts = new int[predicateSet.size()];
    int index = 0;
    for (Iterator<String> pi = predicateSet.iterator(); pi.hasNext(); index++) {
      String predicate = pi.next();
      predCounts[index] = counter.get(predicate);
      predicatesInOut.put(predicate,index);
    }
    eventStore.close();
    return eventCount;
  }

  private List<ComparableEvent> index(int numEvents, ObjectStream<Event> es, Map<String,Integer> predicateIndex) throws IOException {
    Map<String,Integer> omap = new HashMap<>();
    int outcomeCount = 0;
    List<ComparableEvent> eventsToCompare = new ArrayList<>(numEvents);
    List<Integer> indexedContext = new ArrayList<>();

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
        for (int ci = 0;ci < cons.length; ci++) {
          cons[ci] = indexedContext.get(ci);
        }
        ce = new ComparableEvent(ocID, cons);
        eventsToCompare.add(ce);
      }
      else {
        System.err.println("Dropped event " + ev.getOutcome() + ":" + Arrays.asList(ev.getContext()));
      }
      // recycle the TIntArrayList
      indexedContext.clear();
    }
    outcomeLabels = toIndexedStringArray(omap);
    predLabels = toIndexedStringArray(predicateIndex);
    return eventsToCompare;
  }

}

