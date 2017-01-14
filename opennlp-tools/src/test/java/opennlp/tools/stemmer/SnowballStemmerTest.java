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

import static org.junit.Assert.assertEquals;
import junit.framework.TestCase;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer.ALGORITHM;

import org.junit.Test;

public class SnowballStemmerTest {

  @Test
  public void testDanish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.DANISH);
    assertEquals(stemmer.stem("aabenbaringen"), "aabenbaring");
    assertEquals(stemmer.stem("skuebrødsbordene"), "skuebrødsbord");
    assertEquals(stemmer.stem("skrøbeligheder"), "skrøb");
  }

  @Test
  public void testDutch() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.DUTCH);
    assertEquals(stemmer.stem("vliegtuigtransport"), "vliegtuigtransport");
    assertEquals(stemmer.stem("sterlabcertificaat"), "sterlabcertificat");
    assertEquals(stemmer.stem("vollegrondsgroenteteelt"),
        "vollegrondsgroenteteelt");

  }

  @Test
  public void testEnglish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ENGLISH);
    assertEquals(stemmer.stem("accompanying"), "accompani");
    assertEquals(stemmer.stem("malediction"), "maledict");
    assertEquals(stemmer.stem("softeners"), "soften");

  }

  @Test
  public void testFinnish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.FINNISH);
    assertEquals(stemmer.stem("esiintymispaikasta"), "esiintymispaik");
    assertEquals(stemmer.stem("esiintyviätaiteilijaystäviään"),
        "esiintyviätaiteilijaystäviä");
    assertEquals(stemmer.stem("hellbergiä"), "hellberg");

  }

  @Test
  public void testFrench() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.FRENCH);
    assertEquals(stemmer.stem("accomplissaient"), "accompl");
    assertEquals(stemmer.stem("examinateurs"), "examin");
    assertEquals(stemmer.stem("prévoyant"), "prévoi");
  }

  @Test
  public void testGerman() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.GERMAN);
    assertEquals(stemmer.stem("buchbindergesellen"), "buchbindergesell");
    assertEquals(stemmer.stem("mindere"), "mind");
    assertEquals(stemmer.stem("mitverursacht"), "mitverursacht");

  }

  @Test
  public void testHungarian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.HUNGARIAN);
    assertEquals(stemmer.stem("abbahagynám"), "abbahagyna");
    assertEquals(stemmer.stem("konstrukciójából"), "konstrukció");
    assertEquals(stemmer.stem("lopta"), "lopt");

  }

  @Test
  public void testItalian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ITALIAN);
    assertEquals(stemmer.stem("abbattimento"), "abbatt");
    assertEquals(stemmer.stem("dancer"), "dancer");
    assertEquals(stemmer.stem("dance"), "danc");

  }

  @Test
  public void testPortuguese() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.PORTUGUESE);
    assertEquals(stemmer.stem("aborrecimentos"), "aborrec");
    assertEquals(stemmer.stem("aché"), "aché");
    assertEquals(stemmer.stem("ache"), "ache");

  }

  @Test
  public void testRomanian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ROMANIAN);
    assertEquals(stemmer.stem("absurdităţilor"), "absurd");
    assertEquals(stemmer.stem("laşi"), "laş");
    assertEquals(stemmer.stem("sechsunddreissig"), "sechsunddreissig");

  }

  @Test
  public void testSpanish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.SPANISH);
    assertEquals(stemmer.stem("besó"), "bes");
    assertEquals(stemmer.stem("importantísimas"), "importantisim");
    assertEquals(stemmer.stem("incidental"), "incidental");
  }

  @Test
  public void testSwedish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.SWEDISH);
    assertEquals(stemmer.stem("aftonringningen"), "aftonringning");
    assertEquals(stemmer.stem("andedrag"), "andedrag");
    assertEquals(stemmer.stem("andedrägt"), "andedräg");

  }

  @Test
  public void testTurkish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.TURKISH);
    assertEquals(stemmer.stem("ab'yle"), "ab'yle");
    assertEquals(stemmer.stem("kaçmamaktadır"), "kaçmamak");
    assertEquals(stemmer.stem("sarayı'nı"), "sarayı'nı");
  }
}
