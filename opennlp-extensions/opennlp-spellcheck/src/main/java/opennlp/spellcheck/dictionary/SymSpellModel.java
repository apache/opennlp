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

package opennlp.spellcheck.dictionary;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import opennlp.spellcheck.symspell.SymSpell;
import opennlp.spellcheck.symspell.SymSpellConfig;
import opennlp.tools.util.model.SerializableArtifact;

/**
 * A serializable spell-correction model: a built {@link SymSpell} engine together with
 * the source frequency data and the metadata needed to reproduce and identify it.
 *
 * <p>The model keeps the <em>source</em> dictionary (unigram counts and optional bigram
 * counts) and the {@link SymSpellConfig configuration} rather than the derived delete
 * index. This is what the {@link SymSpellModelSerializer} writes; the (much larger)
 * delete index is rebuilt by replaying the source through {@link SymSpell#add} /
 * {@link SymSpell#addBigram} when the engine is constructed. See the serializer's class
 * javadoc for the rationale.</p>
 *
 * <p>Instances are immutable: the engine is built once at construction time and exposed
 * read-only via {@link #getSymSpell()}; the source maps returned by {@link #unigrams()}
 * and {@link #bigrams()} are unmodifiable views.</p>
 *
 * <p>As a {@link SerializableArtifact} this type can be embedded in OpenNLP model
 * containers and is round-tripped by {@link SymSpellModelSerializer}.</p>
 */
public final class SymSpellModel implements SerializableArtifact {

  /** The default model name fragment used for classpath discovery. */
  public static final String DEFAULT_MODEL_NAME = "spellcheck-symspell";

  /** The default model version used when none is supplied. */
  public static final String DEFAULT_MODEL_VERSION = "1.0";

  private final String language;
  private final String name;
  private final String version;
  private final SymSpellConfig config;
  private final Map<String, Long> unigrams;
  private final Map<String, Long> bigrams;
  private final SymSpell symSpell;

  /**
   * Creates a model and builds its {@link SymSpell} engine from the supplied source data.
   *
   * @param language an IETF/ISO language tag (e.g. {@code "en"}); must not be
   *                 {@code null} or blank
   * @param config   the engine configuration; must not be {@code null}
   * @param unigrams the {@code word -> count} source map; must not be {@code null}
   * @param bigrams  the {@code "w1 w2" -> count} source map; must not be {@code null}
   *                 (may be empty)
   */
  public SymSpellModel(String language, SymSpellConfig config,
                       Map<String, Long> unigrams, Map<String, Long> bigrams) {
    this(language, DEFAULT_MODEL_NAME, DEFAULT_MODEL_VERSION, config, unigrams, bigrams);
  }

  /**
   * Creates a model with explicit name and version, and builds its {@link SymSpell}
   * engine from the supplied source data.
   *
   * @param language an IETF/ISO language tag (e.g. {@code "en"}); must not be
   *                 {@code null} or blank
   * @param name     the model name (becomes {@code model.name}); must not be
   *                 {@code null} or blank
   * @param version  the model version (becomes {@code model.version}); must not be
   *                 {@code null} or blank
   * @param config   the engine configuration; must not be {@code null}
   * @param unigrams the {@code word -> count} source map; must not be {@code null}
   * @param bigrams  the {@code "w1 w2" -> count} source map; must not be {@code null}
   *                 (may be empty)
   */
  public SymSpellModel(String language, String name, String version, SymSpellConfig config,
                       Map<String, Long> unigrams, Map<String, Long> bigrams) {
    this.language = requireNonBlank(language, "language");
    this.name = requireNonBlank(name, "name");
    this.version = requireNonBlank(version, "version");
    this.config = Objects.requireNonNull(config, "config must not be null");
    Objects.requireNonNull(unigrams, "unigrams must not be null");
    Objects.requireNonNull(bigrams, "bigrams must not be null");

    // Defensive, order-preserving copies so the model is fully immutable.
    this.unigrams = Collections.unmodifiableMap(new LinkedHashMap<>(unigrams));
    this.bigrams = Collections.unmodifiableMap(new LinkedHashMap<>(bigrams));
    this.symSpell = build(config, this.unigrams, this.bigrams);
  }

  private static SymSpell build(SymSpellConfig config,
                                Map<String, Long> unigrams, Map<String, Long> bigrams) {
    final SymSpell engine = new SymSpell(config);
    for (Map.Entry<String, Long> e : unigrams.entrySet()) {
      engine.add(e.getKey(), e.getValue());
    }
    for (Map.Entry<String, Long> e : bigrams.entrySet()) {
      final String key = e.getKey();
      final int space = key.indexOf(' ');
      if (space < 1 || space >= key.length() - 1) {
        throw new IllegalArgumentException("invalid bigram key (expected 'w1 w2'): " + key);
      }
      engine.addBigram(key.substring(0, space), key.substring(space + 1), e.getValue());
    }
    return engine;
  }

  /** @return the ready-to-query engine backed by this model. */
  public SymSpell getSymSpell() {
    return symSpell;
  }

  /** @return the language tag of this model. */
  public String getLanguage() {
    return language;
  }

  /** @return the model name (also emitted as {@code model.name}). */
  public String getName() {
    return name;
  }

  /** @return the model version (also emitted as {@code model.version}). */
  public String getVersion() {
    return version;
  }

  /** @return the configuration used to build the engine. */
  public SymSpellConfig getConfig() {
    return config;
  }

  /** @return an unmodifiable view of the {@code word -> count} source map. */
  public Map<String, Long> unigrams() {
    return unigrams;
  }

  /** @return an unmodifiable view of the {@code "w1 w2" -> count} source map. */
  public Map<String, Long> bigrams() {
    return bigrams;
  }

  @Override
  public Class<?> getArtifactSerializerClass() {
    return SymSpellModelSerializer.class;
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be null or blank");
    }
    return value;
  }
}
