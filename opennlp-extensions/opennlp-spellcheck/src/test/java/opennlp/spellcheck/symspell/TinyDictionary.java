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

package opennlp.spellcheck.symspell;

import java.io.IOException;
import java.io.InputStream;

import opennlp.spellcheck.dictionary.FrequencyDictionaryLoader;
import opennlp.tools.util.InputStreamFactory;

/**
 * Test helper that builds a {@link SymSpell} engine from the small committed fixture
 * dictionaries under {@code src/test/resources/opennlp/spellcheck/}.
 */
public final class TinyDictionary {

  private static final String UNIGRAMS = "/opennlp/spellcheck/frequency_dictionary_tiny.txt";
  private static final String BIGRAMS = "/opennlp/spellcheck/frequency_bigramdictionary_tiny.txt";

  private TinyDictionary() {
  }

  /**
   * @return a {@link SymSpell} (maxDictionaryEditDistance=2) populated from the tiny
   *     unigram and bigram fixtures
   * @throws IOException if a fixture cannot be read
   */
  public static SymSpell load() throws IOException {
    final SymSpell engine =
        new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    loader.loadUnigrams(engine, resource(UNIGRAMS));
    loader.loadBigrams(engine, resource(BIGRAMS));
    return engine;
  }

  private static InputStreamFactory resource(String path) {
    return () -> {
      final InputStream in = TinyDictionary.class.getResourceAsStream(path);
      if (in == null) {
        throw new IOException("test resource not found: " + path);
      }
      return in;
    };
  }
}
