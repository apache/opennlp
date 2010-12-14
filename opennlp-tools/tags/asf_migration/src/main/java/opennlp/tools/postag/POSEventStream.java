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


package opennlp.tools.postag;

import java.io.FileInputStream;
import java.io.StringReader;

import opennlp.maxent.DataStream;
import opennlp.model.Event;
import opennlp.model.EventCollector;
import opennlp.model.EventStream;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;

/**
 * An implementation of EventStream whcih assumes the data stream gives a
 * sentence at a time with tokens as word_tag pairs.
 */
@Deprecated
public class POSEventStream extends opennlp.model.AbstractEventStream {

  private POSContextGenerator cg;
  private DataStream data;
  private Event[] events;
  private int ei;

  /** The last line read in from the data file. */
  private String line;

  /**
   * Initializes the current instance.
   *
   * @param d
   */
  public POSEventStream(DataStream d) {
    this(d, new DefaultPOSContextGenerator(null));
  }

  /**
   * Initializes the current instance.
   *
   * @param d
   * @param dict
   */
  public POSEventStream(DataStream d, Dictionary dict) {
    this(d, new DefaultPOSContextGenerator(dict));
  }

  /**
   * Initializes the current instance.
   *
   * @param d
   * @param cg
   */
  public POSEventStream(DataStream d, POSContextGenerator cg) {
    this.cg = cg;
    data = d;
    ei = 0;
    if (d.hasNext()) {
      addNewEvents((String) d.nextToken());
    }
    else {
      events = new Event[0];
    }
  }

  public boolean hasNext() {
    if (ei < events.length) {
      return true;
    }
    else if (line != null) { // previous result has not been consumed
      return true;
    }
    //find next non-blank line
    while (data.hasNext()) {
      line = (String) data.nextToken();
      if (line.equals("")) {
      }
      else {
        return true;
      }
    }
    return false;
  }

  public Event next() {
    if (ei == events.length) {
      addNewEvents(line);
      ei = 0;
      line = null;
    }
    return events[ei++];
  }



  private void addNewEvents(String sentence) {
    //String sentence = "the_DT stories_NNS about_IN well-heeled_JJ communities_NNS and_CC developers_NNS";
    EventCollector ec = new POSEventCollector(new StringReader(sentence), cg);
    events = ec.getEvents();
    //System.err.println("POSEventStream.addNewEvents: got "+events.length+" events");
  }

  public static void main(String[] args) throws java.io.IOException, InvalidFormatException {
    EventStream es;
    if (args.length == 0) {
      es = new POSEventStream(new opennlp.maxent.PlainTextByLineDataStream(new java.io.InputStreamReader(System.in)));
    }
    else {
      es = new POSEventStream(new opennlp.maxent.PlainTextByLineDataStream(new java.io.InputStreamReader(System.in)),new Dictionary(new FileInputStream(args[0])));
    }
    while (es.hasNext()) {
      System.out.println(es.next());
    }
  }
}
