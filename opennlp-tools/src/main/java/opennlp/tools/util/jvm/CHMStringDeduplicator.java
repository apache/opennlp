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
package opennlp.tools.util.jvm;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import opennlp.tools.commons.Internal;
import opennlp.tools.commons.ThreadSafe;

/**
 * A {@link StringInterner} implementation by Aleksey Shipilëv with relaxed canonical requirements.
 * It is a probabilistic deduplication implementation with a default probability of {@code 0.5}.
 * Users may implement a custom class with a different probability value.
 * <p>
 * Origin:
 * <a href="https://shipilev.net/talks/joker-Oct2014-string-catechism.pdf">
 * https://shipilev.net/talks/joker-Oct2014-string-catechism.pdf</a>
 */
@Internal
@ThreadSafe
class CHMStringDeduplicator implements StringInterner {
  private final int prob;
  private final Map<String, String> map;

  /**
   * Creates a {@link CHMStringDeduplicator} with a probability of {@code 0.5}.
   */
  public CHMStringDeduplicator() {
    this(0.5);
  }

  /**
   * @param prob represents the probability, that a given String will be interned
   */
  public CHMStringDeduplicator(double prob) {
    this.prob = (int) (Integer.MIN_VALUE + prob * (1L << 32));
    this.map = new ConcurrentHashMap<>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String intern(String sample) {
    if (ThreadLocalRandom.current().nextInt() > prob) {
      return sample;
    }
    final String exist = map.putIfAbsent(sample, sample);
    return (exist == null) ? sample : exist;
  }
}
