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

package opennlp.tools.stemmer;

import java.util.List;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.OwnerOrPerThreadState;

/**
 * A {@link Stemmer} that is safe to share across threads by routing each call to a per-thread
 * delegate minted from a {@link StemmerFactory}. Use it to share a stemmer whose implementation is
 * <em>not</em> itself thread-safe, such as {@link PorterStemmer}; thread-safe implementations like
 * {@code SnowballStemmer} do not need this wrapper.
 *
 * <p>The first thread uses a single owner delegate without touching {@link ThreadLocal} until a
 * second thread appears, matching the {@link OwnerOrPerThreadState} pattern used by the
 * thread-safe {@code *ME} components.</p>
 */
@ThreadSafe
public final class SharingStemmer extends DelegatingStemmer<Stemmer> {

  /**
   * @param factory The factory that mints per-thread delegates. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code factory} is {@code null}.
   */
  public SharingStemmer(StemmerFactory factory) {
    super(requireFactory(factory)::newStemmer, stemmer -> { });
  }

  @Override
  public CharSequence stem(CharSequence word) {
    return state.get().stem(word);
  }

  @Override
  public List<CharSequence> stemAll(CharSequence word) {
    return state.get().stemAll(word);
  }
}
