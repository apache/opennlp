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
import opennlp.tools.util.Span;

/**
 * A thread-safe version of {@link NameFinderME}. Using it is completely transparent.
 * You can use it in a single-threaded context as well, it only incurs a minimal overhead.
 *
 * @implNote
 * This implementation uses a {@link ThreadLocal}. Although the implementation is
 * lightweight because the model is not duplicated, if you have many long-running threads,
 * you may run into memory problems.
 * <p>
 * Be careful when using this in a Jakarta EE application, for example.
 * </p>
 * The user is responsible for clearing the {@link ThreadLocal}.
 *
 * @see NameFinderME
 * @see TokenNameFinder
 */
@ThreadSafe
public class ThreadSafeNameFinderME implements TokenNameFinder, AutoCloseable {

  private final TokenNameFinderModel model;

  private final ThreadLocal<NameFinderME> threadLocal = new ThreadLocal<>();

  /**
   * Initializes a {@link ThreadSafeNameFinderME} with the specified {@code model}.
   *
   * @param model A valid {@link TokenNameFinderModel}.
   */
  public ThreadSafeNameFinderME(TokenNameFinderModel model) {
    super();
    this.model = model;
  }

  // If a thread-local version exists, return it. Otherwise, create, then return.
  private NameFinderME getNameFinder() {
    NameFinderME nf = threadLocal.get();
    if (nf == null) {
      nf = new NameFinderME(model);
      threadLocal.set(nf);
    }
    return nf;
  }

  @Override
  public Span[] find(String[] tokens) {
    return getNameFinder().find(tokens);
  }

  @Override
  public void clearAdaptiveData() {
    getNameFinder().clearAdaptiveData();
  }

  @Override
  public void close() {
    threadLocal.remove();
  }
}
