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
public class BPETokenizerRealisticFrTest extends AbstractBPETokenizerRealisticTest {

  @Override
  List<String> getTrainingCorpus() {
    return List.of(
        "Hier je suis alle au marche pour acheter des fruits et des legumes",
        "Le marche etait plein de monde et les prix etaient raisonnables",
        "Les pommes et les oranges etaient particulierement fraiches",
        "Je suis rentre a la maison et je ai prepare un bon repas",
        "Le repas etait delicieux et toute la famille etait contente",
        "Apres le repas nous avons fait une promenade dans le parc",
        "Le parc etait magnifique avec les arbres en fleurs",
        "Les enfants jouaient dans le jardin et les oiseaux chantaient",
        "Le soir nous avons regarde un film a la television",
        "Le film etait tres interessant et nous avons bien aime",
        "Mon ami Pierre habite dans une grande maison a Paris",
        "Il travaille dans une entreprise de technologie depuis cinq ans",
        "Sa femme Marie est professeur a une universite",
        "Ils ont deux enfants qui vont a une ecole pres de la maison",
        "Le weekend ils aiment faire des randonnees dans la campagne",
        "La France est un beau pays avec une riche histoire",
        "Paris est la capitale et la plus grande ville du pays",
        "La cuisine francaise est connue dans le monde entier",
        "Les musees de Paris attirent des millions de visiteurs chaque annee",
        "La Tour Eiffel est le monument le plus visite de France"
    );
  }

  @Override
  String getLanguageCode() {
    return "fr";
  }

  @Override
  String getSimpleSentence() {
    return "Le repas etait delicieux";
  }

  @Override
  String[] getSimpleSentenceExpectedWords() {
    return new String[] {"Le", "repas", "etait", "delicieux"};
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
    return new String[] {"Les", "enfants", "jouaient", "dans", "le", "jardin"};
  }

  @Override
  String getMultiWordSentence() {
    return "Le parc etait magnifique avec les arbres en fleurs";
  }

  @Override
  String getSerializationTestSentence() {
    return "Je suis alle au marche pour acheter des fruits";
  }

  @Override
  String getConsistencyTestSentence() {
    return "Mon ami Pierre habite dans une grande maison a Paris";
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
    return "La cuisine francaise est connue dans le monde entier";
  }
}
