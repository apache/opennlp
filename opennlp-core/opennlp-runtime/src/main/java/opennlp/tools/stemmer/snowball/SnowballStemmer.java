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
import opennlp.tools.stemmer.SharingStemmer;
import opennlp.tools.stemmer.Stemmer;

/**
 * A {@link Stemmer} backed by the generated Snowball engines.
 *
 * <p>The generated engines hold mutable per-call buffers, so this class routes each call to a
 * per-thread engine, the same pattern the thread-safe {@code *ME} components use. A single
 * {@code SnowballStemmer} instance is therefore safe to share across threads.</p>
 *
 * <p>As with the {@code *ME} components, long-running environments such as application
 * containers should call {@link #clearThreadLocalState()} when a pooled thread no longer uses
 * this stemmer; otherwise the thread retains its engine until the instance is unreachable,
 * which can pin the defining classloader on redeploys.</p>
 */
@ThreadSafe
public class SnowballStemmer implements Stemmer {

  // All per-thread routing is delegated, so the pattern lives in exactly one class.
  private final SharingStemmer sharing;

  /**
   * Creates a stemmer for the given algorithm and repeat count.
   *
   * @param algorithm The Snowball algorithm. Must not be {@code null}.
   * @param repeat    How many times to apply the stemmer per word; must be positive.
   * @throws IllegalArgumentException Thrown if {@code algorithm} is {@code null} or
   *     {@code repeat} is not positive.
   */
  public SnowballStemmer(ALGORITHM algorithm, int repeat) {
    this.sharing = new SharingStemmer(new SnowballStemmerFactory(algorithm, repeat));
  }

  /**
   * Creates a stemmer for the given algorithm with {@code repeat = 1}.
   *
   * @param algorithm The Snowball algorithm. Must not be {@code null}.
   */
  public SnowballStemmer(ALGORITHM algorithm) {
    this(algorithm, 1);
  }

  /**
   * Returns a supplier of a fresh Snowball engine for the given algorithm, shared with
   * {@link SnowballStemmerFactory} whose products embed one engine directly.
   *
   * @param algorithm The Snowball algorithm.
   * @return a supplier that mints a new engine on each call.
   */
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

  /**
   * {@inheritDoc}
   */
  @Override
  public CharSequence stem(CharSequence word) {
    return sharing.stem(word);
  }

  /**
   * Removes this thread's engine to prevent classloader leaks in container environments. Call
   * when the thread is returned to a pool or the stemmer is no longer needed, mirroring
   * {@code clearThreadLocalState()} on the thread-safe {@code *ME} components.
   */
  public void clearThreadLocalState() {
    sharing.clearThreadLocalState();
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
