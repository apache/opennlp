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
import java.util.Objects;

public class TokenTag {

  private final String token;
  private final String tag;
  private final String[] addtionalData;

  public TokenTag(String token, String tag, String[] addtionalData) {
    this.token = token;
    this.tag = tag;
    if (addtionalData != null) {
      this.addtionalData = Arrays.copyOf(addtionalData, addtionalData.length);
    } else {
      this.addtionalData = null;
    }
  }

  public String getToken() {
    return token;
  }

  public String getTag() {
    return tag;
  }

  public String[] getAddtionalData() {
    return addtionalData;
  }

  public static String[] extractTokens(TokenTag[] tuples) {
    String[] tokens = new String[tuples.length];
    for (int i = 0; i < tuples.length; i++) {
      tokens[i] = tuples[i].getToken();
    }

    return tokens;
  }

  public static String[] extractTags(TokenTag[] tuples) {
    String[] tags = new String[tuples.length];
    for (int i = 0; i < tuples.length; i++) {
      tags[i] = tuples[i].getTag();
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof TokenTag) {
      return Objects.equals(this.token, ((TokenTag) o).token)
          && Objects.equals(this.tag, ((TokenTag) o).tag)
          && Objects.equals(this.addtionalData, ((TokenTag) o).addtionalData);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(token, tag, addtionalData);
  }

  @Override
  public String toString() {
    return token + "_" + tag;
  }
}
