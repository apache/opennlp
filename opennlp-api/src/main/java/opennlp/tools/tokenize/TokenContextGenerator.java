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

package opennlp.tools.tokenize;

/**
 * Interface for context generators required for
 * {@link Tokenizer tokenizer implementations}.
 */
public interface TokenContextGenerator {

  /**
   * @param sentence The string that represents a sentence.
   * @param index The index to consider splitting tokens.
   *
   * @return An array of features for a {@code sentence} at the specified {@code index}.
   */
  String[] getContext(String sentence, int index);
}
