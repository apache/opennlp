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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DocumentCategorizerConfig(Map<String, String> id2label) {

  private static final Pattern ID_TO_LABEL_PATTERN =
      Pattern.compile("\"id2label\"\\s*:\\s*\\{(.*?)\\}", Pattern.DOTALL);
  private static final Pattern ENTRY_PATTERN =
      Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"(.*?)\"");

  @Override
  public Map<String, String> id2label() {
    return Collections.unmodifiableMap(id2label);
  }

  public static DocumentCategorizerConfig fromJson(String json) {
    Objects.requireNonNull(json, "json must not be null");

    final Map<String, String> id2label = new HashMap<>();
    final Matcher matcher = ID_TO_LABEL_PATTERN.matcher(json);

    if (matcher.find()) {
      final String id2labelContent = matcher.group(1);
      final Matcher entryMatcher = ENTRY_PATTERN.matcher(id2labelContent);

      while (entryMatcher.find()) {
        final String key = entryMatcher.group(1);
        final String value = entryMatcher.group(2);
        id2label.put(key, value);
      }
    }

    return new DocumentCategorizerConfig(id2label);
  }
}
