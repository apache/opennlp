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

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;

import org.junit.Before;
import org.junit.Test;

/**
 * This is the test class for {@link NameFinderME}.
 * <p>
 * A proper testing and evaluation of the name finder is only possible with a
 * large corpus which contains a huge amount of test sentences.
 * <p>
 * The scope of this test is to make sure that the name finder code can be
 * executed. This test can not detect mistakes which lead to incorrect feature
 * generation or other mistakes which decrease the tagging performance of the
 * name finder.
 * <p>
 * In this test the {@link NameFinderME} is trained with a small amount of
 * training sentences and then the computed model is used to predict sentences
 * from the training sentences.
 */
public class ChunkerMETest {

  private Chunker chunker;

  String[] toks1 = { "Rockwell", "said", "the", "agreement", "calls", "for",
      "it", "to", "supply", "200", "additional", "so-called", "shipsets",
      "for", "the", "planes", "." };

  String[] tags1 = { "NNP", "VBD", "DT", "NN", "VBZ", "IN", "PRP", "TO", "VB",
      "CD", "JJ", "JJ", "NNS", "IN", "DT", "NNS", "." };

  String[] expect1 = { "B-NP", "B-VP", "B-NP", "I-NP", "B-VP", "B-SBAR",
      "B-NP", "B-VP", "I-VP", "B-NP", "I-NP", "I-NP", "I-NP", "B-PP", "B-NP",
      "I-NP", "O" };

  @Before
  public void startup() throws IOException {
    // train the chunker

    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/chunker/test.txt");

    String encoding = "UTF-8";

    ObjectStream<ChunkSample> sampleStream = new ChunkSampleStream(
        new PlainTextByLineStream(new InputStreamReader(in, encoding)));

    ChunkerModel chunkerModel = ChunkerME.train("en", sampleStream, 1, 70);

    this.chunker = new ChunkerME(chunkerModel);
  }

  @Test
  public void testChunkAsArray() throws Exception {

    String preds[] = chunker.chunk(toks1, tags1);

    assertArrayEquals(expect1, preds);
  }

  @Test
  public void testChunkAsSpan() throws Exception {

    Span[] preds = chunker.chunkAsSpans(toks1, tags1);
    System.out.println(Arrays.toString(preds));

    assertEquals(10, preds.length);
    assertEquals(new Span(0, 1, "NP"), preds[0]);
    assertEquals(new Span(1, 2, "VP"), preds[1]);
    assertEquals(new Span(2, 4, "NP"), preds[2]);
    assertEquals(new Span(4, 5, "VP"), preds[3]);
    assertEquals(new Span(5, 6, "SBAR"), preds[4]);
    assertEquals(new Span(6, 7, "NP"), preds[5]);
    assertEquals(new Span(7, 9, "VP"), preds[6]);
    assertEquals(new Span(9, 13, "NP"), preds[7]);
    assertEquals(new Span(13, 14, "PP"), preds[8]);
    assertEquals(new Span(14, 16, "NP"), preds[9]);

  }

  @Test
  public void testChunkAsList() throws Exception {

    @SuppressWarnings("deprecation")
    List<String> preds = chunker.chunk(Arrays.asList(toks1),
        Arrays.asList(tags1));

    assertEquals(Arrays.asList(expect1), preds);
  }

  @Test
  public void testTokenProbList() throws Exception {

    @SuppressWarnings("deprecation")
    Sequence[] preds = chunker.topKSequences(Arrays.asList(toks1),
        Arrays.asList(tags1));

    assertTrue(preds.length > 0);
    assertEquals(expect1.length, preds[0].getProbs().length);
    assertEquals(Arrays.asList(expect1), preds[0].getOutcomes());
    assertNotSame(Arrays.asList(expect1), preds[1].getOutcomes());
  }
  
  @Test
  public void testTokenProbArray() throws Exception {

    Sequence[] preds = chunker.topKSequences(toks1, tags1);

    assertTrue(preds.length > 0);
    assertEquals(expect1.length, preds[0].getProbs().length);
    assertEquals(Arrays.asList(expect1), preds[0].getOutcomes());
    assertNotSame(Arrays.asList(expect1), preds[1].getOutcomes());
  }

  @Test
  public void testTokenProbMinScore() throws Exception {

    Sequence[] preds = chunker.topKSequences(toks1, tags1, -5.55);

    assertTrue(preds.length == 4);
    assertEquals(expect1.length, preds[0].getProbs().length);
    assertEquals(Arrays.asList(expect1), preds[0].getOutcomes());
    assertNotSame(Arrays.asList(expect1), preds[1].getOutcomes());
  }

}
