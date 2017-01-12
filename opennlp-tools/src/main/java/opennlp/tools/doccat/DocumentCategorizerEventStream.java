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

package opennlp.tools.doccat;

import java.util.Iterator;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.ObjectStream;

/**
 * Iterator-like class for modeling document classification events.
 */
public class DocumentCategorizerEventStream extends AbstractEventStream<DocumentSample> {

  private DocumentCategorizerContextGenerator mContextGenerator;

  /**
   * Initializes the current instance via samples and feature generators.
   *
   * @param data {@link ObjectStream} of {@link DocumentSample}s
   *
   * @param featureGenerators the feature generators
   */
  public DocumentCategorizerEventStream(ObjectStream<DocumentSample> data,
      FeatureGenerator... featureGenerators) {
    super(data);

    mContextGenerator =
        new DocumentCategorizerContextGenerator(featureGenerators);
  }

  /**
   * Initializes the current instance.
   *
   * @param samples {@link ObjectStream} of {@link DocumentSample}s
   */
  public DocumentCategorizerEventStream(ObjectStream<DocumentSample> samples) {
    super(samples);

    mContextGenerator =
        new DocumentCategorizerContextGenerator(new BagOfWordsFeatureGenerator());
  }

  @Override
  protected Iterator<Event> createEvents(final DocumentSample sample) {

    return new Iterator<Event>() {

      private boolean isVirgin = true;

      public boolean hasNext() {
        return isVirgin;
      }

      public Event next() {

        isVirgin = false;

        return new Event(sample.getCategory(),
            mContextGenerator.getContext(sample.getText(), sample.getExtraInformation()));
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
