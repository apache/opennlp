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

package opennlp.tools.stemmer.snowball;

import java.util.function.Supplier;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.util.OwnerOrPerThreadState;

/**
 * A {@link Stemmer} backed by the generated Snowball engines.
 *
 * <p>The generated engines hold mutable per-call buffers, so this class routes each call to a
 * per-thread engine via {@link OwnerOrPerThreadState}, the same pattern the thread-safe {@code *ME}
 * components use. A single {@code SnowballStemmer} instance is therefore safe to share across
 * threads.</p>
 */
@ThreadSafe
public class SnowballStemmer implements Stemmer {

  private final OwnerOrPerThreadState<AbstractSnowballStemmer> delegates;
  private final int repeat;

  /**
   * Creates a stemmer for the given algorithm and repeat count.
   *
   * @param algorithm The Snowball algorithm. Must not be {@code null}.
   * @param repeat    How many times to apply the stemmer per word; must be positive.
   * @throws IllegalArgumentException Thrown if {@code algorithm} is {@code null} or
   *     {@code repeat} is not positive.
   */
  public SnowballStemmer(ALGORITHM algorithm, int repeat) {
    if (algorithm == null) {
      throw new IllegalArgumentException("algorithm must not be null");
    }
    if (repeat <= 0) {
      throw new IllegalArgumentException("repeat must be positive, got " + repeat);
    }
    this.repeat = repeat;
    final Supplier<AbstractSnowballStemmer> engine = engineFor(algorithm);
    this.delegates = new OwnerOrPerThreadState<>(engine, stemmer -> { });
  }

  /**
   * Creates a stemmer for the given algorithm with {@code repeat = 1}.
   *
   * @param algorithm The Snowball algorithm. Must not be {@code null}.
   */
  public SnowballStemmer(ALGORITHM algorithm) {
    this(algorithm, 1);
  }

  // Shared with SnowballStemmerFactory, whose products embed one engine directly.
  static Supplier<AbstractSnowballStemmer> engineFor(ALGORITHM algorithm) {
    return switch (algorithm) {
      case ARABIC -> arabicStemmer::new;
      case DANISH -> danishStemmer::new;
      case DUTCH -> dutchStemmer::new;
      case CATALAN -> catalanStemmer::new;
      case ENGLISH -> englishStemmer::new;
      case FINNISH -> finnishStemmer::new;
      case FRENCH -> frenchStemmer::new;
      case GERMAN -> germanStemmer::new;
      case GREEK -> greekStemmer::new;
      case HUNGARIAN -> hungarianStemmer::new;
      case INDONESIAN -> indonesianStemmer::new;
      case IRISH -> irishStemmer::new;
      case ITALIAN -> italianStemmer::new;
      case NORWEGIAN -> norwegianStemmer::new;
      case PORTER -> porterStemmer::new;
      case PORTUGUESE -> portugueseStemmer::new;
      case ROMANIAN -> romanianStemmer::new;
      case RUSSIAN -> russianStemmer::new;
      case SPANISH -> spanishStemmer::new;
      case SWEDISH -> swedishStemmer::new;
      case TURKISH -> turkishStemmer::new;
    };
  }

  @Override
  public CharSequence stem(CharSequence word) {
    final AbstractSnowballStemmer stemmer = delegates.get();

    stemmer.setCurrent(word.toString());

    for (int i = 0; i < repeat; i++) {
      stemmer.stem();
    }

    return stemmer.getCurrent();
  }

  /**
   * Removes this thread's engine to prevent classloader leaks in container environments. Call
   * when the thread is returned to a pool or the stemmer is no longer needed, mirroring
   * {@code clearThreadLocalState()} on the thread-safe {@code *ME} components.
   */
  public void clearThreadLocalState() {
    delegates.clearForCurrentThread();
  }

  public enum ALGORITHM {
    ARABIC,
    DANISH,
    DUTCH,
    CATALAN,
    ENGLISH,
    FINNISH,
    FRENCH,
    GERMAN,
    GREEK,
    HUNGARIAN,
    INDONESIAN,
    IRISH,
    ITALIAN,
    NORWEGIAN,
    PORTER,
    PORTUGUESE,
    ROMANIAN,
    RUSSIAN,
    SPANISH,
    SWEDISH,
    TURKISH
  }
}
