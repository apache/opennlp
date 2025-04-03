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

package opennlp.tools.postag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.tokenize.ThreadSafeTokenizerME;
import opennlp.tools.tokenize.Tokenizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class POSTaggerMEIT {

  private static final String CATALAN = "ca";
  private static final String ENGLISH = "en";
  private static final String GERMAN = "de";
  private static final String POLISH = "pl";
  private static final String PORTUGUESE = "pt";

  private static final Map<String, Tokenizer> TOKENIZERS = new HashMap<>();
  private static final Map<String, POSTagger> TAGGERS = new HashMap<>();

  private static final boolean debug = false;

  @BeforeAll
  public static void initResources() throws IOException {
    List<String> langs = List.of(CATALAN, ENGLISH, GERMAN, POLISH, PORTUGUESE);
    for (String langCode: langs) {
      TOKENIZERS.put(langCode, new ThreadSafeTokenizerME(langCode));
      TAGGERS.put(langCode, new ThreadSafePOSTaggerME(langCode));
    }
  }

  @ParameterizedTest(name = "Verify \"{0}\" sample")
  @MethodSource(value = "provideData")
  void testPOSTagger(String langCode, int allowedDelta, String input, String[] expectedTags) {

    final String[] tokens = TOKENIZERS.get(langCode).tokenize(input);
    assertNotNull(tokens);
    assertEquals(expectedTags.length, tokens.length);
    final String[] tags = TAGGERS.get(langCode).tag(tokens);
    assertNotNull(tags);
    assertEquals(expectedTags.length, tags.length);
    StringBuilder fullyTagged = new StringBuilder();
    for (int i = 0; i < tags.length; i++) {
      fullyTagged.append(tokens[i]).append("_").append(tags[i]).append(" ");
    }
    if (debug) {
      System.out.println(fullyTagged);
    }

    List<Integer> incorrectTagsPositions = new ArrayList<>();
    for (int i = 0; i < tags.length; i++) {
      StringBuilder sb = new StringBuilder();
      sb.append(tokens[i]).append("[").append(tags[i]).append("]");
      if (expectedTags[i].equals(tags[i])) {
        sb.append(" <-- " + "OK");
      } else {
        sb.append(" <-- " + "NOK" + ", pos=").append(i);
        incorrectTagsPositions.add(i);
      }
      if (debug) {
        System.out.println(sb);
      }
    }
    assertTrue(incorrectTagsPositions.size() <= allowedDelta);
  }

  private static Stream<Arguments> provideData() {
    return Stream.of(
      // see: Dev Manual
      Arguments.of(ENGLISH, 0,
        "Mr. Vinken is chairman of Elsevier N.V. , the Dutch publishing group .",
          new String[]{"PROPN", "PROPN", "AUX", "NOUN", "ADP", "ADJ", "PROPN", "PUNCT", "DET", "PROPN",
            "VERB", "NOUN", "PUNCT"}),
      // see: 'de-ud-train-sample.conllu'
      Arguments.of(GERMAN, 0,
        "Fachlich kompetent, sehr gute Beratung und ein freundliches Team .",
          new String[]{"ADJ", "ADJ", "PUNCT", "ADV", "ADJ", "NOUN", "CCONJ", "DET", "ADJ", "NOUN", "PUNCT"}),
      // see: 'pt-br-ud-sample.conllu'
      Arguments.of(PORTUGUESE, 1,
        "Numa reunião entre representantes da Secretaria da Criança do DF ea juíza da Vara de Execuções de " +
        "Medidas Socioeducativas, Lavínia Tupi Vieira Fonseca, ficou acordado que dos 25 internos, " +
        "12 serão internados na Unidade de Planaltina e os outros 13 devem retornar para a Unidade do " +
        "Recanto das Emas, antigo Ciago .",
          // pos=10 -> NOK
          new String[]{"ADP+DET", "NOUN", "ADP", "NOUN", "ADP+DET", "PROPN", "ADP+DET", "PROPN", "ADP+DET",
            "PROPN", "CCONJ", "NOUN", "ADP+DET", "PROPN", "ADP", "PROPN", "ADP", "PROPN", "PROPN", "PUNCT",
            "PROPN", "PROPN", "PROPN", "PROPN", "PUNCT", "VERB", "ADJ", "CCONJ", "ADP+DET", "NUM", "NOUN",
            "PUNCT", "NUM", "AUX", "VERB", "ADP+DET", "PROPN", "ADP", "PROPN", "CCONJ", "DET", "DET", "NUM",
            "AUX", "VERB", "ADP", "DET", "PROPN", "ADP+DET", "PROPN", "ADP+DET", "PROPN", "PUNCT", "ADJ",
            "PROPN", "PUNCT"}),
      // via @alsmolarczyk, original by Lem, Stanisław (1961/2022):
      // Solaris, Wydawnictwo Literackie, Kraków, S. 81.
      Arguments.of(POLISH, 1,
        "Zerwałem się ze stosu zwiniętych spadochronów i pobiegłem prosto do radiostacji .",
          new String[]{"VERB+AUX", "PART", "ADP", "NOUN", "ADJ", "NOUN", "CCONJ", "VERB+AUX", "ADV", "ADP",
            "NOUN", "PUNCT"}),
      // via @alsmolarczyk, original by Tokarczuk, Olga (2009/2021):
      // Prowadź swój pług przez kości umarłych, Wydawnictwo Literackie, Kraków, S. 43-44.
      Arguments.of(POLISH, 0,
        "Więzienie nie tkwi na zewnątrz, ale jest w środku każdego z nas .",
          new String[]{"NOUN", "PART", "VERB", "ADP", "ADV", "PUNCT", "CCONJ", "VERB", "ADP", "NOUN",
            "DET", "ADP", "PRON", "PUNCT"}),
      // via @alsmolarczyk, original by Zalega, Dariusz (2019):
      // Śląsk zbuntowany, Wydawnictwo Czarne, Wołowiec, S. 96.
      Arguments.of(POLISH, 0,
        "Działacze stosowali też różne formy nacisku na polski konsulat , żeby zaopiekował się " +
        "bezrobotnymi z Polski albo dał im choćby na bezpłatny bilet do kraju .",
          new String[]{"NOUN", "VERB", "PART", "ADJ", "NOUN", "NOUN", "ADP", "ADJ", "NOUN", "PUNCT", "SCONJ", 
            "VERB", "PRON", "ADJ", "ADP", "PROPN", "CCONJ", "VERB", "PRON", "PART", "ADP", "ADJ", "NOUN", 
            "ADP", "NOUN", "PUNCT"}),
      // via: @kinow
      Arguments.of(CATALAN, 1,
        "Un gran embossament d'aire fred es comença a despenjar cap al centre d'Europa.",
          // OpenNLP, different at: idx pos 2, 3, 5, and 13(+14) -> however, only pos 5 is "wrong" (ref)
          new String[]{"DET", "ADJ", "NOUN", "ADP", "NOUN", "ADJ", "PRON", "VERB", "ADP", "VERB", "NOUN",
              "ADP+DET", "NOUN", "ADP", "PROPN", "PUNCT"})
      // REFERENCE ("gold"):
      // "DET", "ADJ", "NOUN", "ADP", "NOUN", "ADJ", "PRON", "VERB", "ADP", "VERB", "NOUN", "ADP+DET",
        // "NOUN", "ADP", "PROPN", "PUNCT"})

      // Spacy, wrong tags at: idx pos 2, 3 and 14
      //"DET", "ADJ", "ADV", "PROPN", "NOUN", "ADJ", "PRON", "VERB", "ADP", "VERB", "NOUN", "ADP" + "DET",
        // "NOUN", "PROPN", "PROPN", "PUNCT"
        // ok! ,  ok! ,  ??? ,  ???   ,  ok!  ,  ok! ,  ok!  ,  ok!  ,  ok! ,  ok!  ,  ok!  ,  ok!  +  ok! ,
        // ok!  ,  ???   ,  ok!   ,  ok!
    );
  }
}
