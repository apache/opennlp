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

import java.util.Objects;

import opennlp.tools.util.StringUtil;

/**
 * Identifies one annotation layer of a {@link Document} and carries the type of that
 * layer's annotation values, so reading a layer back is statically typed.
 *
 * <p>The key space is deliberately open: any producer may define new keys in its own
 * package, and the container never enumerates them. Two keys are equal when both their
 * id and their value type are equal, so independently created constants for the same
 * layer interoperate. Standard keys for the toolkit's own results live in
 * {@link Layers}.</p>
 *
 * @param <T> The type of the annotation values stored under this key.
 *
 * @since 3.0.0
 */
public final class LayerKey<T> {

  private final String id;
  private final Class<T> type;

  private LayerKey(String id, Class<T> type) {
    this.id = id;
    this.type = type;
  }

  /**
   * Creates a {@link LayerKey}.
   *
   * @param id The layer identifier, for example {@code opennlp:tokens}. Keys defined by
   *           the toolkit carry the {@code opennlp:} prefix, an extension uses its own
   *           prefix, and a bare id is legal for an application-local layer. Must not
   *           be {@code null} or blank.
   * @param type The class of the annotation values stored under the key. Must not be
   *             {@code null}.
   * @param <T> The type of the annotation values.
   * @return A {@link LayerKey}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code id} is {@code null} or blank, or
   *         {@code type} is {@code null}.
   */
  public static <T> LayerKey<T> of(String id, Class<T> type) {
    if (id == null || StringUtil.isBlank(id)) {
      throw new IllegalArgumentException("id must not be null or blank");
    }
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    return new LayerKey<>(id, type);
  }

  /**
   * @return The layer identifier. Never {@code null}.
   */
  public String id() {
    return id;
  }

  /**
   * @return The class of the annotation values stored under this key. Never {@code null}.
   */
  public Class<T> type() {
    return type;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof LayerKey<?> other)) {
      return false;
    }
    return id.equals(other.id) && type.equals(other.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, type);
  }

  @Override
  public String toString() {
    return id + '<' + type.getSimpleName() + '>';
  }
}
