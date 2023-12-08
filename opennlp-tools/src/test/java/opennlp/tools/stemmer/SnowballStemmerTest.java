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

package opennlp.tools.stemmer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer.ALGORITHM;

public class SnowballStemmerTest {

  @Test
  void testArabic() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ARABIC);
    Assertions.assertEquals(stemmer.stem("أأباءاهم"), "اباء");
    Assertions.assertEquals(stemmer.stem("استفتياكما"), "استفتي");
    Assertions.assertEquals(stemmer.stem("استنتاجاتهما"), "استنتاجا");
  }

  @Test
  void testDanish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.DANISH);
    Assertions.assertEquals(stemmer.stem("aabenbaringen"), "aabenbaring");
    Assertions.assertEquals(stemmer.stem("skuebrødsbordene"), "skuebrødsbord");
    Assertions.assertEquals(stemmer.stem("skrøbeligheder"), "skrøb");
  }

  @Test
  void testDutch() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.DUTCH);
    Assertions.assertEquals(stemmer.stem("vliegtuigtransport"), "vliegtuigtransport");
    Assertions.assertEquals(stemmer.stem("sterlabcertificaat"), "sterlabcertificat");
    Assertions.assertEquals(stemmer.stem("vollegrondsgroenteteelt"),
        "vollegrondsgroenteteelt");

  }

  @Test
  void testCatalan() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.CATALAN);
    Assertions.assertEquals(stemmer.stem("importantíssimes"), "important");
    Assertions.assertEquals(stemmer.stem("besar"), "bes");
    Assertions.assertEquals(stemmer.stem("accidentalment"), "accidental");

  }

  @Test
  void testEnglish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ENGLISH);
    Assertions.assertEquals(stemmer.stem("accompanying"), "accompani");
    Assertions.assertEquals(stemmer.stem("malediction"), "maledict");
    Assertions.assertEquals(stemmer.stem("softeners"), "soften");

  }

  @Test // Context: OpenNLP-1229 - This is here to demonstrate & verify.
  void testStemThis() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ENGLISH);
    Assertions.assertEquals("this", stemmer.stem("this"));
  }

  @Test
  void testFinnish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.FINNISH);
    Assertions.assertEquals(stemmer.stem("esiintymispaikasta"), "esiintymispaik");
    Assertions.assertEquals(stemmer.stem("esiintyviätaiteilijaystäviään"),
        "esiintyviätaiteilijaystäviä");
    Assertions.assertEquals(stemmer.stem("hellbergiä"), "hellberg");

  }

  @Test
  void testFrench() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.FRENCH);
    Assertions.assertEquals(stemmer.stem("accomplissaient"), "accompl");
    Assertions.assertEquals(stemmer.stem("examinateurs"), "examin");
    Assertions.assertEquals(stemmer.stem("prévoyant"), "prévoi");
  }

  @Test
  void testGerman() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.GERMAN);
    Assertions.assertEquals(stemmer.stem("buchbindergesellen"), "buchbindergesell");
    Assertions.assertEquals(stemmer.stem("mindere"), "mind");
    Assertions.assertEquals(stemmer.stem("mitverursacht"), "mitverursacht");

  }

  @Test
  void testGreek() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.GREEK);
    Assertions.assertEquals(stemmer.stem("επιστροφή"), "επιστροφ");
    Assertions.assertEquals(stemmer.stem("Αμερικανών"), "αμερικαν");
    Assertions.assertEquals(stemmer.stem("στρατιωτών"), "στρατιωτ");

  }

  @Test
  void testHungarian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.HUNGARIAN);
    Assertions.assertEquals(stemmer.stem("abbahagynám"), "abbahagyna");
    Assertions.assertEquals(stemmer.stem("konstrukciójából"), "konstrukció");
    Assertions.assertEquals(stemmer.stem("lopta"), "lopt");

  }

  @Test
  void testIrish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.IRISH);
    Assertions.assertEquals(stemmer.stem("bhfeidhm"), "feidhm");
    Assertions.assertEquals(stemmer.stem("feirmeoireacht"), "feirmeoir");
    Assertions.assertEquals(stemmer.stem("monarcacht"), "monarc");

  }

  @Test
  void testItalian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ITALIAN);
    Assertions.assertEquals(stemmer.stem("abbattimento"), "abbatt");
    Assertions.assertEquals(stemmer.stem("dancer"), "dancer");
    Assertions.assertEquals(stemmer.stem("dance"), "danc");

  }

  @Test
  void testIndonesian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.INDONESIAN);
    Assertions.assertEquals(stemmer.stem("peledakan"), "ledak");
    Assertions.assertEquals(stemmer.stem("pelajaran"), "ajar");
    Assertions.assertEquals(stemmer.stem("perbaikan"), "baik");

  }

  @Test
  void testPortuguese() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.PORTUGUESE);
    Assertions.assertEquals(stemmer.stem("aborrecimentos"), "aborrec");
    Assertions.assertEquals(stemmer.stem("aché"), "aché");
    Assertions.assertEquals(stemmer.stem("ache"), "ache");

  }

  @Test
  void testRomanian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ROMANIAN);
    Assertions.assertEquals(stemmer.stem("absurdităţilor"), "absurd");
    Assertions.assertEquals(stemmer.stem("laşi"), "laș");
    Assertions.assertEquals(stemmer.stem("saracilor"), "sarac");
  }

  @Test
  void testSpanish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.SPANISH);
    Assertions.assertEquals(stemmer.stem("besó"), "bes");
    Assertions.assertEquals(stemmer.stem("importantísimas"), "importantisim");
    Assertions.assertEquals(stemmer.stem("incidental"), "incidental");
  }

  @Test
  void testSwedish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.SWEDISH);
    Assertions.assertEquals(stemmer.stem("aftonringningen"), "aftonringning");
    Assertions.assertEquals(stemmer.stem("andedrag"), "andedrag");
    Assertions.assertEquals(stemmer.stem("andedrägt"), "andedräg");

  }

  @Test
  void testTurkish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.TURKISH);
    Assertions.assertEquals(stemmer.stem("ab'yle"), "ab'yle");
    Assertions.assertEquals(stemmer.stem("kaçmamaktadır"), "kaçmamak");
    Assertions.assertEquals(stemmer.stem("sarayı'nı"), "sarayı'nı");
  }

  @Test
  void testFinish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.FINNISH);
    // reference: https://snowballstem.org/demo.html#Finnish
    Assertions.assertEquals("edeltän", stemmer.stem("edeltäneeseen"));  //r_LONG()
    Assertions.assertEquals("voita", stemmer.stem("voitaisiin")); // r_VI()
    Assertions.assertEquals("innostuks", stemmer.stem("innostuksessaan"));
  }

}
