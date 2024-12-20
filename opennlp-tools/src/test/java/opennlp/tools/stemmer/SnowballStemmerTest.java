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
    Assertions.assertEquals("اباء", stemmer.stem("أأباءاهم"));
    Assertions.assertEquals("استفتي", stemmer.stem("استفتياكما"));
    Assertions.assertEquals("استنتاجا", stemmer.stem("استنتاجاتهما"));
  }

  @Test
  void testDanish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.DANISH);
    Assertions.assertEquals("aabenbaring", stemmer.stem("aabenbaringen"));
    Assertions.assertEquals("skuebrødsbord", stemmer.stem("skuebrødsbordene"));
    Assertions.assertEquals("skrøb", stemmer.stem("skrøbeligheder"));
  }

  @Test
  void testDutch() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.DUTCH);
    Assertions.assertEquals("vliegtuigtransport", stemmer.stem("vliegtuigtransport"));
    Assertions.assertEquals("sterlabcertificat", stemmer.stem("sterlabcertificaat"));
    Assertions.assertEquals("vollegrondsgroenteteelt",
            stemmer.stem("vollegrondsgroenteteelt"));

  }

  @Test
  void testCatalan() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.CATALAN);
    Assertions.assertEquals("important", stemmer.stem("importantíssimes"));
    Assertions.assertEquals("bes", stemmer.stem("besar"));
    Assertions.assertEquals("accidental", stemmer.stem("accidentalment"));

  }

  @Test
  void testEnglish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ENGLISH);
    Assertions.assertEquals("accompani", stemmer.stem("accompanying"));
    Assertions.assertEquals("maledict", stemmer.stem("malediction"));
    Assertions.assertEquals("soften", stemmer.stem("softeners"));

  }

  @Test // Context: OpenNLP-1229 - This is here to demonstrate & verify.
  void testStemThis() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ENGLISH);
    Assertions.assertEquals("this", stemmer.stem("this"));
  }

  @Test
  void testFinnish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.FINNISH);
    Assertions.assertEquals("esiintymispaik", stemmer.stem("esiintymispaikasta"));
    Assertions.assertEquals("esiintyviätaiteilijaystäviä",
            stemmer.stem("esiintyviätaiteilijaystäviään"));
    Assertions.assertEquals("hellberg", stemmer.stem("hellbergiä"));

  }

  @Test
  void testFrench() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.FRENCH);
    Assertions.assertEquals("accompl", stemmer.stem("accomplissaient"));
    Assertions.assertEquals("examin", stemmer.stem("examinateurs"));
    Assertions.assertEquals("prévoi", stemmer.stem("prévoyant"));
  }

  @Test
  void testGerman() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.GERMAN);
    Assertions.assertEquals("buchbindergesell", stemmer.stem("buchbindergesellen"));
    Assertions.assertEquals("mind", stemmer.stem("mindere"));
    Assertions.assertEquals("mitverursacht", stemmer.stem("mitverursacht"));

  }

  @Test
  void testGreek() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.GREEK);
    Assertions.assertEquals("επιστροφ", stemmer.stem("επιστροφή"));
    Assertions.assertEquals("αμερικαν", stemmer.stem("Αμερικανών"));
    Assertions.assertEquals("στρατιωτ", stemmer.stem("στρατιωτών"));

  }

  @Test
  void testHungarian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.HUNGARIAN);
    Assertions.assertEquals("abbahagyna", stemmer.stem("abbahagynám"));
    Assertions.assertEquals("konstrukció", stemmer.stem("konstrukciójából"));
    Assertions.assertEquals("lopt", stemmer.stem("lopta"));

  }

  @Test
  void testIrish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.IRISH);
    Assertions.assertEquals("feidhm", stemmer.stem("bhfeidhm"));
    Assertions.assertEquals("feirmeoir", stemmer.stem("feirmeoireacht"));
    Assertions.assertEquals("monarc", stemmer.stem("monarcacht"));

  }

  @Test
  void testItalian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ITALIAN);
    Assertions.assertEquals("abbatt", stemmer.stem("abbattimento"));
    Assertions.assertEquals("dancer", stemmer.stem("dancer"));
    Assertions.assertEquals("danc", stemmer.stem("dance"));

  }

  @Test
  void testIndonesian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.INDONESIAN);
    Assertions.assertEquals("ledak", stemmer.stem("peledakan"));
    Assertions.assertEquals("ajar", stemmer.stem("pelajaran"));
    Assertions.assertEquals("baik", stemmer.stem("perbaikan"));

  }

  @Test
  void testPortuguese() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.PORTUGUESE);
    Assertions.assertEquals("aborrec", stemmer.stem("aborrecimentos"));
    Assertions.assertEquals("aché", stemmer.stem("aché"));
    Assertions.assertEquals("ache", stemmer.stem("ache"));

  }

  @Test
  void testRomanian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ROMANIAN);
    Assertions.assertEquals("absurd", stemmer.stem("absurdităţilor"));
    Assertions.assertEquals("laș", stemmer.stem("laşi"));
    Assertions.assertEquals("sarac", stemmer.stem("saracilor"));
  }

  @Test
  void testSpanish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.SPANISH);
    Assertions.assertEquals("bes", stemmer.stem("besó"));
    Assertions.assertEquals("importantisim", stemmer.stem("importantísimas"));
    Assertions.assertEquals("incidental", stemmer.stem("incidental"));
  }

  @Test
  void testSwedish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.SWEDISH);
    Assertions.assertEquals("aftonringning", stemmer.stem("aftonringningen"));
    Assertions.assertEquals("andedrag", stemmer.stem("andedrag"));
    Assertions.assertEquals("andedräg", stemmer.stem("andedrägt"));

  }

  @Test
  void testTurkish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.TURKISH);
    Assertions.assertEquals("ab'yle", stemmer.stem("ab'yle"));
    Assertions.assertEquals("kaçmamak", stemmer.stem("kaçmamaktadır"));
    Assertions.assertEquals("sarayı'nı", stemmer.stem("sarayı'nı"));
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
