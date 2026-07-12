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

package opennlp.tools.stemmer;

import java.util.function.Consumer;
import java.util.function.Supplier;

import opennlp.tools.util.OwnerOrPerThreadState;

/**
 * Shared plumbing for {@link Stemmer} wrappers that route each call to a per-thread payload minted
 * from a {@link StemmerFactory}, following the {@link OwnerOrPerThreadState} pattern of the
 * thread-safe {@code *ME} components. Subclasses differ only in the payload they carry per thread
 * and in what {@link #stem(CharSequence)} does with it.
 *
 * @param <P> the per-thread payload: a bare delegate stemmer, or a delegate paired with per-thread
 *     working state such as a cache.
 */
abstract class DelegatingStemmer<P> implements Stemmer {

  /** The per-thread payload holder. */
  final OwnerOrPerThreadState<P> state;

  /**
   * @param init    Mints the per-thread payload on first use by the owner and by each extra thread.
   * @param cleanup Releases a per-thread payload when its thread clears state.
   */
  DelegatingStemmer(Supplier<P> init, Consumer<P> cleanup) {
    this.state = new OwnerOrPerThreadState<>(init, cleanup);
  }

  /**
   * Validates a factory argument before it is captured by a subclass supplier, so a {@code null}
   * factory fails at construction rather than on first use.
   *
   * @param factory The factory to validate. Must not be {@code null}.
   * @return {@code factory}.
   * @throws IllegalArgumentException if {@code factory} is {@code null}.
   */
  static StemmerFactory requireFactory(StemmerFactory factory) {
    if (factory == null) {
      throw new IllegalArgumentException("factory must not be null");
    }
    return factory;
  }

  /**
   * Removes this thread's payload to prevent classloader leaks in container environments. Call
   * when the thread is returned to a pool or the stemmer is no longer needed, mirroring
   * {@code clearThreadLocalState()} on the thread-safe {@code *ME} components.
   */
  public void clearThreadLocalState() {
    state.clearForCurrentThread();
  }
}
