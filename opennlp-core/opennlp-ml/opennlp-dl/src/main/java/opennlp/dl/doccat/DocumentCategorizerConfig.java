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
import java.util.HashMap;
import java.util.Map;

import opennlp.dl.JsonScan;
import opennlp.tools.util.StringUtil;

public record DocumentCategorizerConfig(Map<String, String> id2label) {

  private static final String ID_TO_LABEL_KEY = "id2label";
  private static final String BYTE_ORDER_MARK = "\uFEFF";

  @Override
  public Map<String, String> id2label() {
    return Collections.unmodifiableMap(id2label);
  }

  /**
   * Reads the {@code id2label} member of a model configuration, the object that maps each
   * output index to its label in the {@code config.json} files that accompany classification
   * models. Keys and labels are decoded from their escapes, and a later entry for the same
   * key overwrites an earlier one.
   *
   * @param json The JSON text of the configuration. Blank text, with or without a leading
   *     byte order mark, is a configuration without labels.
   * @return The configuration, with an empty map if the configuration has no
   *     {@code id2label} member.
   * @throws IllegalArgumentException Thrown if {@code json} is {@code null}, if the text is
   *     not a single well-formed JSON object, if {@code id2label} is not an object, or if a
   *     label is not a string. The message names the offset or the key.
   */
  public static DocumentCategorizerConfig fromJson(String json) {
    if (json == null) {
      throw new IllegalArgumentException("json must not be null");
    }
    final Map<String, String> id2label = new HashMap<>();
    final String text = json.startsWith(BYTE_ORDER_MARK) ? json.substring(1) : json;
    if (!StringUtil.isBlank(text)) {
      final JsonScan.Member labels = JsonScan.member(JsonScan.document(text), ID_TO_LABEL_KEY);
      if (labels != null) {
        if (!JsonScan.isObject(text, labels)) {
          throw new IllegalArgumentException("\"" + ID_TO_LABEL_KEY + "\" must be an object: "
              + text.substring(labels.valueStart(), labels.valueEnd()));
        }
        for (JsonScan.Member entry : JsonScan.members(text, labels.valueStart())) {
          id2label.put(entry.key(), JsonScan.stringValue(text, entry));
        }
      }
    }
    return new DocumentCategorizerConfig(id2label);
  }
}
