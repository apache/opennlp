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

package opennlp.spellcheck.symspell;

import java.util.Objects;

import opennlp.spellcheck.distance.DamerauOSADistance;
import opennlp.spellcheck.distance.EditDistance;

/**
 * Immutable configuration for {@link SymSpell}, created through {@link #builder()}.
 *
 * <p>The tunables mirror the SymSpell reference implementation:</p>
 * <ul>
 *   <li><b>maxDictionaryEditDistance</b> &ndash; the largest edit distance for which the
 *       deletes index is precomputed; queries cannot exceed this.</li>
 *   <li><b>prefixLength</b> &ndash; only the first {@code prefixLength} symbols of each
 *       term are used to generate deletes, trading index size for recall on long words.</li>
 *   <li><b>countThreshold</b> &ndash; the minimum corpus count for a term to be indexed.</li>
 *   <li><b>editDistance</b> &ndash; the verification metric, defaulting to
 *       {@link DamerauOSADistance}.</li>
 *   <li><b>corpusWordCount</b> &ndash; the corpus normalization constant <i>N</i> used by
 *       the Naive-Bayes word combine/split scoring in
 *       {@link SymSpell#lookupCompound(String, int)}. Defaults to
 *       {@link #DERIVE_CORPUS_WORD_COUNT}, which makes the engine derive <i>N</i> from the
 *       summed counts of the loaded dictionary so it is always corpus-correct; set it
 *       explicitly to pin <i>N</i> (e.g. to the full-corpus total a reference dictionary was
 *       drawn from).</li>
 * </ul>
 */
public final class SymSpellConfig {

  /**
   * Sentinel for {@link #corpusWordCount()} meaning "derive <i>N</i> from the summed counts
   * of the loaded dictionary" rather than pinning it to a fixed value.
   */
  public static final long DERIVE_CORPUS_WORD_COUNT = 0L;

  private final int maxDictionaryEditDistance;
  private final int prefixLength;
  private final long countThreshold;
  private final EditDistance editDistance;
  private final long corpusWordCount;

  private SymSpellConfig(Builder b) {
    this.maxDictionaryEditDistance = b.maxDictionaryEditDistance;
    this.prefixLength = b.prefixLength;
    this.countThreshold = b.countThreshold;
    this.editDistance = b.editDistance;
    this.corpusWordCount = b.corpusWordCount;
  }

  public int maxDictionaryEditDistance() {
    return maxDictionaryEditDistance;
  }

  public int prefixLength() {
    return prefixLength;
  }

  public long countThreshold() {
    return countThreshold;
  }

  public EditDistance editDistance() {
    return editDistance;
  }

  /**
   * @return the pinned corpus normalization constant <i>N</i>, or
   *     {@link #DERIVE_CORPUS_WORD_COUNT} when <i>N</i> is derived from the loaded
   *     dictionary's summed counts.
   */
  public long corpusWordCount() {
    return corpusWordCount;
  }

  /**
   * @return a builder with the SymSpell reference defaults
   *     (maxDictionaryEditDistance=2, prefixLength=7, countThreshold=1,
   *     editDistance={@link DamerauOSADistance}).
   */
  public static Builder builder() {
    return new Builder();
  }

  /** @return a configuration with all reference defaults. */
  public static SymSpellConfig defaultConfig() {
    return builder().build();
  }

  /** Mutable builder for {@link SymSpellConfig}. */
  public static final class Builder {

    private int maxDictionaryEditDistance = 2;
    private int prefixLength = 7;
    private long countThreshold = 1;
    private EditDistance editDistance = DamerauOSADistance.INSTANCE;
    private long corpusWordCount = DERIVE_CORPUS_WORD_COUNT;

    private Builder() {
    }

    /**
     * @param value largest precomputed dictionary edit distance; must be {@code >= 0}
     * @return this builder
     */
    public Builder maxDictionaryEditDistance(int value) {
      if (value < 0) {
        throw new IllegalArgumentException("maxDictionaryEditDistance must be >= 0: " + value);
      }
      this.maxDictionaryEditDistance = value;
      return this;
    }

    /**
     * @param value number of leading symbols used for delete generation; must be
     *     {@code >= 1} and {@code > maxDictionaryEditDistance}
     * @return this builder
     */
    public Builder prefixLength(int value) {
      if (value < 1) {
        throw new IllegalArgumentException("prefixLength must be >= 1: " + value);
      }
      this.prefixLength = value;
      return this;
    }

    /**
     * @param value minimum corpus count for a term to be indexed; must be {@code >= 1}
     * @return this builder
     */
    public Builder countThreshold(long value) {
      if (value < 1) {
        throw new IllegalArgumentException("countThreshold must be >= 1: " + value);
      }
      this.countThreshold = value;
      return this;
    }

    /**
     * @param value verification metric to inject; must not be {@code null}
     * @return this builder
     */
    public Builder editDistance(EditDistance value) {
      this.editDistance = Objects.requireNonNull(value, "editDistance must not be null");
      return this;
    }

    /**
     * Pins the corpus normalization constant <i>N</i> used by the Naive-Bayes word
     * combine/split scoring in {@link SymSpell#lookupCompound(String, int)}.
     *
     * @param value the corpus word count to pin, or {@link #DERIVE_CORPUS_WORD_COUNT} to
     *              derive <i>N</i> from the loaded dictionary's summed counts; must be
     *              {@code >= 0}
     * @return this builder
     */
    public Builder corpusWordCount(long value) {
      if (value < 0) {
        throw new IllegalArgumentException("corpusWordCount must be >= 0: " + value);
      }
      this.corpusWordCount = value;
      return this;
    }

    /** @return the immutable configuration. */
    public SymSpellConfig build() {
      if (prefixLength <= maxDictionaryEditDistance) {
        throw new IllegalArgumentException(
            "prefixLength (" + prefixLength + ") must be > maxDictionaryEditDistance ("
                + maxDictionaryEditDistance + ")");
      }
      return new SymSpellConfig(this);
    }
  }
}
