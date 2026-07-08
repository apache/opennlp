/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.stemmer;

import java.util.List;

/**
 * Reduces a word to its root form.
 *
 * <p>Thread safety is implementation-specific: check the implementation's documentation before
 * sharing an instance across threads. Stateful engines mutate internal buffers on each
 * {@link #stem(CharSequence)} call; when an implementation is not documented as thread-safe,
 * share a {@link StemmerFactory} across threads and confine each {@code Stemmer} to one thread,
 * or wrap the factory in a thread-local adapter when a single {@code Stemmer} reference must be
 * shared.</p>
 */
public interface Stemmer {

  /**
   * Stems {@code word}.
   *
   * @param word The input word. Must not be {@code null}.
   * @return The stemmed form.
   */
  CharSequence stem(CharSequence word);

  /**
   * {@return every stem form for {@code word}} The default returns a single-element list with
   * {@link #stem(CharSequence)}. Multi-output engines (Hunspell, lemmatizers) override this.
   *
   * @param word The input word. Must not be {@code null}.
   */
  default List<CharSequence> stemAll(CharSequence word) {
    return List.of(stem(word));
  }
}
