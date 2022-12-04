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


package opennlp.tools.postag;

/**
 * Interface to determine which tags are valid for a particular word
 * based on a tag dictionary.
 */
public interface TagDictionary {

  /**
   * Retrieves a list of valid tags for the specified {@code word}.
   *
   * @param word The word.
   * @return An array of valid tags for the specified {@code word} or {@code null} if
   *         no information is available for that word.
   */
  String[] getTags(String word);

  /**
   * Whether the dictionary is case-sensitive or not.
   *
   * @return {@code true} if the dictionary is case-sensitive, else {@code false}.
   */
  boolean isCaseSensitive();
}
