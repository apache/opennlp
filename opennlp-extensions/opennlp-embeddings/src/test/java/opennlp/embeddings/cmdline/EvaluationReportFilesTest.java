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
package opennlp.embeddings.cmdline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.embeddings.EmbeddingTestFixtures;
import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.corpus.DictionaryEntry;
import opennlp.embeddings.eval.HnswBaseline;
import opennlp.tools.cmdline.TerminateToolException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Exercises report output with an original model and generated input files. */
class EvaluationReportFilesTest {

  private static final String EXISTING_REPORT = "existing report\n";
  private static final String MARKDOWN_NAME = "report.md";
  private static final String TSV_NAME = "report.tsv";

  @TempDir
  Path directory;

  private Path model;
  private Path passages;
  private Path dictionary;

  /**
   * Creates the model and retrieval inputs used by both commands.
   *
   * @throws IOException If an input file cannot be written.
   */
  @BeforeEach
  void prepareInputs() throws IOException {
    model = Files.createDirectory(directory.resolve("model"));
    EmbeddingTestFixtures.writeAnalogyDirectory(model);
    passages = directory.resolve("passages.jsonl");
    CasePassage.writeJsonl(List.of(
        new CasePassage("royalty", "", "", "", "", "king queen king queen"),
        new CasePassage("fruit", "", "", "", "", "apple apple apple apple")), passages);
    dictionary = directory.resolve("dictionary.tsv");
    DictionaryEntry.writeTsv(List.of(new DictionaryEntry("APPLE", "apple")), dictionary);
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void testRejectsTsvOutputWithoutWriting(boolean hnsw, boolean existing) throws IOException {
    final Path output = directory.resolve(TSV_NAME);
    if (existing) {
      Files.writeString(output, EXISTING_REPORT);
    }

    assertInvalidOutput(hnsw, output, "different files");

    if (existing) {
      assertEquals(EXISTING_REPORT, Files.readString(output));
    } else {
      assertFalse(Files.exists(output));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testRejectsRootOutput(boolean hnsw) {
    assertInvalidOutput(hnsw, directory.toAbsolutePath().getRoot(), "name a file");
  }

  @ParameterizedTest
  @CsvSource({"false,report.TSV", "false,report.TsV", "true,report.TSV", "true,report.TsV"})
  void testRejectsTsvExtensionRegardlessOfCase(boolean hnsw, String name) {
    final Path output = directory.resolve(name);

    assertInvalidOutput(hnsw, output, "different files");

    assertFalse(Files.exists(output));
    assertFalse(Files.exists(directory.resolve(TSV_NAME)));
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void testRejectsLinkedReportFilesWithoutWriting(boolean hnsw, boolean symbolic)
      throws IOException {
    final Path output = directory.resolve(MARKDOWN_NAME);
    final Path tsv = directory.resolve(TSV_NAME);
    Files.writeString(output, EXISTING_REPORT);
    createLink(tsv, output, symbolic);

    assertInvalidOutput(hnsw, output, "different files");

    assertEquals(EXISTING_REPORT, Files.readString(output));
    assertEquals(EXISTING_REPORT, Files.readString(tsv));
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @CsvSource({"false,markdown", "false,tsv", "false,both",
      "true,markdown", "true,tsv", "true,both"})
  void testRejectsDanglingReportLinks(boolean hnsw, String linked) throws IOException {
    final Path output = directory.resolve(MARKDOWN_NAME);
    final Path tsv = directory.resolve(TSV_NAME);
    final Path target = directory.resolve("missing.txt");
    if ("markdown".equals(linked)) {
      createLink(output, tsv, true);
    } else if ("tsv".equals(linked)) {
      createLink(tsv, output, true);
    } else {
      createLink(output, target, true);
      createLink(tsv, target, true);
    }

    assertInvalidOutput(hnsw, output, "dangling symbolic link");

    assertFalse(Files.exists(output));
    assertFalse(Files.exists(tsv));
    assertFalse(Files.exists(target));
  }

  @ParameterizedTest
  @CsvSource({
      "false,report.md,report.tsv", "true,report.md,report.tsv",
      "false,report,report.tsv", "true,report,report.tsv",
      "false,report.txt,report.tsv", "true,report.txt,report.tsv",
      "false,.report,.report.tsv", "true,.report,.report.tsv",
      "false,retrieval.results.md,retrieval.results.tsv",
      "true,retrieval.results.md,retrieval.results.tsv"
  })
  void testWritesMarkdownAndTsv(boolean hnsw, String name, String tsvName) throws IOException {
    final Path output = directory.resolve(name);

    run(hnsw, output);

    assertReportContents(output, directory.resolve(tsvName));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testReplacesSeparateExistingReports(boolean hnsw) throws IOException {
    final Path output = directory.resolve(MARKDOWN_NAME);
    final Path tsv = directory.resolve(TSV_NAME);
    Files.writeString(output, EXISTING_REPORT);
    Files.writeString(tsv, EXISTING_REPORT);

    run(hnsw, output);

    assertReportContents(output, tsv);
  }

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void testRejectsInputAsReport(boolean hnsw, boolean dictionaryInput) throws IOException {
    final Path input = dictionaryInput ? dictionary : passages;
    final Path output = dictionaryInput ? directory.resolve("dictionary.md") : passages;
    final Path other = dictionaryInput ? output : directory.resolve("passages.tsv");
    Files.writeString(other, EXISTING_REPORT);
    final byte[] original = Files.readAllBytes(input);

    assertAll(
        () -> assertInvalidOutput(hnsw, output, "input file"),
        () -> assertArrayEquals(original, Files.readAllBytes(input)),
        () -> assertEquals(EXISTING_REPORT, Files.readString(other)));
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @CsvSource({
      "false,false,false,false", "false,false,false,true",
      "false,false,true,false", "false,false,true,true",
      "false,true,false,false", "false,true,false,true",
      "false,true,true,false", "false,true,true,true",
      "true,false,false,false", "true,false,false,true",
      "true,false,true,false", "true,false,true,true",
      "true,true,false,false", "true,true,false,true",
      "true,true,true,false", "true,true,true,true"
  })
  void testRejectsReportLinkToInput(boolean hnsw, boolean dictionaryInput,
                                   boolean tsvLink, boolean symbolic) throws IOException {
    final Path input = dictionaryInput ? dictionary : passages;
    final Path output = directory.resolve(MARKDOWN_NAME);
    final Path tsv = directory.resolve(TSV_NAME);
    final Path other = tsvLink ? output : tsv;
    createLink(tsvLink ? tsv : output, input, symbolic);
    Files.writeString(other, EXISTING_REPORT);
    final byte[] original = Files.readAllBytes(input);

    assertAll(
        () -> assertInvalidOutput(hnsw, output, "input file"),
        () -> assertArrayEquals(original, Files.readAllBytes(input)),
        () -> assertEquals(EXISTING_REPORT, Files.readString(other)));
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void testChecksInputAliasesAgainBeforeWriting(boolean tsvLink, boolean symbolic)
      throws IOException {
    final Path output = directory.resolve(MARKDOWN_NAME);
    final Path tsv = directory.resolve(TSV_NAME);
    final EvaluationReportFiles reports = new EvaluationReportFiles(output, passages, dictionary);
    createLink(tsvLink ? tsv : output, passages, symbolic);
    final Path other = tsvLink ? output : tsv;
    Files.writeString(other, EXISTING_REPORT);
    final byte[] original = Files.readAllBytes(passages);

    assertThrows(IllegalArgumentException.class, () -> reports.write("markdown", "tsv"));

    assertArrayEquals(original, Files.readAllBytes(passages));
    assertEquals(EXISTING_REPORT, Files.readString(other));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testRejectsNullContentBeforeWriting(boolean markdownNull) throws IOException {
    final Path output = directory.resolve(MARKDOWN_NAME);
    final Path tsv = directory.resolve(TSV_NAME);
    final EvaluationReportFiles reports = new EvaluationReportFiles(output, passages, dictionary);

    assertThrows(IllegalArgumentException.class,
        () -> reports.write(markdownNull ? null : "markdown", markdownNull ? "tsv" : null));

    assertFalse(Files.exists(output));
    assertFalse(Files.exists(tsv));
  }

  @Test
  void testRejectsNullPaths() {
    final Path output = directory.resolve(MARKDOWN_NAME);
    assertThrows(IllegalArgumentException.class, () -> new EvaluationReportFiles(null, passages));
    assertThrows(IllegalArgumentException.class,
        () -> new EvaluationReportFiles(output, (Path[]) null));
    assertThrows(IllegalArgumentException.class,
        () -> new EvaluationReportFiles(output, passages, null));
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void testWritesThroughLinksToSeparateExistingReports(boolean hnsw, boolean symbolic)
      throws IOException {
    final Path markdownTarget = directory.resolve("markdown.txt");
    final Path tsvTarget = directory.resolve("metrics.txt");
    Files.writeString(markdownTarget, EXISTING_REPORT);
    Files.writeString(tsvTarget, EXISTING_REPORT);
    final Path output = directory.resolve(MARKDOWN_NAME);
    final Path tsv = directory.resolve(TSV_NAME);
    createLink(output, markdownTarget, symbolic);
    createLink(tsv, tsvTarget, symbolic);

    run(hnsw, output);

    assertReportContents(markdownTarget, tsvTarget);
    assertReportContents(output, tsv);
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void testUsesFileIdentityForParentAfterDirectoryLink(boolean hnsw, boolean existing)
      throws IOException {
    final Path nested = Files.createDirectories(directory.resolve("actual/nested"));
    createLink(directory.resolve("link"), nested, true);
    final Path output = directory.resolve("link/../passages.jsonl");
    final Path actualOutput = directory.resolve("actual/passages.jsonl");
    final Path actualTsv = directory.resolve("actual/passages.tsv");
    if (existing) {
      Files.writeString(actualOutput, EXISTING_REPORT);
      Files.writeString(actualTsv, EXISTING_REPORT);
    }
    final byte[] original = Files.readAllBytes(passages);

    run(hnsw, output);

    assertReportContents(actualOutput, actualTsv);
    assertArrayEquals(original, Files.readAllBytes(passages));
  }

  /**
   * Creates a file link if the file system supports the requested type.
   *
   * @param link The new link path.
   * @param target The target path.
   * @param symbolic Whether to create a symbolic link instead of a hard link.
   * @throws IOException If link creation fails.
   */
  private void createLink(Path link, Path target, boolean symbolic) throws IOException {
    try {
      if (symbolic) {
        Files.createSymbolicLink(link, link.getParent().relativize(target));
      } else {
        Files.createLink(link, target);
      }
    } catch (UnsupportedOperationException e) {
      assumeTrue(false, "File links are not supported: " + e.getMessage());
    }
  }

  /**
   * Checks the Markdown heading and a TSV input count from the same evaluation.
   *
   * @param output The Markdown report.
   * @param tsv The TSV report.
   * @throws IOException If a report cannot be read.
   */
  private void assertReportContents(Path output, Path tsv) throws IOException {
    final String markdown = Files.readString(output);
    final String metrics = Files.readString(tsv);
    assertTrue(markdown.startsWith("# "), markdown);
    assertTrue(markdown.contains("construction, vector insertion and freeze"), markdown);
    assertTrue(metrics.contains("passages.indexed\t2\n"), metrics);
    assertTrue(metrics.contains("index.buildScope\tconstruction,insertion,freeze\n"), metrics);
    assertFalse(metrics.startsWith("# "), metrics);
  }

  /**
   * Checks rejection of invalid report paths.
   *
   * @param hnsw Whether to use the HNSW command.
   * @param output The requested Markdown path.
   * @param message The expected diagnostic text.
   */
  private void assertInvalidOutput(boolean hnsw, Path output, String message) {
    final RuntimeException error;
    if (hnsw) {
      error = assertThrows(IllegalArgumentException.class, () -> run(true, output));
    } else {
      final TerminateToolException toolError =
          assertThrows(TerminateToolException.class, () -> run(false, output));
      assertEquals(1, toolError.getCode());
      error = toolError;
    }
    assertTrue(error.getMessage().contains(message), error.getMessage());
  }

  /**
   * Runs an evaluation command with the generated input files.
   *
   * @param hnsw Whether to use the HNSW command.
   * @param output The requested Markdown path.
   * @throws IOException If the HNSW command cannot read or write a file.
   */
  private void run(boolean hnsw, Path output) throws IOException {
    if (hnsw) {
      HnswBaseline.main(new String[] {
          model.toString(), passages.toString(), dictionary.toString(), output.toString(), "1"
      });
    } else {
      new EvalVectorSearchTool().run(new String[] {
          "-model", model.toString(), "-passages", passages.toString(),
          "-dictionary", dictionary.toString(), "-out", output.toString(), "-topK", "1"
      });
    }
  }
}
