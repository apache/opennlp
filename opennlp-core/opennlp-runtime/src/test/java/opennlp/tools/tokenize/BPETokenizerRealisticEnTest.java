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
 * English-language realistic BPE tokenization integration tests.
 *
 * @see AbstractBPETokenizerRealisticTest
 */
public class BPETokenizerRealisticEnTest extends AbstractBPETokenizerRealisticTest {

  @Override
  List<String> getTrainingCorpus() {
    return List.of(
        "Last September I tried to find out the address of an old school friend",
        "whom I had not seen for 15 years",
        "I just knew his name Alan McKennedy and I had heard the rumour",
        "that he had moved to Scotland the country of his ancestors",
        "So I called Julie a friend who is still in contact with him",
        "She told me that he lived in Edinburgh Worcesterstreet 12",
        "I wrote him a letter right away and he answered soon",
        "sounding very happy and delighted",
        "Last year I wanted to write a letter to my grandaunt",
        "Her 86th birthday was on October 6 and I no longer wanted",
        "to be hesitant to get in touch with her",
        "I did not know her face to face and so it was not easy",
        "for me to find out her address",
        "As she had two apartments in different countries",
        "I decided to write to both",
        "The first was in Paris in Rue de Grandes Illusions 5",
        "But Marie Clara as my aunt is called preferred her apartment in Berlin",
        "She lived there in beautiful Kaiserstrasse 13 particularly in summer",
        "Hi my name is Michael Graf how much is a taxi",
        "from Ostbahnhof to Hauptbahnhof",
        "About 10 Euro I reckon",
        "That sounds good",
        "So please call a driver to Leonardstrasse 112 near the Ostbahnhof",
        "I would like to be at Silberhornstrasse 12 as soon as possible",
        "Thank you very much"
    );
  }

  @Override
  String getLanguageCode() {
    return "en";
  }

  @Override
  String getSimpleSentence() {
    return "I wrote a letter";
  }

  @Override
  String[] getSimpleSentenceExpectedWords() {
    return new String[] {"I", "wrote", "a", "letter"};
  }

  @Override
  List<String> getFrequentWords() {
    return List.of("the", "in");
  }

  @Override
  String getUnseenWord() {
    return "unbelievable";
  }

  @Override
  String getSpanTestSentence() {
    return "She lived in Edinburgh";
  }

  @Override
  String[] getSpanTestExpectedWords() {
    return new String[] {"She", "lived", "in", "Edinburgh"};
  }

  @Override
  String getMultiWordSentence() {
    return "I had not seen him for years";
  }

  @Override
  String getSerializationTestSentence() {
    return "I wrote him a letter right away";
  }

  @Override
  String getConsistencyTestSentence() {
    return "She told me that he lived in Edinburgh";
  }

  @Override
  String getPunctuationTestSentence() {
    return "Hello, world!";
  }

  @Override
  String[] getExpectedPunctuationWords() {
    return new String[] {"Hello,", "world!"};
  }

  @Override
  String getCoarseTokenizationSentence() {
    return "I wanted to write a letter to my grandaunt";
  }
}
