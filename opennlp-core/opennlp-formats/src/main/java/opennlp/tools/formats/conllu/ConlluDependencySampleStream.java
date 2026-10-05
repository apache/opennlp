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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.depparse.DependencySample;
import opennlp.tools.depparse.DependencyTree;
import opennlp.tools.util.ArgumentChecks;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringUtil;

/**
 * Reads {@link DependencySample samples} directly from
 * <a href="https://universaldependencies.org/format.html">CoNLL-U</a> content, mapping
 * the {@code HEAD} and {@code DEPREL} columns of the basic dependency annotation.
 *
 * <p>This reader does not use {@link ConlluStream}, which merges multiword token ranges
 * with their syntactic words for token and lemma samples. Dependency samples instead
 * omit range lines and empty nodes while retaining the syntactic word rows. Sentences
 * with incomplete or invalid basic dependencies are skipped and counted.</p>
 *
 * @since 3.0.0
 */
public class ConlluDependencySampleStream implements ObjectStream<DependencySample> {

  private static final Logger logger =
      LoggerFactory.getLogger(ConlluDependencySampleStream.class);

  /** The number of tab-separated columns of a CoNLL-U word line. */
  private static final int COLUMNS = 10;

  /** The column holding the word index, a multiword token range, or an empty node id. */
  private static final int ID = 0;

  /** The column holding the word form. */
  private static final int FORM = 1;

  /** The column holding the universal part-of-speech tag. */
  private static final int UPOS = 3;

  /** The column holding the language-specific part-of-speech tag. */
  private static final int XPOS = 4;

  /** The column holding the one-based index of the head, {@code 0} for the root. */
  private static final int HEAD = 6;

  /** The HEAD column value of the root word, which has no governing word. */
  private static final String ROOT_HEAD_ID = "0";

  /** The column holding the relation label to the head. */
  private static final int DEPREL = 7;

  /** Separates the columns of a word line. */
  private static final char FIELD_SEPARATOR = '\t';

  /** Marks a multiword token range id such as {@code 1-2}. */
  private static final char MULTIWORD_RANGE = '-';

  /** Marks an empty node id such as {@code 1.1}. */
  private static final char EMPTY_NODE = '.';

  /** The CoNLL-U placeholder of a missing value. */
  private static final String PLACEHOLDER = "_";

  /** The byte order mark some editors prepend to UTF-8 content. */
  private static final char BOM = '\ufeff';

  /** The first character of a comment line. */
  private static final char COMMENT = '#';

  /** The digit zero: the base of the digit values, and the one digit an ID must not start with. */
  private static final char ZERO_DIGIT = '0';

  private final InputStreamFactory in;
  private final int tagColumn;

  private BufferedReader reader;
  private boolean firstLine = true;
  private int skipped;

