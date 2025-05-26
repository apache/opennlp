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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DictionaryLemmatizerMultiTest {

  private static DictionaryLemmatizer dictionaryLemmatizer;

  @BeforeAll
  static void loadDictionary() throws Exception {
    dictionaryLemmatizer = new DictionaryLemmatizer(
        DictionaryLemmatizerTest.class.getResourceAsStream(
            "/opennlp/tools/lemmatizer/smalldictionarymulti.dict")
    );
  }

  @Test
  void testForNullPointerException() {
    List<String> sentence = Arrays.asList("The", "dogs", "were", "running", "and", "barking",
        "down", "the", "street");
    List<String> sentencePOS = Arrays.asList("DT", "NNS", "VBD", "VBG", "CC", "VBG", "RP", "DT", "NN");
    List<List<String>> expectedLemmas = new ArrayList<>();
    expectedLemmas.add(List.of("the", "dog", "is", "run,run", "and", "bark, bark", "down", "the", "street"));

    List<List<String>> actualLemmas = dictionaryLemmatizer.lemmatize(sentence, sentencePOS);

    for (int i = 0; i < sentence.size(); i++) {
      // don't compare cases where the word is not in the dictionary...
      if (!actualLemmas.get(0).get(0).equals("O")) {
        Assertions.assertEquals(expectedLemmas.get(i), actualLemmas.get(i));
      }
    }
  }

}
