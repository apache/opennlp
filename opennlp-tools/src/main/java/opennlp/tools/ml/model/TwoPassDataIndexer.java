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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  public TwoPassDataIndexer() {}

  @Override
  public void index(ObjectStream<Event> eventStream) throws IOException {
    int cutoff = trainingParameters.getIntParameter(CUTOFF_PARAM, CUTOFF_DEFAULT);
    boolean sort = trainingParameters.getBooleanParameter(SORT_PARAM, SORT_DEFAULT);

    long start = System.currentTimeMillis();

    display("Indexing events with TwoPass using cutoff of " + cutoff + "\n\n");

    display("\tComputing event counts...  ");

    Map<String,Integer> predicateIndex = new HashMap<>();

    File tmp = File.createTempFile("events", null);
    tmp.deleteOnExit();
    int numEvents;
    try (Writer osw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp),
        StandardCharsets.UTF_8))) {
      numEvents = computeEventCounts(eventStream, osw, predicateIndex, cutoff);
    }
    display("done. " + numEvents + " events\n");

    display("\tIndexing...  ");

    List<ComparableEvent> eventsToCompare;
    try (FileEventStream fes = new FileEventStream(tmp)) {
      eventsToCompare = index(fes, predicateIndex);
    }

    tmp.delete();
    display("done.\n");

    if (sort) {
      display("Sorting and merging events... ");
    }
    else {
      display("Collecting events... ");
    }
    sortAndMerge(eventsToCompare,sort);
    display(String.format("Done indexing in %.2f s.\n", (System.currentTimeMillis() - start) / 1000d));
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

    Event ev;
    while ((ev = eventStream.read()) != null) {
      eventCount++;
      eventStore.write(FileEventStream.toLine(ev));
      String[] ec = ev.getContext();
      update(ec, counter);
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

    return eventCount;
  }
}
