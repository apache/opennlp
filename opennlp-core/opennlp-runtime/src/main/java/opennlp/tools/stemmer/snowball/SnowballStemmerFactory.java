/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;

/**
 * A thread-safe factory that captures a Snowball stemmer configuration for APIs that accept a
 * {@link StemmerFactory}.
 *
 * <p>Use this factory for the classic one-stemmer-per-thread pattern: {@link #newStemmer()}
 * returns a plain, thread-confined stemmer without the per-call thread routing of the
 * shareable {@link SnowballStemmer}.</p>
 */
@ThreadSafe
public class SnowballStemmerFactory implements StemmerFactory {

  private final SnowballStemmer.ALGORITHM algorithm;
  private final int repeat;

  /**
   * Creates a factory with {@code repeat = 1}.
   *
   * @param algorithm The Snowball algorithm. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code algorithm} is {@code null}.
   */
  public SnowballStemmerFactory(SnowballStemmer.ALGORITHM algorithm) {
    this(algorithm, 1);
  }

  /**
   * Creates a factory over the given algorithm and repeat count.
   *
   * @param algorithm The Snowball algorithm. Must not be {@code null}.
   * @param repeat    How many times to apply the stemmer per word; must be positive.
   * @throws IllegalArgumentException if {@code algorithm} is {@code null} or {@code repeat}
   *     is not positive.
   */
  public SnowballStemmerFactory(SnowballStemmer.ALGORITHM algorithm, int repeat) {
    if (algorithm == null) {
      throw new IllegalArgumentException("algorithm must not be null");
    }
    if (repeat <= 0) {
      throw new IllegalArgumentException("repeat must be positive, got " + repeat);
    }
    this.algorithm = algorithm;
    this.repeat = repeat;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Stemmer newStemmer() {
    return new ConfinedStemmer(SnowballStemmer.engineFor(algorithm).get(), repeat);
  }

  /** A plain, thread-confined stemmer that applies one Snowball engine directly; not thread-safe. */
  private static final class ConfinedStemmer implements Stemmer {

    private final AbstractSnowballStemmer engine;
    private final int repeat;

    /**
     * Creates a thread-confined stemmer over the given engine.
     *
     * @param engine The Snowball engine to apply.
     * @param repeat How many times to apply the engine per word.
     */
    private ConfinedStemmer(AbstractSnowballStemmer engine, int repeat) {
      this.engine = engine;
      this.repeat = repeat;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code word} is {@code null}.
     */
    @Override
    public CharSequence stem(CharSequence word) {
      if (word == null) {
        throw new IllegalArgumentException("word must not be null");
      }
      engine.setCurrent(word.toString());
      for (int i = 0; i < repeat; i++) {
        engine.stem();
      }
      return engine.getCurrent();
    }
  }
}
