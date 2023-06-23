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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

  @Test
  void testChunkSampleSerDe() throws IOException {
    ChunkSample chunkSample = createGoldSample();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    ObjectOutput out = new ObjectOutputStream(byteArrayOutputStream);
    out.writeObject(chunkSample);
    out.flush();
    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
    ObjectInput objectInput = new ObjectInputStream(byteArrayInputStream);

    ChunkSample deSerializedChunkSample = null;
    try {
      deSerializedChunkSample = (ChunkSample) objectInput.readObject();
    } catch (ClassNotFoundException e) {
      // do nothing
    }

    Assertions.assertNotNull(deSerializedChunkSample);
    Assertions.assertArrayEquals(chunkSample.getPhrasesAsSpanList(),
        deSerializedChunkSample.getPhrasesAsSpanList());
    Assertions.assertArrayEquals(chunkSample.getPreds(), deSerializedChunkSample.getPreds());
    Assertions.assertArrayEquals(chunkSample.getTags(), deSerializedChunkSample.getTags());
    Assertions.assertArrayEquals(chunkSample.getSentence(), deSerializedChunkSample.getSentence());
    Assertions.assertEquals(chunkSample, deSerializedChunkSample);
  }

  @Test
  void testParameterValidation() {
    Assertions.assertThrows(IllegalArgumentException.class, () ->
            new ChunkSample(new String[] {""}, new String[] {""},
                    new String[] {"test", "one element to much"}));
  }

  @Test
  void testRetrievingContent() {
    ChunkSample sample = new ChunkSample(createSentence(), createTags(), createChunks());

    Assertions.assertArrayEquals(createSentence(), sample.getSentence());
    Assertions.assertArrayEquals(createTags(), sample.getTags());
    Assertions.assertArrayEquals(createChunks(), sample.getPreds());
  }

  @Test
  void testToString() throws IOException {

    ChunkSample sample = new ChunkSample(createSentence(), createTags(), createChunks());
    String[] sentence = createSentence();
    String[] tags = createTags();
    String[] chunks = createChunks();

    StringReader sr = new StringReader(sample.toString());
    BufferedReader reader = new BufferedReader(sr);
    for (int i = 0; i < sentence.length; i++) {
      String line = reader.readLine();
      String[] parts = line.split("\\s+");
      Assertions.assertEquals(3, parts.length);
      Assertions.assertEquals(sentence[i], parts[0]);
      Assertions.assertEquals(tags[i], parts[1]);
      Assertions.assertEquals(chunks[i], parts[2]);
    }
  }

  @Test
  void testNicePrint() {

    ChunkSample sample = new ChunkSample(createSentence(), createTags(), createChunks());

    Assertions.assertEquals(" [NP Forecasts_NNS ] [PP for_IN ] [NP the_DT trade_NN figures_NNS ] "
        + "[VP range_VBP ] [ADVP widely_RB ] ,_, [NP Forecasts_NNS ] [PP for_IN ] "
        + "[NP the_DT trade_NN figures_NNS ] "
        + "[VP range_VBP ] [ADVP widely_RB ] ._.", sample.nicePrint());
  }

  @Test
  void testAsSpan() {
    ChunkSample sample = new ChunkSample(createSentence(), createTags(),
        createChunks());
    Span[] spans = sample.getPhrasesAsSpanList();

    Assertions.assertEquals(10, spans.length);
    Assertions.assertEquals(new Span(0, 1, "NP"), spans[0]);
    Assertions.assertEquals(new Span(1, 2, "PP"), spans[1]);
    Assertions.assertEquals(new Span(2, 5, "NP"), spans[2]);
    Assertions.assertEquals(new Span(5, 6, "VP"), spans[3]);
    Assertions.assertEquals(new Span(6, 7, "ADVP"), spans[4]);
    Assertions.assertEquals(new Span(8, 9, "NP"), spans[5]);
    Assertions.assertEquals(new Span(9, 10, "PP"), spans[6]);
    Assertions.assertEquals(new Span(10, 13, "NP"), spans[7]);
    Assertions.assertEquals(new Span(13, 14, "VP"), spans[8]);
    Assertions.assertEquals(new Span(14, 15, "ADVP"), spans[9]);
  }


  // following are some tests to check the argument validation. Since all uses
  // the same validateArguments method, we do a deeper test only once

  @Test
  void testPhraseAsSpan() {
    Span[] spans = ChunkSample.phrasesAsSpanList(createSentence(),
        createTags(), createChunks());

    Assertions.assertEquals(10, spans.length);
    Assertions.assertEquals(new Span(0, 1, "NP"), spans[0]);
    Assertions.assertEquals(new Span(1, 2, "PP"), spans[1]);
    Assertions.assertEquals(new Span(2, 5, "NP"), spans[2]);
    Assertions.assertEquals(new Span(5, 6, "VP"), spans[3]);
    Assertions.assertEquals(new Span(6, 7, "ADVP"), spans[4]);
    Assertions.assertEquals(new Span(8, 9, "NP"), spans[5]);
    Assertions.assertEquals(new Span(9, 10, "PP"), spans[6]);
    Assertions.assertEquals(new Span(10, 13, "NP"), spans[7]);
    Assertions.assertEquals(new Span(13, 14, "VP"), spans[8]);
    Assertions.assertEquals(new Span(14, 15, "ADVP"), spans[9]);
  }

  @Test
  void testRegions() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/chunker/output.txt");

    DummyChunkSampleStream predictedSample = new DummyChunkSampleStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8), false);

    ChunkSample cs1 = predictedSample.read();
    String[] g1 = Span.spansToStrings(cs1.getPhrasesAsSpanList(), cs1.getSentence());
    Assertions.assertEquals(15, g1.length);

    ChunkSample cs2 = predictedSample.read();
    String[] g2 = Span.spansToStrings(cs2.getPhrasesAsSpanList(), cs2.getSentence());
    Assertions.assertEquals(10, g2.length);

    ChunkSample cs3 = predictedSample.read();
    String[] g3 = Span.spansToStrings(cs3.getPhrasesAsSpanList(), cs3.getSentence());
    Assertions.assertEquals(7, g3.length);
    Assertions.assertEquals("United", g3[0]);
    Assertions.assertEquals("'s directors", g3[1]);
    Assertions.assertEquals("voted", g3[2]);
    Assertions.assertEquals("themselves", g3[3]);
    Assertions.assertEquals("their spouses", g3[4]);
    Assertions.assertEquals("lifetime access", g3[5]);
    Assertions.assertEquals("to", g3[6]);

    predictedSample.close();

  }

  @Test
  void testInvalidPhraseAsSpan1() {
    Assertions.assertThrows(IllegalArgumentException.class, () ->
            ChunkSample.phrasesAsSpanList(new String[2], new String[1], new String[1]));
  }

  @Test
  void testInvalidPhraseAsSpan2() {
    Assertions.assertThrows(IllegalArgumentException.class, () ->
            ChunkSample.phrasesAsSpanList(new String[1], new String[2], new String[1]));
  }

  @Test
  void testInvalidPhraseAsSpan3() {
    Assertions.assertThrows(IllegalArgumentException.class, () ->
            ChunkSample.phrasesAsSpanList(new String[1], new String[1], new String[2]));
  }

  @Test
  void testInvalidChunkSampleArray() {
    Assertions.assertThrows(IllegalArgumentException.class, () ->
            new ChunkSample(new String[1], new String[1], new String[2]));
  }

  @Test
  void testInvalidChunkSampleList() {
    Assertions.assertThrows(IllegalArgumentException.class, () ->
            new ChunkSample(Arrays.asList(new String[1]), Arrays.asList(new String[1]),
                    Arrays.asList(new String[2])));
  }

  @Test
  void testEquals() {
    Assertions.assertNotSame(createGoldSample(), createGoldSample());
    Assertions.assertEquals(createGoldSample(), createGoldSample());
    Assertions.assertNotEquals(createPredSample(), createGoldSample());
    Assertions.assertNotEquals(createPredSample(), new Object());
  }

}
