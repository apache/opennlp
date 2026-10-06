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

package opennlp.tools.document;

import opennlp.tools.util.StringUtil;

/**
 * The standard {@link LayerKey layer keys} for the results the toolkit produces itself.
 *
 * <p>Every key defined here carries the {@code opennlp:} id prefix, which
 * {@link #key(String, Class)} and {@link #documentKey(String, Class)} apply. Other
 * producers define their keys in their own packages under their own prefix.</p>
 *
 * @since 3.0.0
 */
public final class Layers {

  /** The id prefix of every key the toolkit defines. */
  private static final String NAMESPACE = "opennlp:";

  /**
   * Sentence boundaries; each annotation covers one sentence and carries its text.
   */
  public static final LayerKey<String> SENTENCES = key("sentences", String.class);

  /**
   * Token boundaries; each annotation covers one token and carries its text.
   */
  public static final LayerKey<String> TOKENS = key("tokens", String.class);

  /**
   * Part-of-speech tags; one annotation per token, aligned with {@link #TOKENS} by
   * position, carrying the tag.
   */
  public static final LayerKey<String> POS_TAGS = key("pos", String.class);

  /**
   * Named entities; each annotation covers one mention and carries the entity type as
   * its value. The annotation's span carries offsets only.
   */
  public static final LayerKey<String> ENTITIES = key("entities", String.class);

  /**
   * Creates a {@link LayerKey.Scope#POSITIONAL positional} key in the toolkit's
   * {@code opennlp:} namespace, for a layer the toolkit itself produces.
   *
   * @param name The layer name without a namespace, for example {@code tokens}. Must
   *             not be {@code null}, blank, or contain {@code ':'}.
   * @param type The class of the annotation values stored under the key. Must not be
   *             {@code null}.
   * @param <T> The type of the annotation values.
   * @return A key whose id is the name under the {@code opennlp:} prefix. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null}, blank, or
   *         contains {@code ':'}, or {@code type} is {@code null}.
   */
  public static <T> LayerKey<T> key(String name, Class<T> type) {
    return LayerKey.of(NAMESPACE + validName(name), type);
  }

  /**
   * Creates a {@link LayerKey.Scope#DOCUMENT document-scoped} key in the toolkit's
   * {@code opennlp:} namespace, for a whole-document value the toolkit itself produces.
   *
   * @param name The layer name without a namespace, for example {@code language}. Must
   *             not be {@code null}, blank, or contain {@code ':'}.
   * @param type The class of the annotation values stored under the key. Must not be
   *             {@code null}.
   * @param <T> The type of the annotation values.
   * @return A document-scoped key whose id is the name under the {@code opennlp:}
   *         prefix. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null}, blank, or
   *         contains {@code ':'}, or {@code type} is {@code null}.
   */
  public static <T> LayerKey<T> documentKey(String name, Class<T> type) {
    return LayerKey.document(NAMESPACE + validName(name), type);
  }

  /**
   * Validates a namespace-free layer name.
   *
   * @param name The name to validate.
   * @return The validated name.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null}, blank, or
   *         contains {@code ':'}.
   */
  private static String validName(String name) {
    if (name == null || StringUtil.isBlank(name)) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    if (name.indexOf(':') >= 0) {
      throw new IllegalArgumentException(
          "name must not contain ':', the namespace is applied by this factory: " + name);
    }
    return name;
  }

  private Layers() {
    // Not instantiated; this class provides constants and static key factories only.
  }
}