  /**
   * Initializes the stream.
   *
   * @param in The CoNLL-U content. Must not be {@code null}.
   * @param tagset The tagset whose part-of-speech column feeds the sample tags. Must
   *               not be {@code null}.
   * @throws IOException Thrown if opening the content fails.
   * @throws IllegalArgumentException Thrown if any parameter is {@code null}.
   */
  public ConlluDependencySampleStream(InputStreamFactory in, ConlluTagset tagset)
      throws IOException {
    ArgumentChecks.requireNonNullArg(in, "in");
    ArgumentChecks.requireNonNullArg(tagset, "tagset");
    this.in = in;
    this.tagColumn = tagset == ConlluTagset.U ? UPOS : XPOS;
    this.reader = open();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Sentences without a usable basic dependency annotation are skipped, and their
   * count is logged once the content is exhausted.</p>
   */
  @Override
  public DependencySample read() throws IOException {
    List<String[]> words;
    while (!(words = nextSentence()).isEmpty()) {
      final DependencySample sample = convert(words);
      if (sample != null) {
        return sample;
      }
      skipped++;
    }
    if (skipped > 0) {
      logger.warn("Skipped {} sentence(s) without a complete basic dependency annotation.",
          skipped);
      skipped = 0;
    }
    return null;
  }

  /**
   * Reads the syntactic word lines of the next sentence: comments, multiword token
   * ranges, and empty nodes are dropped; an empty list means the end of the content.
   *
   * <p>Sentences are separated by any line {@link StringUtil#isBlank(CharSequence)}
   * accepts.</p>
   *
   * @return The word lines of the next sentence, or an empty list at the end of the
   *         content. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws InvalidFormatException Thrown if a word line does not have the expected column count.
   */
  private List<String[]> nextSentence() throws IOException {
    final List<String[]> words = new ArrayList<>();
    String line;
    while ((line = reader.readLine()) != null) {
      if (firstLine) {
        firstLine = false;
        if (!line.isEmpty() && line.charAt(0) == BOM) {
          line = line.substring(1);
        }
      }
      if (StringUtil.isBlank(line)) {
        if (!words.isEmpty()) {
          return words;
        }
        continue;
      }
      if (line.charAt(0) == COMMENT) {
        continue;
      }
      final String[] fields = StringUtil.split(line, FIELD_SEPARATOR, -1);
      if (fields.length != COLUMNS) {
        throw new InvalidFormatException("CoNLL-U word line has " + fields.length
            + " columns, expected " + COLUMNS + ": " + line);
      }
      final String id = fields[ID];
      if (id.indexOf(MULTIWORD_RANGE) < 0 && id.indexOf(EMPTY_NODE) < 0) {
        words.add(fields);
      }
    }
    return words;
  }

  /**
   * Reads a word ID, the one-based position of a syntactic word in its sentence, from the
   * ID column or from the HEAD column of another word.
   *
   * @param id The column value. Must not be {@code null}.
   * @return The position, or {@code -1} if the column is not a plain decimal number:
   *         empty, signed, with a leading zero, with a non-ASCII digit, or too large for
   *         an {@code int}.
   */
  private int wordIndex(String id) {
    final int length = id.length();
    if (length == 0 || id.charAt(0) == ZERO_DIGIT) {
      return -1;
    }
    int value = 0;
    for (int i = 0; i < length; i++) {
      final char c = id.charAt(i);
      if (!StringUtil.isAsciiDigit(c)) {
        return -1;
      }
      final int digit = c - ZERO_DIGIT;
      if (value > (Integer.MAX_VALUE - digit) / 10) {
        return -1;
      }
      value = value * 10 + digit;
    }
    return value;
  }

  /**
   * Converts one sentence into a sample.
   *
   * @param words The word lines of the sentence.
   * @return The converted sample, or {@code null} when the sentence's annotation is
   *         unusable: an ID or head that is not a plain decimal, an underscore head,
   *         selected tag or relation, or a graph that is not a tree.
   */
  private DependencySample convert(List<String[]> words) {
    final int n = words.size();
    final String[] tokens = new String[n];
    final String[] tags = new String[n];
    final int[] heads = new int[n];
    final String[] relations = new String[n];
    for (int i = 0; i < n; i++) {
      final String[] word = words.get(i);
      if (wordIndex(word[ID]) != i + 1) {
        return null;
      }
      tokens[i] = word[FORM];
      tags[i] = word[tagColumn];
      if (PLACEHOLDER.equals(tags[i]) || PLACEHOLDER.equals(word[DEPREL])) {
        return null;
      }
      relations[i] = word[DEPREL];
      final int head = ROOT_HEAD_ID.equals(word[HEAD]) ? 0 : wordIndex(word[HEAD]);
      if (head < 0) {
        return null;
      }
      heads[i] = head - 1;
    }
    try {
      return new DependencySample(tokens, tags, DependencyTree.of(heads, relations));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reopens the content through the {@link InputStreamFactory}, which must therefore
   * produce a fresh stream on every call.</p>
   */
  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    reader.close();
    reader = open();
    firstLine = true;
    skipped = 0;
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws IOException {
    reader.close();
  }

  /**
   * Opens a fresh UTF-8 reader over the content.
   *
   * @return A reader positioned at the start of the content. Never {@code null}.
   * @throws IOException Thrown if opening the content fails.
   */
  private BufferedReader open() throws IOException {
    return new BufferedReader(
        new InputStreamReader(in.createInputStream(), StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)));
  }
}
