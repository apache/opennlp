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

package opennlp.tools.lemmatizer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DictionaryLemmatizerTest {

  /**
   * Turkish folds {@code 'I'} to the dotless {@code 'ı'} (U+0131) instead of {@code 'i'}.
   */
  private static final Locale TURKISH = Locale.of("tr", "TR");

  private static DictionaryLemmatizer dictionaryLemmatizer;

  private final Locale defaultLocale = Locale.getDefault();

  @BeforeAll
  static void loadDictionary() throws Exception {
    dictionaryLemmatizer = new DictionaryLemmatizer(
        DictionaryLemmatizerTest.class.getResourceAsStream("/opennlp/tools/lemmatizer/smalldictionary.dict")
    );
  }

  @Test
  void testForNullPointerException() {
    String[] sentence = new String[] {"The", "dogs", "were", "running", "and", "barking",
        "down", "the", "street"};
    String[] sentencePOS = new String[] {"DT", "NNS", "VBD", "VBG", "CC", "VBG", "RP", "DT", "NN"};
    String[] expectedLemma = new String[] {"the", "dog", "is", "run", "and", "bark", "down", "the", "street"};

    String[] actualLemma = dictionaryLemmatizer.lemmatize(sentence, sentencePOS);

    for (int i = 0; i < sentence.length; i++) {
      // don't compare cases where the word is not in the dictionary...
      if (!actualLemma[i].equals("O")) {
        Assertions.assertEquals(expectedLemma[i], actualLemma[i]);
      }
    }
  }

  @AfterEach
  void restoreDefaultLocale() {
    Locale.setDefault(defaultLocale);
  }

  /**
   * The dictionary is read verbatim but looked up folded, so the fold has to match the
   * casing of the dictionary file rather than whatever locale the JVM happens to run in.
   */
  @Test
  void testLookupIsIndependentOfDefaultLocale() throws Exception {
    final String entries = "illinois\tNNP\tIllinois\n" + "indices\tNNS\tindex\n";
    final DictionaryLemmatizer lemmatizer = new DictionaryLemmatizer(
        new ByteArrayInputStream(entries.getBytes(StandardCharsets.UTF_8)));

    Locale.setDefault(TURKISH);

    Assertions.assertArrayEquals(new String[] {"Illinois", "index"},
        lemmatizer.lemmatize(new String[] {"Illinois", "INDICES"}, new String[] {"NNP", "NNS"}));
  }

}
