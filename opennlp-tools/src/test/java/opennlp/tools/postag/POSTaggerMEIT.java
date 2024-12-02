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
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class POSTaggerMEIT {

  private static final boolean debug = false;

  @ParameterizedTest(name = "Verify \"{0}\" sample")
  @MethodSource(value = "provideData")
  void testPOSTagger(String langCode, String input, String[] expectedTags) throws IOException {

    Tokenizer tokenizer = new TokenizerME(langCode);
    POSTagger tagger = new POSTaggerME(langCode);

    String[] tokens = tokenizer.tokenize(input);
    assertNotNull(tokens);
    assertEquals(expectedTags.length, tokens.length);
    String[] tags = tagger.tag(tokens);
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
      // assertEquals(expectedTags[i], tags[i]);
    }
    assertTrue(incorrectTagsPositions.size() <= 1);
  }

  private static Stream<Arguments> provideData() {
    return Stream.of(
      // see: Dev Manual
      Arguments.of("en",
        "Mr. Vinken is chairman of Elsevier N.V. , the Dutch publishing group .",
          new String[]{"PROPN", "PROPN", "AUX", "NOUN", "ADP", "ADJ", "PROPN", "PUNCT", "DET", "PROPN",
            "VERB", "NOUN", "PUNCT"}),
      // see: 'de-ud-train-sample.conllu'
      Arguments.of("de",
        "Fachlich kompetent, sehr gute Beratung und ein freundliches Team .",
          new String[]{"ADV", "ADJ", "PUNCT", "ADV", "ADJ", "NOUN", "CCONJ", "DET", "ADJ", "NOUN", "PUNCT"}),
      // see: 'pt-br-ud-sample.conllu'
      Arguments.of("pt",
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
      // see: @kinow
      Arguments.of("ca",
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
