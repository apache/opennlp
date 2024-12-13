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

package opennlp.tools.lemmatizer;

import java.util.List;

import opennlp.tools.commons.ThreadSafe;

/**
 * A thread-safe version of the {@link LemmatizerME}. Using it is completely transparent.
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
 * @see Lemmatizer
 * @see LemmatizerME
 */
@ThreadSafe
public class ThreadSafeLemmatizerME implements Lemmatizer, AutoCloseable {

  private final LemmatizerModel model;

  private final ThreadLocal<LemmatizerME> threadLocal = new ThreadLocal<>();

  /**
   * Initializes a {@link ThreadSafeLemmatizerME} with the specified {@code model}.
   *
   * @param model A valid {@link LemmatizerModel}.
   */
  public ThreadSafeLemmatizerME(LemmatizerModel model) {
    super();
    this.model = model;
  }

  private LemmatizerME getLemmatizer() {
    LemmatizerME l = threadLocal.get();
    if (l == null) {
      l = new LemmatizerME(model);
      threadLocal.set(l);
    }
    return l;
  }

  @Override
  public String[] lemmatize(String[] toks, String[] tags) {
    return getLemmatizer().lemmatize(toks, tags);
  }

  @Override
  public List<List<String>> lemmatize(List<String> toks, List<String> tags) {
    return getLemmatizer().lemmatize(toks, tags);
  }

  @Override
  public void close() {
    threadLocal.remove();
  }

}
