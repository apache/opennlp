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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.depparse.DependencyGraph;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;

/**
 * Reads {@link DependencySample samples} directly from CoNLL-U content, mapping the
 * {@code HEAD} and {@code DEPREL} columns of the basic dependency annotation.
 *
 * <p>The file is parsed raw, deliberately not through {@link ConlluStream}: that stream
 * merges multiword token ranges with their syntactic words, which suits the token and
 * lemma views but destroys the dependency annotation of every sentence containing a
 * contraction. Here range lines and empty nodes are dropped while their syntactic words
 * are kept, so contraction-bearing sentences train and evaluate normally. Sentences
 * whose annotation is incomplete or invalid, for example an underscore head, are
 * skipped and counted; the count is logged once the stream is exhausted.</p>
 *
 * @since 3.0.0
 */
public class ConlluDependencySampleStream implements ObjectStream<DependencySample> {

  private static final Logger logger =
      LoggerFactory.getLogger(ConlluDependencySampleStream.class);

  private static final int COLUMNS = 10;
  private static final int FORM = 1;
  private static final int UPOS = 3;
  private static final int XPOS = 4;
  private static final int HEAD = 6;
  private static final int DEPREL = 7;

  private final InputStreamFactory in;
  private final int tagColumn;

  private BufferedReader reader;
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
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    if (tagset == null) {
      throw new IllegalArgumentException("tagset must not be null");
    }
    this.in = in;
    this.tagColumn = tagset == ConlluTagset.U ? UPOS : XPOS;
    this.reader = open();
  }

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
   */
  private List<String[]> nextSentence() throws IOException {
    final List<String[]> words = new ArrayList<>();
    String line;
    while ((line = reader.readLine()) != null) {
      if (line.isBlank()) {
        if (!words.isEmpty()) {
          return words;
        }
        continue;
      }
      if (line.charAt(0) == '#') {
        continue;
      }
      final String[] fields = line.split("\t", -1);
      if (fields.length < COLUMNS) {
        throw new IOException("not a CoNLL-U word line: " + line);
      }
      final String id = fields[0];
      if (id.indexOf('-') < 0 && id.indexOf('.') < 0) {
        words.add(fields);
      }
    }
    return words;
  }

  /**
   * Converts one sentence, or returns {@code null} when its annotation is unusable.
   */
  private DependencySample convert(List<String[]> words) {
    final int n = words.size();
    final String[] tokens = new String[n];
    final String[] tags = new String[n];
    final int[] heads = new int[n];
    final String[] relations = new String[n];
    for (int i = 0; i < n; i++) {
      final String[] word = words.get(i);
      tokens[i] = word[FORM];
      tags[i] = word[tagColumn];
      relations[i] = word[DEPREL];
      try {
        heads[i] = Integer.parseInt(word[HEAD]) - 1;
      } catch (NumberFormatException e) {
        return null;
      }
    }
    try {
      return new DependencySample(tokens, tags, DependencyGraph.of(heads, relations));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    reader.close();
    reader = open();
    skipped = 0;
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }

  private BufferedReader open() throws IOException {
    return new BufferedReader(
        new InputStreamReader(in.createInputStream(), StandardCharsets.UTF_8));
  }
}
