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

package opennlp.tools.formats.ad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.PlainTextByLineStream;

public class ADChunkSampleStreamTest extends AbstractADSampleStreamTest<ChunkSample> {

  @BeforeEach
  void setup() throws IOException {
    super.setup();
    try (ADChunkSampleStream stream = new ADChunkSampleStream(
            new PlainTextByLineStream(in, StandardCharsets.UTF_8))) {
      ChunkSample sample;
      while ((sample = stream.read()) != null) {
        samples.add(sample);
      }
      Assertions.assertFalse(samples.isEmpty());
    }
  }
  
  @Test
  void testSimpleCount() {
    Assertions.assertEquals(NUM_SENTENCES, samples.size());
  }

  @Test
  void testChunks() {

    Assertions.assertEquals("Inicia", samples.get(0).getSentence()[0]);
    Assertions.assertEquals("v-fin", samples.get(0).getTags()[0]);
    Assertions.assertEquals("B-VP", samples.get(0).getPreds()[0]);

    Assertions.assertEquals("em", samples.get(0).getSentence()[1]);
    Assertions.assertEquals("prp", samples.get(0).getTags()[1]);
    Assertions.assertEquals("B-PP", samples.get(0).getPreds()[1]);

    Assertions.assertEquals("o", samples.get(0).getSentence()[2]);
    Assertions.assertEquals("art", samples.get(0).getTags()[2]);
    Assertions.assertEquals("B-NP", samples.get(0).getPreds()[2]);

    Assertions.assertEquals("próximo", samples.get(0).getSentence()[3]);
    Assertions.assertEquals("adj", samples.get(0).getTags()[3]);
    Assertions.assertEquals("I-NP", samples.get(0).getPreds()[3]);

    Assertions.assertEquals("Casas", samples.get(3).getSentence()[0]);
    Assertions.assertEquals("n", samples.get(3).getTags()[0]);
    Assertions.assertEquals("B-NP", samples.get(3).getPreds()[0]);
  }

  private static ChunkSample readOne(String... treeLines) throws IOException {
    String[] lines = new String[treeLines.length + 5];
    lines[0] = "<s id=\"1\">";
    lines[1] = "SOURCE: ref=\"CF2021-7\" source=\"CETENFolha\"";
    lines[2] = "CF2021-7 (011) 212-2241 e 818-5817.";
    lines[3] = "A1";
    lines[4] = "STA:fcl";
    System.arraycopy(treeLines, 0, lines, 5, treeLines.length);
    try (ADChunkSampleStream stream =
             new ADChunkSampleStream(ObjectStreamUtils.createObjectStream(lines))) {
      ChunkSample sample = stream.read();
      Assertions.assertNull(stream.read());
      return sample;
    }
  }

  /**
   * A leaf with an equals sign and a colon in its tag, as in FlorestaVirgem, keeps the
   * functional tag after the colon as its part of speech; no tag is null.
   */
  @Test
  void testLeafWithEqualsSignInTagHasItsFunctionalTag() throws IOException {
    ChunkSample sample = readOne(
        "=CO:conj-c(\"e\" <co-subj>)\te",
        "=H==CJT:num(\"818-5817\" <cjt-X> <card> <NER:virtual> M/F P)\t818-5817",
        "=.",
        "</s>");
    Assertions.assertEquals(List.of("e", "818-5817", "."), Arrays.asList(sample.getSentence()));
    Assertions.assertEquals(List.of("conj-c", "num", "."), Arrays.asList(sample.getTags()));
    Assertions.assertEquals(List.of("O", "O", "O"), Arrays.asList(sample.getPreds()));
  }

  /** A leaf without a functional tag is tagged with its lexeme, as the POS stream does. */
  @Test
  void testLeafWithoutFunctionalTagIsTaggedWithItsLexeme() throws IOException {
    ChunkSample sample = readOne(
        "=CO:conj-c(\"e\" <co-subj>)\te",
        "=a=b(\"x\" M S)\tx",
        "=.",
        "</s>");
    Assertions.assertEquals(List.of("e", "x", "."), Arrays.asList(sample.getSentence()));
    Assertions.assertEquals(List.of("conj-c", "x", "."), Arrays.asList(sample.getTags()));
    Assertions.assertFalse(Arrays.asList(sample.getPreds()).contains(null));
  }

  /**
   * Bosque has 2 node lines with tags joined by a stray separator,
   * {@code =====P.vp} and {@code ========N<ARGOpp}. They are nodes, not leaves, and add no
   * token. The old reader dropped their first character and put {@code .vp} and
   * {@code <ARGOpp} into the sentence as words of an NP chunk.
   */
  @Test
  void testNodeLineWithStraySeparatorAddsNoToken() throws IOException {
    ChunkSample sample = readOne(
        "=SUBJ:np",
        "==H:pron-indp(\"que\" <rel> M S)\tque",
        "=P.vp",
        "==AUX:v-fin(\"ter\" COND 3S)\tteria",
        "=N<ARGOpp",
        "==H:prp(\"de\")\tde",
        "=.",
        "</s>");
    Assertions.assertEquals(List.of("que", "teria", "de", "."),
        Arrays.asList(sample.getSentence()));
    Assertions.assertEquals(List.of("pron-indp", "v-fin", "prp", "."),
        Arrays.asList(sample.getTags()));
  }
}
