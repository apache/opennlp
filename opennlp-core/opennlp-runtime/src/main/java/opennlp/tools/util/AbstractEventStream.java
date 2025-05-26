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

/**
 * A base {@link ObjectStream} implementation for events.
 *
 * @param <T> The generic type representing samples.
 */
public abstract class AbstractEventStream<T> implements ObjectStream<Event> {

  private final ObjectStream<T> samples;

  private Iterator<Event> events = Collections.emptyIterator();

  /**
   * Initializes an {@link AbstractEventStream} with a sample {@link Iterator}.
   *
   * @param samples The {@link Iterator} that provides the {@link T} samples.
   */
  public AbstractEventStream(ObjectStream<T> samples) {
    this.samples = samples;
  }

  /**
   * Creates events for the provided {@code sample}.
   *
   * @param sample The {@link T sample} for which training {@link Event events} are created.
   *
   * @return An {@link Iterator} of training events or an empty {@link Iterator}.
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
