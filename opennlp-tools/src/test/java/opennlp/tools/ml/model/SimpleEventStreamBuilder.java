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
import java.util.List;

import opennlp.tools.util.ObjectStream;

public class SimpleEventStreamBuilder {

  private final List<Event> eventList = new ArrayList<>();
  private int pos = 0;

  /*
   * the format of event should look like:
   * without values) other/w=he n1w=belongs n2w=to po=other pow=other,He powf=other,ic
   * with values) other/w=he;0.5 n1w=belongs;0.4 n2w=to;0.3 po=other;0.5 pow=other,He;0.25 powf=other,ic;0.5
   */
  public SimpleEventStreamBuilder add(String event) {
    String[] ss = event.split("/");
    if (ss.length != 2) {
      throw new RuntimeException(String.format("format error of the event \"%s\"", event));
    }

    // look for context (and values)
    String[] cvPairs = ss[1].split("\\s+");
    if (cvPairs[0].contains(";")) { // has values?
      String[] context = new String[cvPairs.length];
      float[] values = new float[cvPairs.length];
      for (int i = 0; i < cvPairs.length; i++) {
        String[] pair = cvPairs[i].split(";");
        if (pair.length != 2) {
          throw new RuntimeException(String.format("format error of the event \"%s\". "
                       + "\"%s\" doesn't have value", event, pair));
        }
        context[i] = pair[0];
        values[i] = Float.parseFloat(pair[1]);
      }
      eventList.add(new Event(ss[0], context, values));
    }
    else {
      eventList.add(new Event(ss[0], cvPairs));
    }

    return this;
  }

  public ObjectStream<Event> build() {
    return new ObjectStream<Event>() {
      @Override
      public Event read() throws IOException {
        if (eventList.size() <= pos) {
          return null;
        }
        return eventList.get(pos++);
      }
    };
  }
}
