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

package opennlp.tools.langdetect;

import opennlp.tools.commons.ThreadSafe;

/**
 * A thread-safe version of the {@link LanguageDetectorME}. Using it is completely transparent.
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
 * @see LanguageDetector
 * @see LanguageDetectorME
 */
@ThreadSafe
public class ThreadSafeLanguageDetectorME implements LanguageDetector, AutoCloseable {

  private final LanguageDetectorModel model;

  private final ThreadLocal<LanguageDetectorME> threadLocal = new ThreadLocal<>();

  /**
   * Initializes a {@link ThreadSafeLanguageDetectorME} with the specified {@code model}.
   *
   * @param model A valid {@link LanguageDetectorModel}.
   */
  public ThreadSafeLanguageDetectorME(LanguageDetectorModel model) {
    super();
    this.model = model;
  }

  private LanguageDetectorME getLanguageDetector() {
    LanguageDetectorME ld = threadLocal.get();
    if (ld == null) {
      ld = new LanguageDetectorME(model);
      threadLocal.set(ld);
    }
    return ld;
  }

  @Override
  public Language[] predictLanguages(CharSequence content) {
    return getLanguageDetector().predictLanguages(content);
  }

  @Override
  public Language predictLanguage(CharSequence content) {
    return getLanguageDetector().predictLanguage(content);
  }

  @Override
  public String[] getSupportedLanguages() {
    return getLanguageDetector().getSupportedLanguages();
  }

  @Override
  public void close() {
    threadLocal.remove();
  }

}
