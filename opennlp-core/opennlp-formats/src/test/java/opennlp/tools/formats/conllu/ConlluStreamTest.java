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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;

public class ConlluStreamTest extends AbstractConlluSampleStreamTest<SentenceSample> {

  @Test
  void testParseTwoSentences() throws IOException {
    try (ObjectStream<ConlluSentence> stream = getStream("de-ud-train-sample.conllu")) {
      ConlluSentence sent1 = stream.read();

      Assertions.assertEquals("train-s21", sent1.getSentenceIdComment());
      Assertions.assertEquals("Fachlich kompetent, sehr gute Beratung und ein freundliches Team.",
          sent1.getTextComment());
      Assertions.assertEquals(11, sent1.getWordLines().size());

      ConlluSentence sent2 = stream.read();

      Assertions.assertEquals("train-s22", sent2.getSentenceIdComment());
      Assertions.assertEquals(
          "Beiden Zahnärzten verdanke ich einen neuen Biss und dadurch endlich keine Rückenschmerzen mehr.",
          sent2.getTextComment());
      Assertions.assertEquals(14, sent2.getWordLines().size());

      Assertions.assertNull(stream.read(), "Stream must be exhausted");
    }
  }

  @Test
  void testOptionalComments() throws IOException {
    try (ObjectStream<ConlluSentence> stream = getStream("full-sample.conllu")) {
      ConlluSentence sent1 = stream.read();

      Assertions.assertEquals("1", sent1.getSentenceIdComment());
      Assertions.assertEquals("They buy and sell books.",
          sent1.getTextComment());
      Assertions.assertTrue(sent1.isNewDocument());
      Assertions.assertTrue(sent1.isNewParagraph());
      Assertions.assertEquals(6, sent1.getWordLines().size());

      ConlluSentence sent2 = stream.read();

      Assertions.assertEquals("2", sent2.getSentenceIdComment());
      Assertions.assertEquals(
          "I have no clue.",
          sent2.getTextComment());
      Assertions.assertTrue(sent2.isNewDocument());
      Assertions.assertEquals(5, sent2.getWordLines().size());

      ConlluSentence sent3 = stream.read();

      Assertions.assertEquals("panc0.s4", sent3.getSentenceIdComment());
      Assertions.assertEquals(Optional.of("tat yathānuśrūyate."), sent3.getTranslit());
      Assertions.assertEquals("तत् यथानुश्रूयते।", sent3.getTextComment());
      Assertions.assertEquals(3, sent3.getWordLines().size());
      Assertions.assertTrue(sent3.isNewParagraph());
      Map<Object, Object> textLang3 = new HashMap<>();
      textLang3.put(Locale.of("fr"), "Voilà ce qui nous est parvenu par la tradition orale.");
      textLang3.put(Locale.of("en"), "This is what is heard.");
      Assertions.assertEquals(Optional.of(textLang3)
          , sent3.getTextLang());

      ConlluSentence sent4 = stream.read();

      Assertions.assertEquals("mf920901-001-p1s1A", sent4.getSentenceIdComment());
      Assertions.assertEquals(
          "Slovenská ústava: pro i proti",
          sent4.getTextComment());
      Assertions.assertEquals(6, sent4.getWordLines().size());
      Assertions.assertTrue(sent4.isNewDocument());
      Assertions.assertTrue(sent4.isNewParagraph());
      Assertions.assertEquals(Optional.of("mf920901-001"), sent4.getDocumentId());
      Assertions.assertEquals(Optional.of("mf920901-001-p1"), sent4.getParagraphId());
      Assertions.assertEquals(Optional.of(Collections.singletonMap(Locale.of("en"),
              "Slovak constitution: pros and cons"))
          , sent4.getTextLang());

      Assertions.assertNull(stream.read(), "Stream must be exhausted");
    }
  }

  private static final String WORD_TAIL = "\t_\t_\t_\t0\troot\t_\t_\n";

  private static String word(String id, String form) {
    return id + "\t" + form + "\t" + form + WORD_TAIL;
  }

  private static String range(String id, String form) {
    return id + "\t" + form + "\t_\t_\t_\t_\t_\t_\t_\t_\n";
  }

  private static ConlluStream stream(String text) throws IOException {
    return new ConlluStream(
        () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }

  private static Stream<Arguments> rangesWithoutWordLines() {
    return Stream.of(
        // the end of the range has no line
        Arguments.of(range("1-2", "del") + word("1", "de"), "1-2", "2"),
        // a line in the middle of the range has none
        Arguments.of(range("1-3", "dello") + word("1", "de") + word("3", "lo"), "1-3", "2"),
        // the start of the range has none
        Arguments.of(word("1", "el") + range("2-3", "del") + word("3", "el"), "2-3", "2"),
        // the range is the only line
        Arguments.of(range("1-2", "del"), "1-2", "1"));
  }

  @ParameterizedTest
  @MethodSource("rangesWithoutWordLines")
  void testRangeWithoutWordLineIsReported(String sentence, String rangeId, String missingId)
      throws IOException {
    try (ConlluStream stream = stream("# text = x\n" + sentence + "\n")) {
      InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class, stream::read);
      Assertions.assertTrue(e.getMessage().contains(rangeId), e.getMessage());
      Assertions.assertTrue(e.getMessage().contains("id " + missingId), e.getMessage());
    }
  }

  @Test
  void testRangeWithAllWordLinesIsMerged() throws IOException {
    String sentence = range("1-2", "del") + word("1", "de") + word("2", "el") + word("3", "mar");
    try (ConlluStream stream = stream("# text = del mar\n" + sentence + "\n")) {
      ConlluSentence read = stream.read();
      Assertions.assertEquals(2, read.getWordLines().size());
      Assertions.assertEquals("1-2", read.getWordLines().get(0).getId());
      Assertions.assertEquals("3", read.getWordLines().get(1).getId());
      Assertions.assertNull(stream.read());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"1-2147483647", "2147483646-2147483647", "complete"})
  void testExtremeRangeHasBoundedResources(String scenario, @TempDir Path directory) throws Exception {
    Path output = directory.resolve("range-probe.log");
    Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-Xmx48m", "-cp", System.getProperty("java.class.path"),
        RangeProbe.class.getName(), scenario).redirectErrorStream(true)
        .redirectOutput(output.toFile()).start();
    try {
      Assertions.assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Range processing did not terminate");
      Assertions.assertEquals(0, child.exitValue(), Files.readString(output));
    } finally {
      child.destroyForcibly();
      child.waitFor();
    }
  }

  /** Runs extreme inputs in a bounded heap so a regression cannot exhaust the test runner. */
  public static final class RangeProbe {
    /** Verifies a complete maximum-endpoint range or a missing-word diagnostic. */
    public static void main(String[] args) throws Exception {
      boolean complete = args[0].equals("complete");
      String id = complete ? "2147483646-2147483647" : args[0];
      String input = range(id, "joined");
      if (complete) {
        input += word("2147483646", "a") + word("2147483647", "b");
      }
      try (ConlluStream stream = stream(input + "\n")) {
        if (complete) {
          ConlluSentence result = stream.read();
          Assertions.assertEquals(1, result.getWordLines().size());
          Assertions.assertEquals(id, result.getWordLines().get(0).getId());
        } else {
          InvalidFormatException error = Assertions.assertThrows(InvalidFormatException.class, stream::read);
          Assertions.assertTrue(error.getMessage().contains(id));
          Assertions.assertTrue(error.getMessage().contains("id " + id.substring(0, id.indexOf('-'))));
        }
      }
    }
  }

}
