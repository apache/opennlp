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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InvalidFormatException;

import static opennlp.tools.formats.conllu.ConlluTestLines.line;
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

  /** An in-memory factory over the shared fixture. */
  private static InputStreamFactory factory() {
    return () -> new ByteArrayInputStream(CONLLU.getBytes(StandardCharsets.UTF_8));
  }

  /** A stream over the shared fixture using the universal tagset. */
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
      assertEquals(1, first.getTree().headOf(0));
      assertEquals(DependencyArc.ROOT_HEAD, first.getTree().headOf(1));
      assertEquals(3, first.getTree().headOf(2));
      assertEquals("obj", first.getTree().relationOf(3));

      // the underscore-head sentence is skipped; the contraction sentence is kept,
      // with the range line dropped and its syntactic words intact
      final DependencySample second = samples.read();
      assertNotNull(second);
      assertArrayEquals(new String[] {"in", "Haus"}, second.getTokens());
      assertEquals(1, second.getTree().headOf(0));
      assertEquals("case", second.getTree().relationOf(0));

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
  void testMalformedLineIsRejected() {
    final InputStreamFactory bad = () -> new ByteArrayInputStream(
        "1\ttoo\tfew\tcolumns\n".getBytes(StandardCharsets.UTF_8));
    assertThrows(InvalidFormatException.class,
        () -> new ConlluDependencySampleStream(bad, ConlluTagset.U).read());
  }

  @Test
  void testExtraColumnIsRejected() {
    final InputStreamFactory bad = () -> new ByteArrayInputStream(
        (line("1", "word", "word", "NOUN", "NN", "_", "0", "root", "_", "_",
            "extra") + "\n").getBytes(StandardCharsets.UTF_8));
    assertThrows(InvalidFormatException.class,
        () -> new ConlluDependencySampleStream(bad, ConlluTagset.U).read());
  }

  @Test
  void testUtf8BomIsAccepted() throws IOException {
    final String content = "\ufeff" + line("1", "Word", "word", "NOUN", "NN", "_",
        "0", "root", "_", "_") + "\n";
    final InputStreamFactory in = () -> new ByteArrayInputStream(
        content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      final DependencySample sample = samples.read();
      assertNotNull(sample);
      assertArrayEquals(new String[] {"Word"}, sample.getTokens());
    }
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
      assertEquals(DependencyArc.ROOT_HEAD, onlyValid.getTree().headOf(0));
      assertNull(samples.read());
    }
  }

  @ParameterizedTest(name = "id = \"{0}\"")
  @ValueSource(strings = {"2", "01", "+1", "-1", "1 ", " 1", "\u0661", "\uff11", "\uD835\uDFCF", "",
      "0", "1-2", "1.1", "2147483647", "2147483648", "4294967297", "9999999999999999999"})
  void testWordIdsOtherThanThePositionAreSkipped(String id) throws IOException {
    final String content = line(id, "Dogs", "dog", "NOUN", "NNS", "_", "0",
        "root", "_", "_") + "\n";
    final InputStreamFactory in = () -> new ByteArrayInputStream(
        content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      assertNull(samples.read());
    }
  }

  /**
   * Word ID sequences that do not count up from one: a gap, a swap, a repeat, and a
   * one-based sequence that starts at two.
   *
   * @return The ID columns of a three-word sentence.
   */
  static Stream<Arguments> misnumberedSentences() {
    return Stream.of(
        Arguments.of("gap", new String[] {"1", "3", "4"}),
        Arguments.of("swap", new String[] {"2", "1", "3"}),
        Arguments.of("repeat", new String[] {"1", "1", "2"}),
        Arguments.of("starts at two", new String[] {"2", "3", "4"}),
        Arguments.of("descending", new String[] {"3", "2", "1"}));
  }

  /** A sentence whose IDs do not count up from one is skipped and the next sentence is read. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("misnumberedSentences")
  void testMisnumberedSentenceIsSkipped(String description, String[] ids) throws IOException {
    final String content = String.join("\n",
        line(ids[0], "Dogs", "dog", "NOUN", "NNS", "_", "2", "nsubj", "_", "_"),
        line(ids[1], "bark", "bark", "VERB", "VBP", "_", "0", "root", "_", "_"),
        line(ids[2], "loudly", "loudly", "ADV", "RB", "_", "2", "advmod", "_", "_"),
        "",
        line("1", "Fine", "fine", "ADJ", "JJ", "_", "0", "root", "_", "_"),
        "") + "\n";
    final InputStreamFactory in = () -> new ByteArrayInputStream(
        content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      final DependencySample sample = samples.read();
      assertNotNull(sample);
      assertArrayEquals(new String[] {"Fine"}, sample.getTokens());
      assertNull(samples.read());
    }
  }

  /**
   * A head that is not a plain decimal like the ID column is skipped: signed, with a
   * leading zero, with a non-ASCII digit, padded, empty, or fractional.
   *
   * @param head The HEAD column of the second word, whose plain form is {@code 1}.
   * @throws IOException Thrown if reading fails.
   */
  @ParameterizedTest(name = "head = \"{0}\"")
  @ValueSource(strings = {"+1", "01", "\u0661", "\uff11", "-1", " 1", "1 ", "", "1.0", "1e0", "_"})
  void testHeadsOtherThanPlainDecimalsAreSkipped(String head) throws IOException {
    final String content = String.join("\n",
        line("1", "Dogs", "dog", "NOUN", "NNS", "_", "0", "root", "_", "_"),
        line("2", "bark", "bark", "VERB", "VBP", "_", head, "dep", "_", "_"),
        "",
        line("1", "Fine", "fine", "ADJ", "JJ", "_", "0", "root", "_", "_"),
        "") + "\n";
    final InputStreamFactory in = () -> new ByteArrayInputStream(
        content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      final DependencySample sample = samples.read();
      assertNotNull(sample);
      assertArrayEquals(new String[] {"Fine"}, sample.getTokens());
      assertNull(samples.read());
    }
  }

  /** The root head {@code 0} and a two-digit head are read as written. */
  @Test
  void testRootAndTwoDigitHeadsAreRead() throws IOException {
    final StringBuilder content = new StringBuilder();
    for (int i = 1; i <= 10; i++) {
      content.append(line(Integer.toString(i), "w" + i, "w" + i, "NOUN", "NN", "_",
          i == 10 ? "0" : "10", i == 10 ? "root" : "nmod", "_", "_")).append('\n');
    }
    final InputStreamFactory in = () -> new ByteArrayInputStream(
        content.toString().getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      final DependencySample sample = samples.read();
      assertNotNull(sample);
      assertEquals(DependencyArc.ROOT_HEAD, sample.getTree().headOf(9));
      assertEquals(9, sample.getTree().headOf(0));
      assertNull(samples.read());
    }
  }

  /** A sentence made only of range and empty-node lines has no words and is passed over. */
  @Test
  void testSentenceWithoutWordLinesIsPassedOver() throws IOException {
    final String content = String.join("\n",
        line("1-2", "im", "_", "_", "_", "_", "_", "_", "_", "_"),
        line("1.1", "gap", "gap", "NOUN", "NN", "_", "_", "_", "_", "_"),
        "",
        line("1", "Fine", "fine", "ADJ", "JJ", "_", "0", "root", "_", "_"),
        "") + "\n";
    final InputStreamFactory in = () -> new ByteArrayInputStream(
        content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      assertArrayEquals(new String[] {"Fine"}, samples.read().getTokens());
      assertNull(samples.read());
    }
  }

  /**
   * Reads a sentence whose tenth word has the two-digit ID {@code 10}, so the position
   * comparison covers more than one digit.
   *
   * @throws IOException Thrown if reading fails.
   */
  @Test
  void testTwoDigitWordIdIsRead() throws IOException {
    final StringBuilder content = new StringBuilder();
    for (int i = 1; i <= 10; i++) {
      content.append(line(Integer.toString(i), "w" + i, "w" + i, "NOUN", "NN", "_",
          i == 1 ? "0" : "1", i == 1 ? "root" : "nmod", "_", "_")).append('\n');
    }
    final InputStreamFactory in = () -> new ByteArrayInputStream(
        content.toString().getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      final DependencySample sample = samples.read();
      assertNotNull(sample);
      assertEquals(10, sample.getTokens().length);
      assertEquals("w10", sample.getTokens()[9]);
      assertNull(samples.read());
    }
  }

  /**
   * Verifies that a word whose relation is the underscore placeholder makes the
   * sentence incomplete: it is skipped like an underscore head, and reading continues
   * with the next sentence.
   *
   * @throws IOException Thrown if reading fails.
   */
  @Test
  void testUnderscoreRelationIsSkipped() throws IOException {
    final String content = String.join("\n",
        line("1", "Dogs", "dog", "NOUN", "NNS", "_", "2", "_", "_", "_"),
        line("2", "bark", "bark", "VERB", "VBP", "_", "0", "root", "_", "_"),
        "",
        line("1", "Cats", "cat", "NOUN", "NNS", "_", "2", "nsubj", "_", "_"),
        line("2", "purr", "purr", "VERB", "VBP", "_", "0", "root", "_", "_"),
        "") + "\n";
    final InputStreamFactory in = () -> new ByteArrayInputStream(
        content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      final DependencySample sample = samples.read();
      assertNotNull(sample);
      assertArrayEquals(new String[] {"Cats", "purr"}, sample.getTokens());
      assertEquals("nsubj", sample.getTree().relationOf(0));
      assertNull(samples.read());
    }
  }

  @ParameterizedTest
  @EnumSource(ConlluTagset.class)
  void testMissingSelectedPosTagIsSkipped(ConlluTagset tagset) throws IOException {
    final String content = String.join("\n",
        line("1", "Missing", "missing", tagset == ConlluTagset.U ? "_" : "NOUN",
            tagset == ConlluTagset.U ? "NN" : "_", "_", "0", "root", "_", "_"),
        "",
        line("1", "Valid", "valid", "NOUN", "NN", "_", "0", "root", "_", "_"), "");
    final InputStreamFactory input = () ->
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples = new ConlluDependencySampleStream(input, tagset)) {
      assertArrayEquals(new String[] {"Valid"}, samples.read().getTokens());
      assertNull(samples.read());
    }
  }

  @Test
  void testEmptyNodeLinesAreDropped() throws IOException {
    // An empty node (id 1.1) only carries enhanced dependencies; the basic tree is
    // formed by the surrounding word lines.
    final String content = String.join("\n",
        line("1", "Dogs", "dog", "NOUN", "NNS", "_", "2", "nsubj", "_", "_"),
        line("1.1", "bark", "bark", "VERB", "VBP", "_", "_", "_", "2:conj", "CopyOf=2"),
        line("2", "bark", "bark", "VERB", "VBP", "_", "0", "root", "_", "_"),
        "") + "\n";
    final InputStreamFactory in = () -> new ByteArrayInputStream(
        content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      final DependencySample sample = samples.read();
      assertNotNull(sample);
      assertArrayEquals(new String[] {"Dogs", "bark"}, sample.getTokens());
      assertEquals(1, sample.getTree().headOf(0));
      assertEquals(DependencyArc.ROOT_HEAD, sample.getTree().headOf(1));
      assertNull(samples.read());
    }
  }

  @Test
  void testMalformedUtf8Throws() throws IOException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bytes.writeBytes("1\t".getBytes(StandardCharsets.UTF_8));
    bytes.write(0xc3);
    bytes.writeBytes("\t_\tNOUN\tNNS\t_\t0\troot\t_\t_\n"
        .getBytes(StandardCharsets.UTF_8));
    final InputStreamFactory in = () -> new ByteArrayInputStream(bytes.toByteArray());
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      assertThrows(IOException.class, samples::read);
    }
  }

  @Test
  void testSeparatorLineOfNonBreakingSpaceSeparatesSentences() throws IOException {
    // A separator line carrying a stray no-break space is still a separator: OpenNLP
    // counts U+00A0 as whitespace, so such a line must not reach the word-line parser
    // and abort the stream.
    final String content = String.join("\n",
        line("1", "Dogs", "dog", "NOUN", "NNS", "_", "2", "nsubj", "_", "_"),
        line("2", "bark", "bark", "VERB", "VBP", "_", "0", "root", "_", "_"),
        "\u00A0",
        line("1", "Fine", "fine", "ADJ", "JJ", "_", "0", "root", "_", "_"),
        "") + "\n";
    final InputStreamFactory in =
        () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    try (ConlluDependencySampleStream samples =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      final DependencySample first = samples.read();
      assertNotNull(first);
      assertArrayEquals(new String[] {"Dogs", "bark"}, first.getTokens());
      final DependencySample second = samples.read();
      assertNotNull(second);
      assertArrayEquals(new String[] {"Fine"}, second.getTokens());
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
