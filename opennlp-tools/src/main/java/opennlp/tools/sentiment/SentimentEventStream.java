/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentiment;

import java.util.Iterator;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.ObjectStream;

/**
 * Class for creating events for Sentiment Analysis that is later sent to
 * MaxEnt.
 */
public class SentimentEventStream extends AbstractEventStream<SentimentSample> {

  private SentimentContextGenerator contextGenerator;

  /**
   * Initializes the event stream.
   *
   * @param samples
   *          the sentiment samples to be used
   * @param createContextGenerator
   *          the context generator to be used
   */
  public SentimentEventStream(ObjectStream<SentimentSample> samples,
      SentimentContextGenerator createContextGenerator) {
    super(samples);
    contextGenerator = createContextGenerator;
  }

  /**
   * Creates events.
   *
   * @param sample
   *          the sentiment sample to be used
   * @return event iterator
   */
  @Override
  protected Iterator<Event> createEvents(final SentimentSample sample) {

    return new Iterator<Event>() {

      private boolean isVirgin = true;

      public boolean hasNext() {
        return isVirgin;
      }

      public Event next() {

        isVirgin = false;

        return new Event(sample.getSentiment(),
            contextGenerator.getContext(sample.getSentence()));
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

}
