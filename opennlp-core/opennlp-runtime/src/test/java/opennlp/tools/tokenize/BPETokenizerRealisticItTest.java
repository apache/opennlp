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
 * Italian-language realistic BPE tokenization integration tests.
 *
 * @see AbstractBPETokenizerRealisticTest
 */
public class BPETokenizerRealisticItTest extends AbstractBPETokenizerRealisticTest {

  @Override
  List<String> getTrainingCorpus() {
    return List.of(
        "Ieri sono andato al mercato per comprare della frutta e della verdura",
        "Il mercato era pieno di gente e i prezzi erano ragionevoli",
        "Le mele e le arance erano particolarmente fresche e buone",
        "Sono tornato a casa e ho preparato un buon pranzo",
        "Il pranzo era delizioso e tutta la famiglia era contenta",
        "Dopo il pranzo abbiamo fatto una passeggiata nel parco",
        "Il parco era bellissimo con gli alberi in fiore",
        "I bambini giocavano nel giardino e gli uccelli cantavano",
        "La sera abbiamo guardato un film alla televisione",
        "Il film era molto interessante e ci e piaciuto tanto",
        "Il mio amico Marco abita in una grande casa a Roma",
        "Lui lavora in una azienda di tecnologia da cinque anni",
        "Sua moglie Giulia e professoressa alla universita",
        "Hanno due bambini che vanno a una scuola vicino a casa",
        "Nel fine settimana amano fare delle escursioni in campagna",
        "Roma e una citta bellissima con una storia molto ricca",
        "La cucina italiana e conosciuta in tutto il mondo",
        "I musei di Roma attraggono milioni di visitatori ogni anno",
        "Il Colosseo e il monumento piu visitato di Roma",
        "La vita in Italia e molto piacevole e rilassante"
    );
  }

  @Override
  String getLanguageCode() {
    return "it";
  }

  @Override
  String getSimpleSentence() {
    return "Il pranzo era delizioso";
  }

  @Override
  String[] getSimpleSentenceExpectedWords() {
    return new String[] {"Il", "pranzo", "era", "delizioso"};
  }

  @Override
  List<String> getFrequentWords() {
    return List.of("il", "una", "in");
  }

  @Override
  String getUnseenWord() {
    return "straordinario";
  }

  @Override
  String getSpanTestSentence() {
    return "I bambini giocavano nel giardino";
  }

  @Override
  String[] getSpanTestExpectedWords() {
    return new String[] {"I", "bambini", "giocavano", "nel", "giardino"};
  }

  @Override
  String getMultiWordSentence() {
    return "Il parco era bellissimo con gli alberi in fiore";
  }

  @Override
  String getSerializationTestSentence() {
    return "Sono andato al mercato per comprare della frutta";
  }

  @Override
  String getConsistencyTestSentence() {
    return "Il mio amico Marco abita in una grande casa a Roma";
  }

  @Override
  String getPunctuationTestSentence() {
    return "Ciao, mondo!";
  }

  @Override
  String[] getExpectedPunctuationWords() {
    return new String[] {"Ciao,", "mondo!"};
  }

  @Override
  String getCoarseTokenizationSentence() {
    return "La cucina italiana e conosciuta in tutto il mondo";
  }
}
