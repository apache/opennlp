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

package opennlp.tools.formats.conllu;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.InputStreamFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests that the raw reader maps the basic dependency columns, keeps the syntactic
 * words of multiword tokens while dropping the range line itself, and skips sentences
 * without a usable annotation.
 */
public class ConlluDependencySampleStreamTest {

  private static String line(String... fields) {
    return String.join("\t", fields);
  }

  private static final String CONLLU = String.join("\n",
      "# sent_id = test-1",
      "# text = He bought the bonds",
      line("1", "He", "he", "PRON", "PRP", "_", "2", "nsubj", "_", "_"),
      line("2", "bought", "buy", "VERB", "VBD", "_", "0", "root", "_", "_"),
      line("3", "the", "the", "DET", "DT", "_", "4", "det", "_", "_"),
      line("4", "bonds", "bond", "NOUN", "NNS", "_", "2", "obj", "_", "_"),
      "",
      "# sent_id = test-2",
      "# text = Broken",
      line("1", "Broken", "broken", "ADJ", "JJ", "_", "_", "_", "_", "_"),
      "",
      "# sent_id = test-3",
      "# text = im Haus",
      line("1-2", "im", "_", "_", "_", "_", "_", "_", "_", "_"),
      line("1", "in", "in", "ADP", "APPR", "_", "2", "case", "_", "_"),
      line("2", "Haus", "Haus", "NOUN", "NN", "_", "0", "root", "_", "_"),
      "",
      "# sent_id = test-4",
      "# text = Dogs bark",
      line("1", "Dogs", "dog", "NOUN", "NNS", "_", "2", "nsubj", "_", "_"),
      line("2", "bark", "bark", "VERB", "VBP", "_", "0", "root", "_", "_"),
      "") + "\n";

  private static InputStreamFactory factory() {
    return () -> new ByteArrayInputStream(CONLLU.getBytes(StandardCharsets.UTF_8));
  }

  private static ConlluDependencySampleStream stream() throws IOException {
    return new ConlluDependencySampleStream(factory(), ConlluTagset.U);
  }

  @Test
  void testReadsSamplesKeepsContractionsAndSkipsUnusableSentences() throws IOException {
    try (ConlluDependencySampleStream samples = stream()) {
      final DependencySample first = samples.read();
      assertNotNull(first);
      assertArrayEquals(new String[] {"He", "bought", "the", "bonds"}, first.getTokens());
      assertArrayEquals(new String[] {"PRON", "VERB", "DET", "NOUN"}, first.getTags());
      assertEquals(1, first.getGraph().headOf(0));
      assertEquals(DependencyArc.ROOT_HEAD, first.getGraph().headOf(1));
      assertEquals(3, first.getGraph().headOf(2));
      assertEquals("obj", first.getGraph().relationOf(3));

      // the underscore-head sentence is skipped; the contraction sentence is KEPT,
      // with the range line dropped and its syntactic words intact
      final DependencySample second = samples.read();
      assertNotNull(second);
      assertArrayEquals(new String[] {"in", "Haus"}, second.getTokens());
      assertEquals(1, second.getGraph().headOf(0));
      assertEquals("case", second.getGraph().relationOf(0));

      final DependencySample third = samples.read();
      assertNotNull(third);
      assertArrayEquals(new String[] {"Dogs", "bark"}, third.getTokens());

      assertNull(samples.read());
    }
  }

  @Test
  void testResetRestartsTheStream() throws IOException {
    try (ConlluDependencySampleStream samples = stream()) {
      assertNotNull(samples.read());
      samples.reset();
      final DependencySample first = samples.read();
      assertNotNull(first);
      assertArrayEquals(new String[] {"He", "bought", "the", "bonds"}, first.getTokens());
    }
  }

  @Test
  void testXposTagsetSelectsTheOtherColumn() throws IOException {
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(factory(), ConlluTagset.X)) {
      assertArrayEquals(new String[] {"PRP", "VBD", "DT", "NNS"},
          samples.read().getTags());
    }
  }

  @Test
  void testMalformedLineFailsLoud() {
    final InputStreamFactory bad = () -> new ByteArrayInputStream(
        "1\ttoo\tfew\tcolumns\n".getBytes(StandardCharsets.UTF_8));
    assertThrows(IOException.class,
        () -> new ConlluDependencySampleStream(bad, ConlluTagset.U).read());
  }

  @Test
  void testSemanticallyInvalidAnnotationIsSkippedNotFatal() throws IOException {
    // Structurally well-formed lines whose annotation cannot form a valid tree, here an
    // out-of-range head and a rootless cycle, skip the sentence instead of failing, so
    // one broken sentence cannot abort reading a large treebank.
    final String content = String.join("\n",
        line("1", "far", "far", "ADV", "RB", "_", "5", "advmod", "_", "_"),
        line("2", "off", "off", "ADP", "RP", "_", "0", "root", "_", "_"),
        "",
        line("1", "loop", "loop", "NOUN", "NN", "_", "2", "dep", "_", "_"),
        line("2", "back", "back", "ADV", "RB", "_", "1", "dep", "_", "_"),
        "",
        line("1", "Fine", "fine", "ADJ", "JJ", "_", "0", "root", "_", "_"),
        "") + "\n";
    final InputStreamFactory in =
        () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      final DependencySample onlyValid = samples.read();
      assertNotNull(onlyValid);
      assertArrayEquals(new String[] {"Fine"}, onlyValid.getTokens());
      assertEquals(DependencyArc.ROOT_HEAD, onlyValid.getGraph().headOf(0));
      assertNull(samples.read());
    }
  }

  @Test
  void testEmptyContentYieldsNoSample() throws IOException {
    final InputStreamFactory in = () -> new ByteArrayInputStream(new byte[0]);
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      assertNull(samples.read());
    }
  }

  @Test
  void testValidation() {
    assertThrows(IllegalArgumentException.class,
        () -> new ConlluDependencySampleStream(null, ConlluTagset.U));
    assertThrows(IllegalArgumentException.class,
        () -> new ConlluDependencySampleStream(factory(), null));
  }
}
