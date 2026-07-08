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

/**
 * An immutable, thread-safe factory for {@link Stemmer} instances.
 *
 * <p>Generated and stateful stemmers (Snowball, Porter, Hunspell, and similar) hold per-call
 * mutable buffers and must not be shared across threads. A {@code StemmerFactory} captures the
 * configuration (algorithm, repeat count, dictionary path, ...) and mints a fresh {@link Stemmer}
 * on each {@link #newStemmer()} call. The factory itself is safe to share; confine each returned
 * {@link Stemmer} to a single thread, or route through a thread-local adapter when a component must
 * be shared.</p>
 *
 * <p>Implementations must be immutable and thread-safe after construction.</p>
 */
public interface StemmerFactory {

  /**
   * {@return a new {@link Stemmer} confined to the calling thread} Implementations must return a
   * distinct instance on each call when the underlying engine is stateful.
   */
  Stemmer newStemmer();

  /**
   * Stems one word using a freshly minted {@link Stemmer}.
   *
   * <p>This is a convenience for one-off use. Pipelines that stem many tokens should call
   * {@link #newStemmer()} once per thread instead of invoking this
   * method per token.</p>
   *
   * @param word The input word. Must not be {@code null}.
   * @return The stemmed form.
   */
  default CharSequence stem(CharSequence word) {
    return newStemmer().stem(word);
  }
}
