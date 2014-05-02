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

import opennlp.tools.ml.model.Event;

public class EventTraceStream extends FilterObjectStream<Event, Event> {

  private Writer writer;

  public EventTraceStream(ObjectStream<Event> stream, Writer writer) {
    super(stream);

    this.writer = writer;
  }


  public Event read() throws IOException {
    Event event = samples.read();

    if (event != null) {
      writer.write(event.toString());
      writer.write("\n");
    }

    return event;
  }

  public void remove() {
    // TODO: not supported, should be removed ...
  }
}
