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

package opennlp.spellcheck.cmdline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.AbstractTempDirTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test: build a model from the tiny fixtures with
 * {@link SpellCheckModelBuilderTool} and correct a line with {@link CorrectTextTool}.
 */
public class SpellCheckCmdLineToolsTest extends AbstractTempDirTest {

  private static final String UNIGRAMS = "/opennlp/spellcheck/frequency_dictionary_tiny.txt";
  private static final String BIGRAMS = "/opennlp/spellcheck/frequency_bigramdictionary_tiny.txt";

  private static Path copyResource(String resource, Path dir, String name) throws IOException {
    final Path target = dir.resolve(name);
    try (InputStream in = SpellCheckCmdLineToolsTest.class.getResourceAsStream(resource)) {
      assertNotNull(in, "missing test fixture: " + resource);
      Files.copy(in, target);
    }
    return target;
  }

  @Test
  void buildModelAndCorrectLineEndToEnd() throws IOException {
    final Path unigrams = copyResource(UNIGRAMS, tempDir, "unigrams.txt");
    final Path bigrams = copyResource(BIGRAMS, tempDir, "bigrams.txt");
    final Path model = tempDir.resolve("en-spellcheck.bin");

    // 1. Build the model.
    new SpellCheckModelBuilderTool().run(new String[] {
        "-lang", "en",
        "-unigrams", unigrams.toString(),
        "-bigrams", bigrams.toString(),
        "-model", model.toString()
    });

    assertTrue(Files.exists(model), "model file was not created");
    assertTrue(Files.size(model) > 0, "model file is empty");

    // 2. Per-token correction.
    final Path input = tempDir.resolve("input.txt");
    Files.writeString(input, "quikc broen wrold\n", StandardCharsets.UTF_8);
    final Path output = tempDir.resolve("output.txt");

    new CorrectTextTool().run(new String[] {
        "-model", model.toString(),
        "-inputFile", input.toString(),
        "-outputFile", output.toString()
    });

    final List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
    assertEquals(1, lines.size());
    assertEquals("quick brown world", lines.get(0));
  }

  @Test
  void compoundModeRepairsRunOnWords() throws IOException {
    final Path unigrams = copyResource(UNIGRAMS, tempDir, "unigrams.txt");
    final Path bigrams = copyResource(BIGRAMS, tempDir, "bigrams.txt");
    final Path model = tempDir.resolve("en-spellcheck.bin");

    new SpellCheckModelBuilderTool().run(new String[] {
        "-lang", "en",
        "-unigrams", unigrams.toString(),
        "-bigrams", bigrams.toString(),
        "-model", model.toString()
    });

    final Path input = tempDir.resolve("input.txt");
    Files.writeString(input, "helloworld\n", StandardCharsets.UTF_8);
    final Path output = tempDir.resolve("output.txt");

    new CorrectTextTool().run(new String[] {
        "-model", model.toString(),
        "-compound", "true",
        "-inputFile", input.toString(),
        "-outputFile", output.toString()
    });

    final List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
    assertEquals(1, lines.size());
    assertEquals("hello world", lines.get(0));
  }

  @Test
  void suggestModeListsCandidatesPerToken() throws IOException {
    final Path unigrams = copyResource(UNIGRAMS, tempDir, "unigrams.txt");
    final Path bigrams = copyResource(BIGRAMS, tempDir, "bigrams.txt");
    final Path model = tempDir.resolve("en-spellcheck.bin");

    new SpellCheckModelBuilderTool().run(new String[] {
        "-lang", "en",
        "-unigrams", unigrams.toString(),
        "-bigrams", bigrams.toString(),
        "-model", model.toString()
    });

    final Path input = tempDir.resolve("input.txt");
    Files.writeString(input, "teh\n", StandardCharsets.UTF_8);
    final Path output = tempDir.resolve("output.txt");

    new CorrectTextTool().run(new String[] {
        "-model", model.toString(),
        "-suggest", "true",
        "-verbosity", "TOP",
        "-inputFile", input.toString(),
        "-outputFile", output.toString()
    });

    final List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
    assertEquals(1, lines.size());
    // Suggestions are listed (not corrected text) as: token => [s1, s2, ...]
    assertEquals("teh => [the]", lines.get(0));
  }

  @Test
  void suggestModeTreatsNoBreakSpaceJoinedPairAsOneToken() throws IOException {
    final Path unigrams = copyResource(UNIGRAMS, tempDir, "unigrams.txt");
    final Path bigrams = copyResource(BIGRAMS, tempDir, "bigrams.txt");
    final Path model = tempDir.resolve("en-spellcheck.bin");

    new SpellCheckModelBuilderTool().run(new String[] {
        "-lang", "en",
        "-unigrams", unigrams.toString(),
        "-bigrams", bigrams.toString(),
        "-model", model.toString()
    });

    final Path input = tempDir.resolve("input.txt");
    final String nbsp = new String(Character.toChars(0x00A0));
    Files.writeString(input, "teh" + nbsp + "teh\n", StandardCharsets.UTF_8);
    final Path output = tempDir.resolve("output.txt");

    new CorrectTextTool().run(new String[] {
        "-model", model.toString(),
        "-suggest", "true",
        "-verbosity", "TOP",
        "-inputFile", input.toString(),
        "-outputFile", output.toString()
    });

    final List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
    // Characterization: tokens come from the regex \s+ (ASCII whitespace only), so the
    // NBSP-joined pair is a single token, and no suggestion is within edit distance reach.
    assertEquals(1, lines.size());
    assertEquals("teh" + nbsp + "teh => []", lines.get(0));
  }
}
