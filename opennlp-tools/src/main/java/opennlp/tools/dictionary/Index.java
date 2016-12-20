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

package opennlp.tools.dictionary;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import opennlp.tools.util.StringList;

/**
 * This classes indexes {@link StringList}s. This makes it possible
 * to check if a certain token is contained in at least one of the
 * {@link StringList}s.
 */
public class Index {

  private Set<String> tokens = new HashSet<>();

  /**
   * Initializes the current instance with the given
   * {@link StringList} {@link Iterator}.
   *
   * @param tokenLists
   */
  public Index(Iterator<StringList> tokenLists) {

    while (tokenLists.hasNext()) {

      StringList tokens = tokenLists.next();

      for (int i = 0; i < tokens.size(); i++) {
        this.tokens.add(tokens.getToken(i));
      }
    }
  }

  /**
   * Checks if at leat one {@link StringList} contains the
   * given token.
   *
   * @param token
   *
   * @return true if the token is contained otherwise false.
   */
  public boolean contains(String token) {
    return tokens.contains(token);
  }
}
