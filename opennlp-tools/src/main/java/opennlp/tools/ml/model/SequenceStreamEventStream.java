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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import opennlp.tools.util.ObjectStream;

/**
 * Class which turns a sequence stream into an event stream.
 */
public class SequenceStreamEventStream implements ObjectStream<Event> {

  private final SequenceStream sequenceStream;

  private Iterator<Event> eventIt = Collections.emptyListIterator();

  public SequenceStreamEventStream(SequenceStream sequenceStream) {
    this.sequenceStream = sequenceStream;
  }

  @Override
  public Event read() throws IOException {
    while (!eventIt.hasNext()) {
      Sequence<?> sequence = sequenceStream.read();
      if (sequence == null) {
        return null;
      }
      eventIt = Arrays.asList(sequence.getEvents()).iterator();
    }
    return eventIt.next();
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    eventIt = Collections.emptyListIterator();
    sequenceStream.reset();
  }

  @Override
  public void close() throws IOException {
    eventIt = Collections.emptyListIterator();
    sequenceStream.close();
  }
}
