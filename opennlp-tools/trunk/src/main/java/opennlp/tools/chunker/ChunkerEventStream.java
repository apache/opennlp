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

package opennlp.tools.chunker;

import java.io.IOException;

import opennlp.model.Event;
import opennlp.tools.util.ObjectStream;

/**
 * Class for creating an event stream out of data files for training a chunker.
 */
public class ChunkerEventStream extends opennlp.model.AbstractEventStream {

  private ChunkerContextGenerator cg;
  private ObjectStream<ChunkSample> data;
  private Event[] events;
  private int ei;

  
  /**
   * Creates a new event stream based on the specified data stream using the specified context generator.
   * @param d The data stream for this event stream.
   * @param cg The context generator which should be used in the creation of events for this event stream.
   */
  public ChunkerEventStream(ObjectStream<ChunkSample> d, ChunkerContextGenerator cg) {
    this.cg = cg;
    data = d;
    ei = 0;
    addNewEvents();
  }
  
  /**
   * Creates a new event stream based on the specified data stream.
   * @param d The data stream for this event stream.
   */
  public ChunkerEventStream(ObjectStream<ChunkSample> d) {
    this(d, new DefaultChunkerContextGenerator());
  }

  public Event next() {
    
    hasNext();
    
    return events[ei++];
  }

  public boolean hasNext() {
    if (ei == events.length) {
      addNewEvents();
      ei = 0;
    }
    return ei < events.length;
  }

  private void addNewEvents() {
    
    ChunkSample sample;
    try {
      sample = data.read();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    
    if (sample != null) {
      events = new Event[sample.getSentence().length];
      String[] toksArray = sample.getSentence();
      String[] tagsArray = sample.getTags();
      String[] predsArray = sample.getPreds();
      for (int ei = 0, el = events.length; ei < el; ei++) {
        events[ei] = new Event(predsArray[ei], cg.getContext(ei,toksArray,tagsArray,predsArray));
      }
    }
    else {
      events = new Event[0];
    }
  }
}
