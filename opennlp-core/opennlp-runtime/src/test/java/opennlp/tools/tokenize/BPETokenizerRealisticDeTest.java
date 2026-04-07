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
 * German-language realistic BPE tokenization integration tests.
 *
 * @see AbstractBPETokenizerRealisticTest
 */
public class BPETokenizerRealisticDeTest extends AbstractBPETokenizerRealisticTest {

  @Override
  List<String> getTrainingCorpus() {
    return List.of(
        "Ich habe gestern einen alten Schulfreund in der Stadt getroffen",
        "Er hat mir von seiner neuen Arbeit in Berlin erzählt",
        "Die Arbeit ist sehr interessant und er ist sehr zufrieden",
        "Wir haben zusammen in einem kleinen Restaurant zu Mittag gegessen",
        "Das Essen war ausgezeichnet und die Bedienung sehr freundlich",
        "Nach dem Essen sind wir durch den Park spazieren gegangen",
        "Der Park war sehr schön und die Bäume hatten bunte Blätter",
        "Am Abend haben wir uns einen Film im Kino angesehen",
        "Der Film war spannend und hat uns beiden sehr gut gefallen",
        "Danach sind wir noch in eine Bar gegangen und haben geredet",
        "Er hat mir von seiner Reise nach Italien erzählt",
        "Die Reise war wunderbar und er hat viele Fotos gemacht",
        "Ich habe ihm von meiner Arbeit an die Monographie erzählt",
        "Die Monographie behandelt die Geschichte der botanischen Forschung",
        "Er fand das Thema sehr interessant und wollte mehr erfahren",
        "Wir haben uns verabredet nächste Woche wieder zu treffen",
        "Ich freue mich schon sehr auf unser nächstes Treffen",
        "Die Stadt ist im Herbst besonders schön mit den bunten Blättern",
        "Mein Freund wohnt jetzt in der Nähe vom Hauptbahnhof",
        "Er nimmt jeden Tag die Bahn zur Arbeit in die Innenstadt"
    );
  }

  @Override
  String getLanguageCode() {
    return "de";
  }

  @Override
  String getSimpleSentence() {
    return "Die Arbeit ist sehr interessant";
  }

  @Override
  String[] getSimpleSentenceExpectedWords() {
    return new String[] {"Die", "Arbeit", "ist", "sehr", "interessant"};
  }

  @Override
  List<String> getFrequentWords() {
    return List.of("die", "und", "er");
  }

  @Override
  String getUnseenWord() {
    return "Wissenschaftler";
  }

  @Override
  String getSpanTestSentence() {
    return "die Monographie behandelt die Geschichte";
  }

  @Override
  String[] getSpanTestExpectedWords() {
    return new String[] {"die", "Monographie", "behandelt", "die", "Geschichte"};
  }

  @Override
  String getMultiWordSentence() {
    return "Er hat mir von seiner Reise erzählt";
  }

  @Override
  String getSerializationTestSentence() {
    return "Wir haben zusammen in einem Restaurant gegessen";
  }

  @Override
  String getConsistencyTestSentence() {
    return "Der Park war sehr schön und die Bäume hatten bunte Blätter";
  }

  @Override
  String getPunctuationTestSentence() {
    return "Hallo, Welt!";
  }

  @Override
  String[] getExpectedPunctuationWords() {
    return new String[] {"Hallo,", "Welt!"};
  }

  @Override
  String getCoarseTokenizationSentence() {
    return "Ich habe ihm von meiner Arbeit an die Monographie erzählt";
  }
}
