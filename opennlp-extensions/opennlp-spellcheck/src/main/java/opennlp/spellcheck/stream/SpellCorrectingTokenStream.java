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

package opennlp.spellcheck.stream;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

import opennlp.spellcheck.SpellChecker;
import opennlp.spellcheck.dictionary.SymSpellModel;
import opennlp.spellcheck.normalizer.SpellCheckingCharSequenceNormalizer;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * A {@link FilterObjectStream} for <em>tokenized</em> data: each element read from the
 * wrapped {@link ObjectStream} is a string of tokens separated by a known delimiter
 * (whitespace by default). Every token is spell-corrected independently and the tokens
 * are re-joined with the same delimiter.
 *
 * <p>This is the shape produced by OpenNLP tokenizers / token-sample formats and is
 * what the trainable components consume: a fixed sequence of tokens per element. Unlike
 * {@link SpellCorrectingObjectStream} in compound mode, this stream is
 * <em>token-count preserving</em> &ndash; it never splits or merges tokens, so the
 * corrected element stays aligned with any parallel annotation (tags, spans).</p>
 *
 * <p>Correction always runs in
 * {@link SpellCheckingCharSequenceNormalizer.Mode#PER_TOKEN per-token} mode and reuses
 * the normalizer's guards (minimum length, skip numbers/URLs, never change a word the
 * dictionary already contains) and its casing preservation.</p>
 *
 * <p>{@code null} (end of stream) is forwarded unchanged; {@link #reset()} and
 * {@link #close()} delegate to the wrapped stream.</p>
 */
public class SpellCorrectingTokenStream extends FilterObjectStream<String, String> {

  /** The default delimiter splitting and re-joining tokens (a single space). */
  public static final String DEFAULT_DELIMITER = " ";

  private final SpellCheckingCharSequenceNormalizer normalizer;
  private final String delimiter;
  private final Pattern splitPattern;

  /**
   * Wraps {@code samples} with a default corrector ({@link #DEFAULT_DELIMITER space}
   * delimited) backed by a {@link SpellChecker}.
   *
   * @param samples      the source token-line stream; must not be {@code null}
   * @param spellChecker the engine used to correct tokens; must not be {@code null}
   */
  public SpellCorrectingTokenStream(ObjectStream<String> samples, SpellChecker spellChecker) {
    this(samples,
        SpellCheckingCharSequenceNormalizer.builder(
            Objects.requireNonNull(spellChecker, "spellChecker must not be null"))
            .mode(SpellCheckingCharSequenceNormalizer.Mode.PER_TOKEN).build(),
        DEFAULT_DELIMITER);
  }

  /**
   * Wraps {@code samples} with a default corrector ({@link #DEFAULT_DELIMITER space}
   * delimited) backed by a loaded {@link SymSpellModel}.
   *
   * @param samples the source token-line stream; must not be {@code null}
   * @param model   the loaded model whose engine is used; must not be {@code null}
   */
  public SpellCorrectingTokenStream(ObjectStream<String> samples, SymSpellModel model) {
    this(samples, Objects.requireNonNull(model, "model must not be null").getSymSpell());
  }

  /**
   * Wraps {@code samples} with an explicitly configured corrector and delimiter.
   *
   * <p>The corrector is forced into per-token mode regardless of how it was built, so
   * the token count is always preserved.</p>
   *
   * @param samples    the source token-line stream; must not be {@code null}
   * @param normalizer the corrector whose guards/config are reused; must not be
   *                   {@code null}
   * @param delimiter  the literal token delimiter to split and re-join on; must not be
   *                   {@code null} or empty
   * @throws NullPointerException     if {@code normalizer} or {@code delimiter} is
   *                                  {@code null}
   * @throws IllegalArgumentException if {@code delimiter} is empty
   */
  public SpellCorrectingTokenStream(ObjectStream<String> samples,
                                    SpellCheckingCharSequenceNormalizer normalizer,
                                    String delimiter) {
    super(samples);
    this.normalizer = Objects.requireNonNull(normalizer, "normalizer must not be null");
    Objects.requireNonNull(delimiter, "delimiter must not be null");
    if (delimiter.isEmpty()) {
      throw new IllegalArgumentException("delimiter must not be empty");
    }
    this.delimiter = delimiter;
    this.splitPattern = Pattern.compile(Pattern.quote(delimiter));
  }

  @Override
  public String read() throws IOException {
    final String line = samples.read();
    if (line == null) {
      return null;
    }
    if (line.isEmpty()) {
      return line;
    }
    final String[] tokens = splitPattern.split(line, -1);
    final StringBuilder out = new StringBuilder(line.length());
    for (int i = 0; i < tokens.length; i++) {
      if (i > 0) {
        out.append(delimiter);
      }
      final String token = tokens[i];
      // Empty tokens (e.g. from leading/trailing/duplicate delimiters) pass through.
      out.append(token.isEmpty() ? token : normalizer.normalize(token));
    }
    return out.toString();
  }
}
