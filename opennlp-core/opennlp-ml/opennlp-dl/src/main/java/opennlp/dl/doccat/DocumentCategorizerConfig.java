/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl.doccat;

import java.util.Collections;
import java.util.Map;

import opennlp.dl.JsonScan;

/**
 * The part of a model configuration that a {@link DocumentCategorizerDL} uses: the
 * {@code id2label} map of output index to label.
 *
 * @param id2label The labels by output index.
 */
public record DocumentCategorizerConfig(Map<String, String> id2label) {

  private static final String ID_TO_LABEL_KEY = "id2label";

  @Override
  public Map<String, String> id2label() {
    return Collections.unmodifiableMap(id2label);
  }

  /**
   * Reads the top-level {@code id2label} member of a model configuration, the object that
   * maps each output index to its label in the {@code config.json} files that accompany
   * classification models. Keys and labels are decoded from their escapes, and a later entry
   * for the same key overwrites an earlier one. An {@code id2label} member nested in another
   * member is not the configuration's map.
   *
   * @param json The JSON text of the configuration. Blank text, with or without a leading
   *     byte order mark, is a configuration without labels. Must not be {@code null}.
   * @return The configuration, with an empty map if the configuration has no top-level
   *     {@code id2label} member.
   * @throws IllegalArgumentException Thrown if {@code json} is {@code null}, if the text is
   *     neither blank nor a single well-formed JSON object, if {@code id2label} is not an
   *     object, or if a label is not a string. The message names the offset or the key.
   */
  public static DocumentCategorizerConfig fromJson(String json) {
    if (json == null) {
      throw new IllegalArgumentException("json must not be null");
    }
    return new DocumentCategorizerConfig(JsonScan.stringObject(json, ID_TO_LABEL_KEY));
  }
}
