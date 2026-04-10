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

package opennlp.tools.namefind;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.ml.Probabilistic;
import opennlp.tools.util.Span;

/**
 * A thread-safe version of {@link NameFinderME}. Using it is completely transparent.
 * You can use it in a single-threaded context as well, it only incurs a minimal overhead.
 * <p>
 * <b>Note:</b> This class is deprecated and now delegates to a single shared
 * {@link NameFinderME} instance, which is thread-safe as of OPENNLP-1816.
 * Calling {@link #close()} clears the current thread's thread-local state for compatibility.
 *
 * @see NameFinderME
 * @see Probabilistic
 * @see TokenNameFinder
 *
 * @deprecated As of OPENNLP-1816, {@link NameFinderME} is
 *     itself thread-safe. Use it directly instead.
 */
@Deprecated(since = "3.0.0")
@ThreadSafe
public class ThreadSafeNameFinderME implements TokenNameFinder, Probabilistic, AutoCloseable {

  private final NameFinderME sharedNameFinder;

  /**
   * Initializes a {@link ThreadSafeNameFinderME} with the specified {@code model}.
   *
   * @param model A valid {@link TokenNameFinderModel}.
   */
  public ThreadSafeNameFinderME(TokenNameFinderModel model) {
    super();
    this.sharedNameFinder = new NameFinderME(model);
  }

  @Override
  public Span[] find(String[] tokens) {
    return sharedNameFinder.find(tokens);
  }

  @Override
  public double[] probs() {
    return sharedNameFinder.probs();
  }

  @Override
  public void clearAdaptiveData() {
    sharedNameFinder.clearAdaptiveData();
  }

  @Override
  public void close() {
    sharedNameFinder.clearThreadLocalState();
  }
}
