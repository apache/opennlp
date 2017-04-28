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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer.ALGORITHM;

public class SnowballStemmerTest {

  @Test
  public void testDanish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.DANISH);
    Assert.assertEquals(stemmer.stem("aabenbaringen"), "aabenbaring");
    Assert.assertEquals(stemmer.stem("skuebrødsbordene"), "skuebrødsbord");
    Assert.assertEquals(stemmer.stem("skrøbeligheder"), "skrøb");
  }

  @Test
  public void testDutch() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.DUTCH);
    Assert.assertEquals(stemmer.stem("vliegtuigtransport"), "vliegtuigtransport");
    Assert.assertEquals(stemmer.stem("sterlabcertificaat"), "sterlabcertificat");
    Assert.assertEquals(stemmer.stem("vollegrondsgroenteteelt"),
        "vollegrondsgroenteteelt");

  }

  @Test
  public void testEnglish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ENGLISH);
    Assert.assertEquals(stemmer.stem("accompanying"), "accompani");
    Assert.assertEquals(stemmer.stem("malediction"), "maledict");
    Assert.assertEquals(stemmer.stem("softeners"), "soften");

  }

  @Test
  public void testFinnish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.FINNISH);
    Assert.assertEquals(stemmer.stem("esiintymispaikasta"), "esiintymispaik");
    Assert.assertEquals(stemmer.stem("esiintyviätaiteilijaystäviään"),
        "esiintyviätaiteilijaystäviä");
    Assert.assertEquals(stemmer.stem("hellbergiä"), "hellberg");

  }

  @Test
  public void testFrench() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.FRENCH);
    Assert.assertEquals(stemmer.stem("accomplissaient"), "accompl");
    Assert.assertEquals(stemmer.stem("examinateurs"), "examin");
    Assert.assertEquals(stemmer.stem("prévoyant"), "prévoi");
  }

  @Test
  public void testGerman() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.GERMAN);
    Assert.assertEquals(stemmer.stem("buchbindergesellen"), "buchbindergesell");
    Assert.assertEquals(stemmer.stem("mindere"), "mind");
    Assert.assertEquals(stemmer.stem("mitverursacht"), "mitverursacht");

  }

  @Test
  public void testHungarian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.HUNGARIAN);
    Assert.assertEquals(stemmer.stem("abbahagynám"), "abbahagyna");
    Assert.assertEquals(stemmer.stem("konstrukciójából"), "konstrukció");
    Assert.assertEquals(stemmer.stem("lopta"), "lopt");

  }

  @Test
  public void testIrish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.IRISH);
    Assert.assertEquals(stemmer.stem("bhfeidhm"), "feidhm");
    Assert.assertEquals(stemmer.stem("feirmeoireacht"), "feirmeoir");
    Assert.assertEquals(stemmer.stem("monarcacht"), "monarc");

  }

  @Test
  public void testItalian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ITALIAN);
    Assert.assertEquals(stemmer.stem("abbattimento"), "abbatt");
    Assert.assertEquals(stemmer.stem("dancer"), "dancer");
    Assert.assertEquals(stemmer.stem("dance"), "danc");

  }

  @Test
  public void testPortuguese() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.PORTUGUESE);
    Assert.assertEquals(stemmer.stem("aborrecimentos"), "aborrec");
    Assert.assertEquals(stemmer.stem("aché"), "aché");
    Assert.assertEquals(stemmer.stem("ache"), "ache");

  }

  @Test
  public void testRomanian() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.ROMANIAN);
    Assert.assertEquals(stemmer.stem("absurdităţilor"), "absurd");
    Assert.assertEquals(stemmer.stem("laşi"), "laş");
    Assert.assertEquals(stemmer.stem("sechsunddreissig"), "sechsunddreissig");

  }

  @Test
  public void testSpanish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.SPANISH);
    Assert.assertEquals(stemmer.stem("besó"), "bes");
    Assert.assertEquals(stemmer.stem("importantísimas"), "importantisim");
    Assert.assertEquals(stemmer.stem("incidental"), "incidental");
  }

  @Test
  public void testSwedish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.SWEDISH);
    Assert.assertEquals(stemmer.stem("aftonringningen"), "aftonringning");
    Assert.assertEquals(stemmer.stem("andedrag"), "andedrag");
    Assert.assertEquals(stemmer.stem("andedrägt"), "andedräg");

  }

  @Test
  public void testTurkish() {
    SnowballStemmer stemmer = new SnowballStemmer(ALGORITHM.TURKISH);
    Assert.assertEquals(stemmer.stem("ab'yle"), "ab'yle");
    Assert.assertEquals(stemmer.stem("kaçmamaktadır"), "kaçmamak");
    Assert.assertEquals(stemmer.stem("sarayı'nı"), "sarayı'nı");
  }
}
