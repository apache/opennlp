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

package opennlp.tools.langdetect;

import java.util.Iterator;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.ObjectStream;

/**
 * Iterator-like class for modeling an event stream of {@link LanguageSample samples}.
 */
public class LanguageDetectorEventStream extends AbstractEventStream<LanguageSample> {

  private final LanguageDetectorContextGenerator mContextGenerator;

  /**
   * Initializes an instance via samples and feature generators.
   *
   * @param data An {@link ObjectStream} of {@link LanguageSample samples} as input data.
   * @param cg A {@link LanguageDetectorContextGenerator} used for the event stream {@code data}.
   */
  public LanguageDetectorEventStream(ObjectStream<LanguageSample> data,
                                     LanguageDetectorContextGenerator cg) {
    super(data);

    mContextGenerator = cg;
  }

  @Override
  protected Iterator<Event> createEvents(final LanguageSample sample) {

    return new Iterator<>() {

      private boolean isVirgin = true;

      @Override
      public boolean hasNext() {
        return isVirgin;
      }

      @Override
      public Event next() {

        isVirgin = false;

        return new Event(sample.language().getLang(),
            mContextGenerator.getContext(sample.context().toString()));
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
