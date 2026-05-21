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

import opennlp.spellcheck.SpellChecker;
import opennlp.spellcheck.dictionary.SymSpellModel;
import opennlp.spellcheck.normalizer.SpellCheckingCharSequenceNormalizer;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * A {@link FilterObjectStream} that spell-corrects each {@code String} line read from a
 * wrapped {@link ObjectStream} (for example a {@link PlainTextByLineStream}).
 *
 * <p>Correction is delegated to a {@link SpellCheckingCharSequenceNormalizer}, so the
 * same per-token / compound modes and skip guards apply. Each source line is passed
 * through the normalizer and the corrected line is emitted; {@code null} (end of
 * stream) is forwarded unchanged, honoring the {@link ObjectStream} exhaustion
 * contract. {@link #reset()} and {@link #close()} delegate to the wrapped stream via
 * {@link FilterObjectStream}.</p>
 *
 * <p>Typical use is to drop the corrector into an existing preprocessing chain that
 * already reads text line-by-line:</p>
 * <pre>{@code
 * ObjectStream<String> lines = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
 * ObjectStream<String> corrected = new SpellCorrectingObjectStream(lines, model);
 * }</pre>
 *
 * <p>For tokenized data (one whitespace-separated token list per element) use
 * {@link SpellCorrectingTokenStream}, which corrects token-by-token and re-joins.</p>
 */
public class SpellCorrectingObjectStream extends FilterObjectStream<String, String> {

  private final SpellCheckingCharSequenceNormalizer normalizer;

  /**
   * Wraps {@code samples} with a default per-token corrector backed by a
   * {@link SpellChecker}.
   *
   * @param samples      the source line stream; must not be {@code null}
   * @param spellChecker the engine used to correct lines; must not be {@code null}
   */
  public SpellCorrectingObjectStream(ObjectStream<String> samples, SpellChecker spellChecker) {
    this(samples, new SpellCheckingCharSequenceNormalizer(
        Objects.requireNonNull(spellChecker, "spellChecker must not be null")));
  }

  /**
   * Wraps {@code samples} with a default per-token corrector backed by a loaded
   * {@link SymSpellModel}.
   *
   * @param samples the source line stream; must not be {@code null}
   * @param model   the loaded model whose engine is used; must not be {@code null}
   */
  public SpellCorrectingObjectStream(ObjectStream<String> samples, SymSpellModel model) {
    this(samples, new SpellCheckingCharSequenceNormalizer(
        Objects.requireNonNull(model, "model must not be null")));
  }

  /**
   * Wraps {@code samples} with an explicitly configured corrector, so callers can pick
   * the mode and guards through {@link SpellCheckingCharSequenceNormalizer.Builder}.
   *
   * @param samples    the source line stream; must not be {@code null}
   * @param normalizer the corrector to apply to each line; must not be {@code null}
   */
  public SpellCorrectingObjectStream(ObjectStream<String> samples,
                                     SpellCheckingCharSequenceNormalizer normalizer) {
    super(samples);
    this.normalizer = Objects.requireNonNull(normalizer, "normalizer must not be null");
  }

  @Override
  public String read() throws IOException {
    final String line = samples.read();
    if (line == null) {
      return null;
    }
    return normalizer.normalize(line).toString();
  }
}
