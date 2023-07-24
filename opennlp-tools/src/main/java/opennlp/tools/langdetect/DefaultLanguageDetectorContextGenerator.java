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

import java.util.ArrayList;
import java.util.Collection;

import opennlp.tools.ngram.NGramCharModel;
import opennlp.tools.util.normalizer.AggregateCharSequenceNormalizer;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;

/**
 * A context generator for language detector.
 */
public class DefaultLanguageDetectorContextGenerator implements LanguageDetectorContextGenerator {

  protected final int minLength;
  protected final int maxLength;
  protected final CharSequenceNormalizer normalizer;

  /**
   * Creates a customizable {@link DefaultLanguageDetectorContextGenerator} that computes ngrams from text.
   *
   * @param minLength The min number of ngrams characters. Must be greater than {@code 0}.
   * @param maxLength The max number of ngrams characters. Must be greater than {@code 0}
   *                  and must be greater than {@code minLength}.
   * @param normalizers Zero or more normalizers to be applied in to the text before extracting ngrams.
   */
  public DefaultLanguageDetectorContextGenerator(int minLength, int maxLength,
                                                 CharSequenceNormalizer... normalizers) {
    this.minLength = minLength;
    this.maxLength = maxLength;

    this.normalizer = new AggregateCharSequenceNormalizer(normalizers);
  }

  @Override
  public <T extends CharSequence> T[] getContext(CharSequence document) {
    Collection<CharSequence> context = new ArrayList<>();

    NGramCharModel model = new NGramCharModel();
    model.add(normalizer.normalize(document), minLength, maxLength);

    for (CharSequence token : model) {
      context.add(token);
    }
    return (T[]) context.toArray(new CharSequence[0]);
  }
}
