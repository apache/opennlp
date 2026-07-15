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

package opennlp.tools.stemmer.hunspell;

import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;

/**
 * The shareable handle for Hunspell stemming: holds one immutable
 * {@link HunspellDictionary} and hands out {@link HunspellStemmer} instances over it.
 *
 * <p>The factory is immutable and safe to share across threads.</p>
 *
 * @since 3.0.0
 */
public class HunspellStemmerFactory implements StemmerFactory {

  private final HunspellDictionary dictionary;

  /**
   * Initializes the factory.
   *
   * @param dictionary The dictionary to stem against. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code dictionary} is {@code null}.
   */
  public HunspellStemmerFactory(HunspellDictionary dictionary) {
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary must not be null");
    }
    this.dictionary = dictionary;
  }

  @Override
  public Stemmer newStemmer() {
    return new HunspellStemmer(dictionary);
  }
}
