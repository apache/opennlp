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
 * Interface that allows {@link TagDictionary} entries to be added and removed.
 * This can be used to induce the dictionary from training data.
 */
public interface MutableTagDictionary extends TagDictionary {

  /**
   * Associates the specified tags with the specified word. If the dictionary
   * previously contained keys for the word, the old tags are replaced by the
   * specified tags.
   *
   * @param word
   *          word with which the specified tags is to be associated
   * @param tags
   *          tags to be associated with the specified word
   *
   * @return the previous tags associated with the word, or null if there was no
   *         mapping for word.
   */
  String[] put(String word, String... tags);

  /**
   * Whether if the dictionary is case sensitive or not
   *
   * @return true if the dictionary is case sensitive
   */
  // TODO: move to TagDictionary, can't do it now because of backward
  // compatibility.
  boolean isCaseSensitive();

}
