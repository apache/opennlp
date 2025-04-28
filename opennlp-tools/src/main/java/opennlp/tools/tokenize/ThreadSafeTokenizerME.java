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

import java.io.IOException;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.models.ModelType;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.Span;

/**
 * A thread-safe version of {@link TokenizerME}. Using it is completely transparent.
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
 * @see Tokenizer
 * @see TokenizerME
 */
@ThreadSafe
public class ThreadSafeTokenizerME implements Tokenizer, AutoCloseable {

  private final TokenizerModel model;
  private final Dictionary abbDict;

  private final ThreadLocal<TokenizerME> threadLocal = new ThreadLocal<>();

  /**
   * Initializes a {@link ThreadSafeTokenizerME} by downloading a default model
   * for a given {@code language}.
   *
   * @param language An ISO conform language code.
   * @throws IOException Thrown if the model could not be downloaded or saved.
   */
  public ThreadSafeTokenizerME(String language) throws IOException {
    this(DownloadUtil.downloadModel(language, ModelType.TOKENIZER, TokenizerModel.class));
  }

  /**
   * Initializes a {@link ThreadSafeTokenizerME} with the specified {@code model}.
   *
   * @param model A valid {@link TokenizerModel}.
   */
  public ThreadSafeTokenizerME(TokenizerModel model) {
    this(model, model.getAbbreviations());
  }

  /**
   * Instantiates a {@link ThreadSafeTokenizerME} with an existing {@link TokenizerModel}.
   *
   * @param model The {@link TokenizerModel} to be used.
   * @param abbDict The {@link Dictionary} to be used. It must fit the language of the {@code model}.
   */
  public ThreadSafeTokenizerME(TokenizerModel model, Dictionary abbDict) {
    this.model = model;
    this.abbDict = abbDict;
  }

  private TokenizerME getTokenizer() {
    TokenizerME tokenizer = threadLocal.get();
    if (tokenizer == null) {
      tokenizer = new TokenizerME(model, abbDict);
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
