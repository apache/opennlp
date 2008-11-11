/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.chunker;

import java.util.ArrayList;
import java.util.List;

import opennlp.maxent.DataStream;
import opennlp.model.Event;

/**
 * Class for creating an event stream out of data files for training a chunker.
 */
public class ChunkerEventStream extends opennlp.model.AbstractEventStream {

  private ChunkerContextGenerator cg;
  private DataStream data;
  private Event[] events;
  private int ei;

  /**
   * Creates a new event stream based on the specified data stream.
   * @param d The data stream for this event stream.
   */
  public ChunkerEventStream(DataStream d) {
    this(d, new DefaultChunkerContextGenerator());
  }

  /**
   * Creates a new event stream based on the specified data stream using the specified context generator.
   * @param d The data stream for this event stream.
   * @param cg The context generator which should be used in the creation of events for this event stream.
   */
  public ChunkerEventStream(DataStream d, ChunkerContextGenerator cg) {
    this.cg = cg;
    data = d;
    ei = 0;
    if (d.hasNext()) {
      addNewEvents();
    }
    else {
      events = new Event[0];
    }
  }

  /* inherieted javadoc */
  public Event next() {
    if (ei == events.length) {
      addNewEvents();
      ei = 0;
    }
    return events[ei++];
  }

  /* inherieted javadoc */
  public boolean hasNext() {
    return ei < events.length || data.hasNext();
  }

  private void addNewEvents() {
    List<String> toks = new ArrayList<String>();
    List<String> tags = new ArrayList<String>();
    List<String> preds = new ArrayList<String>();
    for (String line = (String) data.nextToken(); line !=null && !line.equals(""); line = (String) data.nextToken()) {
      String[] parts = line.split(" ");
      if (parts.length != 3) {
        System.err.println("Skipping corrupt line: "+line);
      }
      else {
        toks.add(parts[0]);
        tags.add(parts[1]);
        preds.add(parts[2]);
      }
    }
    events = new Event[toks.size()];
    String[] toksArray = toks.toArray(new String[toks.size()]);
    String[] tagsArray = tags.toArray(new String[tags.size()]);
    String[] predsArray = preds.toArray(new String[preds.size()]);
    for (int ei = 0, el = events.length; ei < el; ei++) {
      events[ei] = new Event(preds.get(ei), cg.getContext(ei,toksArray,tagsArray,predsArray));
    }
  }
}