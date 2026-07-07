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

package opennlp.tools.cmdline.stopword;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.cmdline.TerminateToolException;

public class StopwordFilterToolTest {

  private InputStream originalIn;
  private PrintStream originalOut;
  private ByteArrayOutputStream capturedOut;

  @BeforeEach
  void redirectStreams() {
    originalIn = System.in;
    originalOut = System.out;
    capturedOut = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStreams() {
    System.setIn(originalIn);
    System.setOut(originalOut);
  }

  @Test
  void filtersEnglishStopwordsFromStdin() {
    final String in = "the quick brown fox\n";
    System.setIn(new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8)));

    StopwordFilterTool tool = new StopwordFilterTool();
    tool.run(new String[] {"en"});

    String out = capturedOut.toString(StandardCharsets.UTF_8);
    int idxQuick = out.indexOf("quick");
    int idxBrown = out.indexOf("brown");
    int idxFox = out.indexOf("fox");

    Assertions.assertTrue(idxQuick >= 0, "Expected 'quick' in output, was: " + out);
    Assertions.assertTrue(idxBrown > idxQuick, "Expected 'brown' after 'quick' in output, was: " + out);
    Assertions.assertTrue(idxFox > idxBrown, "Expected 'fox' after 'brown' in output, was: " + out);
    Assertions.assertFalse(out.contains("the"),
        "Did not expect 'the' to appear in output, was: " + out);
  }

  @Test
  void printsHelpWhenNoArgs() {
    // empty stdin in case run() reads it
    System.setIn(new ByteArrayInputStream(new byte[0]));

    StopwordFilterTool tool = new StopwordFilterTool();
    tool.run(new String[] {});

    String out = capturedOut.toString(StandardCharsets.UTF_8);
    Assertions.assertTrue(out.contains("Usage") || out.contains("lang"),
        "Expected help message containing 'Usage' or 'lang', was: " + out);
  }

  @Test
  void unknownLanguageOrFileThrows() {
    System.setIn(new ByteArrayInputStream(new byte[0]));

    // "xx" is neither a supported bundled code nor an existing file, so the
    // tool reports a terminate error rather than silently doing nothing.
    StopwordFilterTool tool = new StopwordFilterTool();
    Assertions.assertThrows(TerminateToolException.class,
        () -> tool.run(new String[] {"xx"}));
  }

  @Test
  void filtersUsingCustomListFile(@TempDir Path tmp) throws IOException {
    final Path list = tmp.resolve("custom-stopwords.txt");
    Files.write(list, List.of("# custom list", "brown", "fox"), StandardCharsets.UTF_8);

    System.setIn(new ByteArrayInputStream(
        "the quick brown fox".getBytes(StandardCharsets.UTF_8)));

    StopwordFilterTool tool = new StopwordFilterTool();
    tool.run(new String[] {list.toString()});

    final String out = capturedOut.toString(StandardCharsets.UTF_8).trim();
    // The custom list drops "brown" and "fox"; "the"/"quick" are kept since
    // they are not in this list.
    Assertions.assertEquals("the quick", out);
  }

  @Test
  void noBreakSpaceDoesNotSplitTokens() {
    // Characterization: the tool splits input lines with the regex \s+, which matches only
    // ASCII whitespace. An NBSP-joined pair is one token, so the stopword inside it is not
    // filtered.
    Assertions.assertEquals("the" + cp(0x00A0) + "fox",
        filterEnglish("the" + cp(0x00A0) + "fox the\n"));
  }

  @Test
  void nextLineControlDoesNotSplitTokens() {
    // Characterization: U+0085 NEL is not ASCII whitespace, so it does not split tokens.
    Assertions.assertEquals("the" + cp(0x0085) + "fox",
        filterEnglish("the" + cp(0x0085) + "fox the\n"));
  }

  @Test
  void informationSeparatorDoesNotSplitTokens() {
    // U+001C is neither ASCII whitespace nor Unicode White_Space; it never splits tokens.
    Assertions.assertEquals("the" + cp(0x001C) + "fox",
        filterEnglish("the" + cp(0x001C) + "fox the\n"));
  }

  private String filterEnglish(String input) {
    System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    StopwordFilterTool tool = new StopwordFilterTool();
    tool.run(new String[] {"en"});
    return capturedOut.toString(StandardCharsets.UTF_8).trim();
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }
}
