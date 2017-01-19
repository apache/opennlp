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

package opennlp.tools.chunker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class ChunkSampleTest {

  private static String[] createSentence() {
    return new String[] {
        "Forecasts",
        "for",
        "the",
        "trade",
        "figures",
        "range",
        "widely",
        ",",
        "Forecasts",
        "for",
        "the",
        "trade",
        "figures",
        "range",
        "widely",
        "."
    };
  }

  private static String[] createTags() {

    return new String[] {
        "NNS",
        "IN",
        "DT",
        "NN",
        "NNS",
        "VBP",
        "RB",
        ",",
        "NNS",
        "IN",
        "DT",
        "NN",
        "NNS",
        "VBP",
        "RB",
        "."
    };
  }

  private static String[] createChunks() {
    return new String[] {
        "B-NP",
        "B-PP",
        "B-NP",
        "I-NP",
        "I-NP",
        "B-VP",
        "B-ADVP",
        "O",
        "B-NP",
        "B-PP",
        "B-NP",
        "I-NP",
        "I-NP",
        "B-VP",
        "B-ADVP",
        "O"
    };
  }

  public static ChunkSample createGoldSample() {
    return new ChunkSample(createSentence(), createTags(), createChunks());
  }

  public static ChunkSample createPredSample() {
    String[] chunks = createChunks();
    chunks[5] = "B-NP";
    return new ChunkSample(createSentence(), createTags(), chunks);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testParameterValidation() {
    new ChunkSample(new String[] {""}, new String[] {""},
        new String[] {"test", "one element to much"});
  }

  @Test
  public void testRetrievingContent() {
    ChunkSample sample = new ChunkSample(createSentence(), createTags(), createChunks());

    Assert.assertArrayEquals(createSentence(), sample.getSentence());
    Assert.assertArrayEquals(createTags(), sample.getTags());
    Assert.assertArrayEquals(createChunks(), sample.getPreds());
  }

  @Test
  public void testToString() throws IOException {

    ChunkSample sample = new ChunkSample(createSentence(), createTags(), createChunks());
    String[] sentence = createSentence();
    String[] tags = createTags();
    String[] chunks = createChunks();

    StringReader sr = new StringReader(sample.toString());
    BufferedReader reader = new BufferedReader(sr);
    for (int i = 0; i < sentence.length; i++) {
      String line = reader.readLine();
      String[] parts = line.split("\\s+");
      Assert.assertEquals(3, parts.length);
      Assert.assertEquals(sentence[i], parts[0]);
      Assert.assertEquals(tags[i], parts[1]);
      Assert.assertEquals(chunks[i], parts[2]);
    }
  }

  @Test
  public void testNicePrint() {

    ChunkSample sample = new ChunkSample(createSentence(), createTags(), createChunks());

    Assert.assertEquals(" [NP Forecasts_NNS ] [PP for_IN ] [NP the_DT trade_NN figures_NNS ] "
        + "[VP range_VBP ] [ADVP widely_RB ] ,_, [NP Forecasts_NNS ] [PP for_IN ] "
        + "[NP the_DT trade_NN figures_NNS ] "
        + "[VP range_VBP ] [ADVP widely_RB ] ._.", sample.nicePrint());
  }

  @Test
  public void testAsSpan() {
    ChunkSample sample = new ChunkSample(createSentence(), createTags(),
        createChunks());
    Span[] spans = sample.getPhrasesAsSpanList();

    Assert.assertEquals(10, spans.length);
    Assert.assertEquals(new Span(0, 1, "NP"), spans[0]);
    Assert.assertEquals(new Span(1, 2, "PP"), spans[1]);
    Assert.assertEquals(new Span(2, 5, "NP"), spans[2]);
    Assert.assertEquals(new Span(5, 6, "VP"), spans[3]);
    Assert.assertEquals(new Span(6, 7, "ADVP"), spans[4]);
    Assert.assertEquals(new Span(8, 9, "NP"), spans[5]);
    Assert.assertEquals(new Span(9, 10, "PP"), spans[6]);
    Assert.assertEquals(new Span(10, 13, "NP"), spans[7]);
    Assert.assertEquals(new Span(13, 14, "VP"), spans[8]);
    Assert.assertEquals(new Span(14, 15, "ADVP"), spans[9]);
  }


  // following are some tests to check the argument validation. Since all uses
  // the same validateArguments method, we do a deeper test only once

  @Test
  public void testPhraseAsSpan() {
    Span[] spans = ChunkSample.phrasesAsSpanList(createSentence(),
        createTags(), createChunks());

    Assert.assertEquals(10, spans.length);
    Assert.assertEquals(new Span(0, 1, "NP"), spans[0]);
    Assert.assertEquals(new Span(1, 2, "PP"), spans[1]);
    Assert.assertEquals(new Span(2, 5, "NP"), spans[2]);
    Assert.assertEquals(new Span(5, 6, "VP"), spans[3]);
    Assert.assertEquals(new Span(6, 7, "ADVP"), spans[4]);
    Assert.assertEquals(new Span(8, 9, "NP"), spans[5]);
    Assert.assertEquals(new Span(9, 10, "PP"), spans[6]);
    Assert.assertEquals(new Span(10, 13, "NP"), spans[7]);
    Assert.assertEquals(new Span(13, 14, "VP"), spans[8]);
    Assert.assertEquals(new Span(14, 15, "ADVP"), spans[9]);
  }

  @Test
  public void testRegions() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/chunker/output.txt");

    DummyChunkSampleStream predictedSample = new DummyChunkSampleStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8), false);

    ChunkSample cs1 = predictedSample.read();
    String[] g1 = Span.spansToStrings(cs1.getPhrasesAsSpanList(), cs1.getSentence());
    Assert.assertEquals(15, g1.length);

    ChunkSample cs2 = predictedSample.read();
    String[] g2 = Span.spansToStrings(cs2.getPhrasesAsSpanList(), cs2.getSentence());
    Assert.assertEquals(10, g2.length);

    ChunkSample cs3 = predictedSample.read();
    String[] g3 = Span.spansToStrings(cs3.getPhrasesAsSpanList(), cs3.getSentence());
    Assert.assertEquals(7, g3.length);
    Assert.assertEquals("United", g3[0]);
    Assert.assertEquals("'s directors", g3[1]);
    Assert.assertEquals("voted", g3[2]);
    Assert.assertEquals("themselves", g3[3]);
    Assert.assertEquals("their spouses", g3[4]);
    Assert.assertEquals("lifetime access", g3[5]);
    Assert.assertEquals("to", g3[6]);

    predictedSample.close();

  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidPhraseAsSpan1() {
    ChunkSample.phrasesAsSpanList(new String[2], new String[1], new String[1]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidPhraseAsSpan2() {
    ChunkSample.phrasesAsSpanList(new String[1], new String[2], new String[1]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidPhraseAsSpan3() {
    ChunkSample.phrasesAsSpanList(new String[1], new String[1], new String[2]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChunkSampleArray() {
    new ChunkSample(new String[1], new String[1], new String[2]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidChunkSampleList() {
    new ChunkSample(Arrays.asList(new String[1]), Arrays.asList(new String[1]),
        Arrays.asList(new String[2]));
  }

  @Test
  public void testEquals() {
    Assert.assertFalse(createGoldSample() == createGoldSample());
    Assert.assertTrue(createGoldSample().equals(createGoldSample()));
    Assert.assertFalse(createPredSample().equals(createGoldSample()));
    Assert.assertFalse(createPredSample().equals(new Object()));
  }

}
