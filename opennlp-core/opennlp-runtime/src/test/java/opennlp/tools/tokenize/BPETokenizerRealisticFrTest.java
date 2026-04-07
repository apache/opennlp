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

package opennlp.tools.tokenize;

import java.util.List;

/**
 * French-language realistic BPE tokenization integration tests.
 *
 * @see AbstractBPETokenizerRealisticTest
 */
public class BPETokenizerRealisticFrTest
    extends AbstractBPETokenizerRealisticTest {

  @Override
  List<String> getTrainingCorpus() {
    return List.of(
        "Hier je suis allé au marché pour acheter des fruits"
            + " et des légumes",
        "Le marché était plein de monde et les prix"
            + " étaient raisonnables",
        "Les pommes et les oranges étaient"
            + " particulièrement fraîches",
        "Je suis rentré à la maison et j'ai préparé"
            + " un bon repas",
        "Le repas était délicieux et toute la famille"
            + " était contente",
        "Après le repas nous avons fait une promenade"
            + " dans le parc",
        "Le parc était magnifique avec les arbres"
            + " en fleurs",
        "Les enfants jouaient dans le jardin et les"
            + " oiseaux chantaient",
        "Le soir nous avons regardé un film"
            + " à la télévision",
        "Le film était très intéressant et nous avons"
            + " bien aimé",
        "Mon ami Pierre habite dans une grande maison"
            + " à Paris",
        "Il travaille dans une entreprise de technologie"
            + " depuis cinq ans",
        "Sa femme Marie est professeur à une"
            + " université",
        "Ils ont deux enfants qui vont à une école"
            + " près de la maison",
        "Le weekend ils aiment faire des randonnées"
            + " dans la campagne",
        "La France est un beau pays avec une riche"
            + " histoire",
        "Paris est la capitale et la plus grande ville"
            + " du pays",
        "La cuisine française est connue dans le monde"
            + " entier",
        "Les musées de Paris attirent des millions"
            + " de visiteurs chaque année",
        "La Tour Eiffel est le monument le plus visité"
            + " de France"
    );
  }

  @Override
  String getLanguageCode() {
    return "fr";
  }

  @Override
  String getSimpleSentence() {
    return "Le repas était délicieux";
  }

  @Override
  String[] getSimpleSentenceExpectedWords() {
    return new String[] {"Le", "repas", "était", "délicieux"};
  }

  @Override
  List<String> getFrequentWords() {
    return List.of("les", "dans", "le");
  }

  @Override
  String getUnseenWord() {
    return "extraordinaire";
  }

  @Override
  String getSpanTestSentence() {
    return "Les enfants jouaient dans le jardin";
  }

  @Override
  String[] getSpanTestExpectedWords() {
    return new String[] {
        "Les", "enfants", "jouaient", "dans", "le", "jardin"
    };
  }

  @Override
  String getMultiWordSentence() {
    return "Le parc était magnifique avec les arbres"
        + " en fleurs";
  }

  @Override
  String getSerializationTestSentence() {
    return "Je suis allé au marché pour acheter"
        + " des fruits";
  }

  @Override
  String getConsistencyTestSentence() {
    return "Mon ami Pierre habite dans une grande"
        + " maison à Paris";
  }

  @Override
  String getPunctuationTestSentence() {
    return "Bonjour, monde!";
  }

  @Override
  String[] getExpectedPunctuationWords() {
    return new String[] {"Bonjour,", "monde!"};
  }

  @Override
  String getCoarseTokenizationSentence() {
    return "La cuisine française est connue dans le"
        + " monde entier";
  }
}
