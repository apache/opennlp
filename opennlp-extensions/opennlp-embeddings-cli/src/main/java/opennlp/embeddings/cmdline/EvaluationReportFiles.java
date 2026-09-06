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

/** Report paths and input-file checks for evaluation commands. */
final class EvaluationReportFiles {

  private final Path markdown;
  private final Path tsv;
  private final List<Path> inputs;

  /**
   * Prepares separate Markdown and TSV output paths.
   *
   * @param markdown The requested Markdown file.
   * @param inputs The input paths to protect from report writes.
   * @throws IllegalArgumentException If an argument is null, output has no filename,
   *         report paths refer to one file, a report path is a dangling symbolic link,
   *         or a report path refers to an input file.
   * @throws IOException If file identity cannot be checked.
   */
  EvaluationReportFiles(Path markdown, Path... inputs) throws IOException {
    if (markdown == null) {
      throw new IllegalArgumentException("markdown must not be null");
    }
    if (inputs == null) {
      throw new IllegalArgumentException("inputs must not be null");
    }
    for (Path input : inputs) {
      if (input == null) {
        throw new IllegalArgumentException("inputs must not contain null");
      }
    }
    if (markdown.getFileName() == null) {
      throw new IllegalArgumentException("Report output must name a file");
    }
    this.markdown = markdown;
    this.inputs = List.of(inputs);
    final String name = markdown.getFileName().toString();
    final int dot = name.lastIndexOf('.');
    tsv = markdown.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + ".tsv");
    validatePaths();
  }

  /**
   * Checks output paths before creating or replacing either report.
   *
   * @throws IllegalArgumentException If report paths overlap or would overwrite an input.
   * @throws IOException If file identity cannot be checked.
   */
  private void validatePaths() throws IOException {
    final List<Path> outputs = List.of(markdown, tsv);
    for (Path output : outputs) {
      if (Files.isSymbolicLink(output) && !Files.exists(output)) {
        throw new IllegalArgumentException("Report output must not be a dangling symbolic link");
      }
    }
    if (markdown.getFileName().toString().equalsIgnoreCase(tsv.getFileName().toString())
        || sameFile(markdown, tsv)) {
      throw new IllegalArgumentException("Markdown and TSV reports must use different files");
    }
    for (Path input : inputs) {
      for (Path output : outputs) {
        if (sameFile(output, input)) {
          throw new IllegalArgumentException("Report output must not overwrite an input file: "
              + output + " refers to " + input);
        }
      }
    }
  }

  /**
   * Checks absolute path names and existing-file identity, including links.
   *
   * @param first A report path.
   * @param other Another report or input path.
   * @return Whether the paths refer to the same file.
   * @throws IOException If file identity cannot be checked.
   */
  private boolean sameFile(Path first, Path other) throws IOException {
    return first.toAbsolutePath().equals(other.toAbsolutePath())
        || (Files.exists(first) && Files.exists(other) && Files.isSameFile(first, other));
  }

  /**
   * Writes report content after checking the current file paths.
   *
   * @param markdownContent The Markdown report.
   * @param tsvContent The TSV report.
   * @throws IllegalArgumentException If content is null or output paths are invalid.
   * @throws IOException If a file check or write fails.
   */
  void write(String markdownContent, String tsvContent) throws IOException {
    if (markdownContent == null || tsvContent == null) {
      throw new IllegalArgumentException("report content must not be null");
    }
    validatePaths();
    Files.writeString(markdown, markdownContent);
    Files.writeString(tsv, tsvContent);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return markdown + " and " + tsv;
  }
}
