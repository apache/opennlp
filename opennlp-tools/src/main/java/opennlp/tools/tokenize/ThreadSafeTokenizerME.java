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

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.Span;

/**
 * A thread-safe version of TokenizerME. Using it is completely transparent. You can use it in
 * a single-threaded context as well, it only incurs a minimal overhead.
 * <p>
 * Note, however, that this implementation uses a {@link ThreadLocal}. Although the implementation is
 * lightweight because the model is not duplicated, if you have many long-running threads,
 * you may run into memory problems.
 * </p>
 * <p>
 * Be careful when using this in a Jakarta EE application, for example.
 * </p>
 * The user is responsible for clearing the {@link ThreadLocal}.
 */
@ThreadSafe
public class ThreadSafeTokenizerME implements Tokenizer, AutoCloseable {

  private final TokenizerModel model;

  private final ThreadLocal<TokenizerME> threadLocal = new ThreadLocal<>();

  public ThreadSafeTokenizerME(TokenizerModel model) {
    super();
    this.model = model;
  }

  private TokenizerME getTokenizer() {
    TokenizerME tokenizer = threadLocal.get();
    if (tokenizer == null) {
      tokenizer = new TokenizerME(model);
      threadLocal.set(tokenizer);
    }
    return tokenizer;
  }

  @Override
  public String[] tokenize(String s) {
    return getTokenizer().tokenize(s);
  }

  @Override
  public Span[] tokenizePos(String s) {
    return getTokenizer().tokenizePos(s);
  }

  public double[] getProbabilities() {
    return getTokenizer().getTokenProbabilities();
  }

  @Override
  public void close() {
    threadLocal.remove();
  }
}
