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

package opennlp.tools.util;

import java.io.IOException;
import java.io.Writer;

import opennlp.model.Event;
import opennlp.model.EventStream;

public class EventTraceStream implements EventStream {

  private EventStream stream;
  private Writer writer;
  
  public EventTraceStream(EventStream stream, Writer writer) {
    this.stream = stream;
    this.writer = writer;
  }
  
  public boolean hasNext() throws IOException {
    return stream.hasNext();
  }

  public Event next() throws IOException {
    Event event = stream.next();
    
    try {
      writer.write(event.toString());
      writer.write("\n");
    } catch (IOException e) {
      // TODO: Fix this, we need error handling in event streams
      e.printStackTrace();
    }
    
    return event;
  }

  public void remove() {
    // TODO: not supported, should be removed ...
  }
}
