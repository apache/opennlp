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

import java.io.IOException;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.ml.Probabilistic;
import opennlp.tools.models.ModelType;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.Sequence;

/**
 * A thread-safe version of the {@link POSTaggerME}. Using it is completely transparent.
 * You can use it in a single-threaded context as well, it only incurs a minimal overhead.
 * <p>
 * <b>Note:</b> This class is deprecated and now delegates to a single shared
 * {@link POSTaggerME} instance, which is thread-safe as of OPENNLP-1816.
 * Calling {@link #close()} clears the current thread's thread-local state for compatibility.
 *
 * @see POSTagger
 * @see POSTaggerME
 * @see Probabilistic
 *
 * @deprecated As of OPENNLP-1816, {@link POSTaggerME} is
 *     itself thread-safe. Use it directly instead.
 */
@Deprecated(since = "3.0.0")
@ThreadSafe
public class ThreadSafePOSTaggerME implements POSTagger, Probabilistic, AutoCloseable {

  private final POSTaggerME sharedTagger;

  /**
   * Initializes a {@link ThreadSafePOSTaggerME} by downloading a default model for a given
   * {@code language}.
   *
   * @param language An ISO conform language code.
   * @throws IOException Thrown if the model could not be downloaded or saved.
   */
  public ThreadSafePOSTaggerME(String language) throws IOException {
    this(language, POSTagFormat.UD);
  }

  /**
   * Initializes a {@link ThreadSafePOSTaggerME} by downloading a default model
   * for a given {@code language}.
   *
   * @param language An ISO conform language code.
   * @param format   A valid {@link POSTagFormat}.
   * @throws IOException Thrown if the model could not be downloaded or saved.
   */
  public ThreadSafePOSTaggerME(String language, POSTagFormat format) throws IOException {
    this(DownloadUtil.downloadModel(language, ModelType.POS, POSModel.class), format);
  }

  /**
   * Initializes a {@link ThreadSafePOSTaggerME} with the specified {@code model}.
   *
   * @param model A valid {@link POSModel}.
   */
  public ThreadSafePOSTaggerME(POSModel model) {
    this(model, POSTagFormat.UD);
  }

  /**
   * Initializes a {@link ThreadSafePOSTaggerME} with the specified {@link POSModel model}.
   *
   * @param model  A valid {@link POSModel}.
   * @param format A valid {@link POSTagFormat}.
   */
  public ThreadSafePOSTaggerME(POSModel model, POSTagFormat format) {
    super();
    this.sharedTagger = new POSTaggerME(model, format);
  }

  @Override
  public String[] tag(String[] sentence) {
    return sharedTagger.tag(sentence);
  }

  @Override
  public String[] tag(String[] sentence, Object[] additionaContext) {
    return sharedTagger.tag(sentence, additionaContext);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence) {
    return sharedTagger.topKSequences(sentence);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence, Object[] additionaContext) {
    return sharedTagger.topKSequences(sentence, additionaContext);
  }

  @Override
  public double[] probs() {
    return sharedTagger.probs();
  }

  @Override
  public void close() {
    sharedTagger.clearThreadLocalState();
  }
}
