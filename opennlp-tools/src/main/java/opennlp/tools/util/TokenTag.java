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


package opennlp.tools.util;

import java.util.Arrays;

public record TokenTag(String token, String tag, String[] additionalData) {

  public TokenTag(String token, String tag, String[] additionalData) {
    this.token = token;
    this.tag = tag;
    if (additionalData != null) {
      this.additionalData = Arrays.copyOf(additionalData, additionalData.length);
    } else {
      this.additionalData = null;
    }
  }

  @Deprecated(forRemoval = true)
  public String getToken() {
    return token;
  }

  @Deprecated(forRemoval = true)
  public String getTag() {
    return tag;
  }

  @Deprecated(forRemoval = true)
  public String[] getAdditionalData() {
    return additionalData;
  }

  public static String[] extractTokens(TokenTag[] tuples) {
    String[] tokens = new String[tuples.length];
    for (int i = 0; i < tuples.length; i++) {
      tokens[i] = tuples[i].token();
    }

    return tokens;
  }

  public static String[] extractTags(TokenTag[] tuples) {
    String[] tags = new String[tuples.length];
    for (int i = 0; i < tuples.length; i++) {
      tags[i] = tuples[i].tag();
    }

    return tags;
  }

  public static TokenTag[] create(String[] toks, String[] tags) {
    TokenTag[] tuples = new TokenTag[toks.length];
    for (int i = 0; i < toks.length; i++) {
      tuples[i] = new TokenTag(toks[i], tags[i], null);
    }
    return tuples;
  }

  @Override
  public String toString() {
    return token + "_" + tag;
  }
}
