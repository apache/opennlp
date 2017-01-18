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
import java.util.Collections;
import java.util.Iterator;

import opennlp.tools.ml.model.Event;

public abstract class AbstractEventStream<T> implements ObjectStream<Event> {

  private ObjectStream<T> samples;

  private Iterator<Event> events = Collections.<Event>emptyList().iterator();

  /**
   * Initializes the current instance with a sample {@link Iterator}.
   *
   * @param samples the sample {@link Iterator}.
   */
  public AbstractEventStream(ObjectStream<T> samples) {
    this.samples = samples;
  }

  /**
   * Creates events for the provided sample.
   *
   * @param sample the sample for which training {@link Event}s
   *     are be created.
   *
   * @return an {@link Iterator} of training events or
   *     an empty {@link Iterator}.
   */
  protected abstract Iterator<Event> createEvents(T sample);

  @Override
  public final Event read() throws IOException {

    if (events.hasNext()) {
      return events.next();
    }
    else {
      T sample;
      while (!events.hasNext() && (sample = samples.read()) != null) {
        events = createEvents(sample);
      }

      if (events.hasNext()) {
        return read();
      }
    }

    return null;
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    events = Collections.emptyIterator();
    samples.reset();
  }

  @Override
  public void close() throws IOException {
    samples.close();
  }
}
