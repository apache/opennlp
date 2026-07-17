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
package opennlp.wordnet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.wordnet.WordNetPOS;

/**
 * The Morphy exception lists: the per-part-of-speech tables of irregular inflected forms
 * ({@code mice} to {@code mouse}, {@code went} to {@code go}) that the Morphy algorithm
 * consults before its detachment rules.
 *
 * <p>{@link #load(Path)} reads the four {@code *.exc} files ({@code noun.exc},
 * {@code verb.exc}, {@code adj.exc}, {@code adv.exc}), which must all be present, in the WNDB
 * format: one entry per line, the inflected form followed by one or more base forms, space
 * separated, with underscores standing for spaces in multiword entries. No exception data is
 * bundled; the caller supplies a directory.</p>
 *
 * <p>Lookups fold the queried word the same way the lexicon seam folds lemmas. Instances are
 * immutable after loading and safe for concurrent lookups.</p>
 */
@ThreadSafe
public final class MorphyExceptions {

  private final Map<WordNetPOS, Map<String, List<String>>> byPos;

  /**
   * Wraps the per-part-of-speech exception tables.
   *
   * @param byPos The loaded tables, one per part of speech.
   */
  private MorphyExceptions(Map<WordNetPOS, Map<String, List<String>>> byPos) {
    this.byPos = byPos;
  }

  /**
   * Loads the four exception lists from a directory.
   *
   * @param directory The directory containing {@code noun.exc}, {@code verb.exc},
   *                  {@code adj.exc}, and {@code adv.exc}. Must not be {@code null} and must
   *                  exist.
   * @return The loaded exception lists.
   * @throws IllegalArgumentException Thrown if {@code directory} is {@code null} or not a
   *     directory.
   * @throws InvalidFormatException Thrown if one of the four files is missing or a line is
   *     malformed; the message names the file and line.
   * @throws IOException Thrown if reading a file fails.
   */
  public static MorphyExceptions load(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("Directory must not be null");
    }
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException(
          "Directory does not exist or is not a directory: " + directory);
    }
    final Map<WordNetPOS, Map<String, List<String>>> byPos = new EnumMap<>(WordNetPOS.class);
    byPos.put(WordNetPOS.NOUN, loadFile(directory, "noun.exc"));
    byPos.put(WordNetPOS.VERB, loadFile(directory, "verb.exc"));
    byPos.put(WordNetPOS.ADJECTIVE, loadFile(directory, "adj.exc"));
    byPos.put(WordNetPOS.ADVERB, loadFile(directory, "adv.exc"));
    return new MorphyExceptions(byPos);
  }

  /**
   * Finds the base forms of an irregular inflected form.
   *
   * @param word The inflected form; folded before lookup. Must not be {@code null}.
   * @param pos  The part of speech. Must not be {@code null}.
   * @return The base forms in file order, never {@code null}; empty when the word has no entry.
   * @throws IllegalArgumentException Thrown if {@code word} or {@code pos} is {@code null}.
   */
  public List<String> lookup(String word, WordNetPOS pos) {
    if (word == null) {
      throw new IllegalArgumentException("Word must not be null");
    }
    if (pos == null) {
      throw new IllegalArgumentException("Pos must not be null");
    }
    final List<String> lemmas = byPos.get(pos).get(LemmaFolding.fold(word));
    return lemmas == null ? List.of() : lemmas;
  }

  /**
   * Loads one {@code *.exc} file into a folded inflected-form to base-forms map.
   *
   * @param directory The directory holding the file.
   * @param fileName  The exception file name, for example {@code noun.exc}.
   * @return The folded exception entries.
   * @throws InvalidFormatException Thrown if the file is missing or a line is malformed.
   * @throws IOException Thrown if reading the file fails.
   */
  private static Map<String, List<String>> loadFile(Path directory, String fileName)
      throws IOException {
    final Path file = directory.resolve(fileName);
    if (!Files.isRegularFile(file)) {
      throw new InvalidFormatException("Missing exception list file: " + file);
    }
    final List<String> lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
    final Map<String, List<String>> entries = new HashMap<>(lines.size() * 2);
    for (int i = 0; i < lines.size(); i++) {
      final String line = lines.get(i);
      if (line.isEmpty()) {
        continue;
      }
      final List<String> fields = LemmaFolding.splitOnSpaces(line);
      if (fields.size() < 2) {
        throw new InvalidFormatException("Malformed exception list " + fileName + " at line "
            + (i + 1) + ": expected an inflected form and at least one base form, got: " + line);
      }
      final List<String> lemmas = new ArrayList<>(fields.size() - 1);
      for (final String lemma : fields.subList(1, fields.size())) {
        lemmas.add(LemmaFolding.fold(lemma));
      }
      // A form listed twice keeps its first entry, matching first-match lookup semantics.
      entries.putIfAbsent(LemmaFolding.fold(fields.get(0)), List.copyOf(lemmas));
    }
    return Map.copyOf(entries);
  }
}
