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

import java.util.Objects;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;

/**
 * A thread-safe factory for {@link SnowballStemmer} instances. Since {@link SnowballStemmer} is
 * itself thread-safe, this factory mainly serves as an immutable capture of the stemmer
 * configuration for APIs that accept a {@link StemmerFactory}.
 *
 * @param algorithm The Snowball algorithm. Must not be {@code null}.
 * @param repeat    How many times to apply the stemmer per word; must be positive.
 */
@ThreadSafe
public record SnowballStemmerFactory(SnowballStemmer.ALGORITHM algorithm, int repeat)
    implements StemmerFactory {

  /**
   * Creates a factory with {@code repeat = 1}.
   *
   * @param algorithm The Snowball algorithm. Must not be {@code null}.
   */
  public SnowballStemmerFactory(SnowballStemmer.ALGORITHM algorithm) {
    this(algorithm, 1);
  }

  /**
   * Validates the components.
   *
   * @throws NullPointerException if {@code algorithm} is {@code null}.
   * @throws IllegalArgumentException if {@code repeat} is not positive.
   */
  public SnowballStemmerFactory {
    Objects.requireNonNull(algorithm, "algorithm");
    if (repeat <= 0) {
      throw new IllegalArgumentException("repeat must be positive");
    }
  }

  @Override
  public Stemmer newStemmer() {
    return new SnowballStemmer(algorithm, repeat);
  }
}
