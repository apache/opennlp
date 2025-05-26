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
 * This classes indexes {@link StringList string lists}. This makes it possible
 * to check if a certain token is contained in at least one of the
 * {@link StringList}s.
 */
public class Index {

  private final Set<String> tokens = new HashSet<>();

  /**
   * Initializes an {@link Index} with the given {@link Iterator}
   * over {@link StringList} elements.
   *
   * @param tokenLists The iterable {@link StringList} elements.
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
   * Checks if at least one {@link StringList} contains the specified {@code token}.
   *
   * @param token The element to check for.
   *
   * @return {@code true} if the token is contained, {@code false} otherwise.
   */
  public boolean contains(String token) {
    return tokens.contains(token);
  }
}
