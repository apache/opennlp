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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.depparse.DependencyGraph;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Reads {@link DependencySample samples} from a stream of {@link ConlluSentence sentences},
 * mapping the {@code HEAD} and {@code DEPREL} columns of the basic dependency annotation.
 *
 * <p>Empty nodes carry no basic dependency annotation and are dropped; the remaining word
 * lines keep their one-based ids, which are shifted to the zero-based indices of
 * {@link DependencyGraph}. Sentences containing a multiword token are skipped entirely:
 * {@link ConlluStream} merges such a range with its syntactic words, so the dependency
 * annotation of those words is no longer recoverable. Sentences whose annotation is
 * incomplete or invalid, for example an underscore head, are skipped as well. Skips are
 * counted and the count is logged once the stream is exhausted.</p>
 *
 * @since 3.0.0
 */
public class ConlluDependencySampleStream
    extends FilterObjectStream<ConlluSentence, DependencySample> {

  private static final Logger logger =
      LoggerFactory.getLogger(ConlluDependencySampleStream.class);

  private final ConlluTagset tagset;

  private int skipped;

  /**
   * Initializes the stream.
   *
   * @param samples The sentences to convert. Must not be {@code null}.
   * @param tagset The tagset whose part-of-speech column feeds the sample tags. Must not
   *               be {@code null}.
   * @throws IllegalArgumentException Thrown if any parameter is {@code null}.
   */
  public ConlluDependencySampleStream(ObjectStream<ConlluSentence> samples,
      ConlluTagset tagset) {
    super(samples);
    if (tagset == null) {
      throw new IllegalArgumentException("tagset must not be null");
    }
    this.tagset = tagset;
  }

  @Override
  public DependencySample read() throws IOException {
    ConlluSentence sentence;
    while ((sentence = samples.read()) != null) {
      final DependencySample sample = convert(sentence);
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
   * Converts one sentence, or returns {@code null} when its annotation is unusable.
   */
  private DependencySample convert(ConlluSentence sentence) {
    final List<ConlluWordLine> words = new ArrayList<>();
    for (final ConlluWordLine line : sentence.getWordLines()) {
      final String id = line.getId();
      if (id.indexOf('-') >= 0) {
        // a merged multiword token: its syntactic words are gone, the sentence is unusable
        return null;
      }
      if (id.indexOf('.') < 0) {
        words.add(line);
      }
    }
    if (words.isEmpty()) {
      return null;
    }
    final int n = words.size();
    final String[] tokens = new String[n];
    final String[] tags = new String[n];
    final int[] heads = new int[n];
    final String[] relations = new String[n];
    for (int i = 0; i < n; i++) {
      final ConlluWordLine word = words.get(i);
      tokens[i] = word.getForm();
      tags[i] = word.getPosTag(tagset);
      relations[i] = word.getDeprel();
      try {
        heads[i] = Integer.parseInt(word.getHead()) - 1;
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
}
