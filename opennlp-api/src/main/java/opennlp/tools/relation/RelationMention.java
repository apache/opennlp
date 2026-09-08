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

package opennlp.tools.relation;

import opennlp.tools.util.StringUtil;

/**
 * A typed relation between two entity annotations. Subject and object values are
 * indices in the entity layer used for extraction.
 *
 * @param type The relation type, for example {@code acquisition}. Must not be
 *             {@code null} or blank.
 * @param subject The index of the subject entity in the entity layer. Must not be
 *                negative.
 * @param object The index of the object entity in the entity layer. Must not be
 *               negative or equal to {@code subject}.
 *
 * @since 3.0.0
 */
public record RelationMention(String type, int subject, int object) {

  /**
   * Validates the relation.
   *
   * @throws IllegalArgumentException Thrown if {@code type} is {@code null} or blank,
   *         an index is negative, or {@code subject} equals {@code object}.
   */
  public RelationMention {
    if (type == null || StringUtil.isBlank(type)) {
      throw new IllegalArgumentException("type must not be null or blank");
    }
    if (subject < 0 || object < 0) {
      throw new IllegalArgumentException(
          "entity indexes must not be negative: " + subject + ", " + object);
    }
    if (subject == object) {
      throw new IllegalArgumentException("subject and object must differ: " + subject);
    }
  }
}
