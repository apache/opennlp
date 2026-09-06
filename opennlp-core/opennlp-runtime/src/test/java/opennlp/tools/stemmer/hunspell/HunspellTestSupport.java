/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

package opennlp.tools.stemmer.hunspell;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/** Utilities for optional native reference tests. */
final class HunspellTestSupport {

  /** Prevents construction. */
  private HunspellTestSupport() { }

  /**
   * Normalizes field separators without changing field order or values.
   *
   * @param analysis The reference text.
   * @return Text with one space between fields.
   */
  static String normalizeFields(String analysis) {
    final StringBuilder result = new StringBuilder();
    boolean space = false;
    for (int i = 0; i < analysis.length(); i++) {
      final char value = analysis.charAt(i);
      if (Character.isWhitespace(value)) {
        space = !result.isEmpty();
      } else {
        if (space) {
          result.append(' ');
        }
        result.append(value);
        space = false;
      }
    }
    return result.toString();
  }

  /**
   * Executes the native reference on an original dictionary.
   *
   * @param directory Temporary output directory.
   * @param affix Original affix content without SET.
   * @param words Original dictionary content.
   * @param input Input text.
   * @param operation The native operation.
   * @return The reference output.
   * @throws Exception If preparation or execution fails.
   */
  static String nativeResult(Path directory, String affix, String words,
      String input, String operation) throws Exception {
    final Path affixPath = directory.resolve("original.aff");
    final Path wordsPath = directory.resolve("original.dic");
    Files.writeString(affixPath, "SET UTF-8\n" + affix);
    Files.writeString(wordsPath, words);
    return nativeResult(directory, affixPath, wordsPath, input, operation);
  }

  /**
   * Executes the native reference on existing dictionary files.
   *
   * @param directory Temporary output directory.
   * @param affix The external affix file.
   * @param words The external dictionary file.
   * @param input Input text, optionally separated by newlines.
   * @param operation The native operation.
   * @return The reference output.
   * @throws Exception If preparation or execution fails.
   */
  static String nativeResult(Path directory, Path affix, Path words,
      String input, String operation) throws Exception {
    final String executable = System.getProperty("opennlp.hunspell.reference");
    Assumptions.assumeTrue(executable != null, "no native Hunspell reference configured");
    Charset encoding = StandardCharsets.UTF_8;
    for (String line : Files.readAllLines(affix, StandardCharsets.ISO_8859_1)) {
      final String fields = normalizeFields(line);
      if (fields.startsWith("SET ")) {
        encoding = Charset.forName(fields.substring(4));
        break;
      }
    }
    final Path output = directory.resolve("native.txt");
    final Path errors = directory.resolve("native-errors.txt");
    final Process process = new ProcessBuilder(executable, affix.toString(), words.toString(), operation)
        .redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
    try {
      try (var writer = process.outputWriter(encoding)) {
        writer.write(input);
        writer.newLine();
      }
      Assertions.assertTrue(process.waitFor(10, TimeUnit.SECONDS), "native Hunspell timed out");
      Assertions.assertEquals(0, process.exitValue(), Files.readString(errors));
      return Files.readString(output, encoding);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
      }
    }
  }
}
