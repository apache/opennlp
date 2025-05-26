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
import opennlp.tools.models.ModelType;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.Sequence;

/**
 * A thread-safe version of the {@link POSTaggerME}. Using it is completely transparent.
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
 * @see POSTagger
 * @see POSTaggerME
 */
@ThreadSafe
public class ThreadSafePOSTaggerME implements POSTagger, AutoCloseable {

  private final POSModel model;

  private final POSTagFormat posTagFormat;

  private final ThreadLocal<POSTaggerME> threadLocal = new ThreadLocal<>();

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
    this.model = model;
    this.posTagFormat = format;
  }

  private POSTaggerME getTagger() {
    POSTaggerME tagger = threadLocal.get();
    if (tagger == null) {
      tagger = new POSTaggerME(model, posTagFormat);
      threadLocal.set(tagger);
    }
    return tagger;
  }

  @Override
  public String[] tag(String[] sentence) {
    return getTagger().tag(sentence);
  }

  @Override
  public String[] tag(String[] sentence, Object[] additionaContext) {
    return getTagger().tag(sentence, additionaContext);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence) {
    return getTagger().topKSequences(sentence);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence, Object[] additionaContext) {
    return getTagger().topKSequences(sentence, additionaContext);
  }

  @Override
  public void close() {
    threadLocal.remove();
  }
}
