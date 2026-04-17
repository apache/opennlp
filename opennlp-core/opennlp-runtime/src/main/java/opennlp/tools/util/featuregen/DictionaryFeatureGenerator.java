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


package opennlp.tools.util.featuregen;

import java.util.List;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.namefind.DictionaryNameFinder;

/**
 * The {@link DictionaryFeatureGenerator} uses a {@link DictionaryNameFinder}
 * to generate features for detected names based on the {@link InSpanGenerator}.
 * <p>
 * <b>Thread safety:</b> in normal use the underlying {@link InSpanGenerator} is configured once at
 * construction time and never replaced, after which {@link #createFeatures} is safe to call from
 * many threads concurrently (it delegates to a thread-safe {@code InSpanGenerator}). The {@code isg}
 * field is {@code volatile} to give safe publication for both that one-shot constructor write and
 * any later {@link #setDictionary(Dictionary) setDictionary} call.
 * <p>
 * {@link #setDictionary(Dictionary) setDictionary} replaces the in-flight dictionary; it is intended
 * for setup time and is <b>not</b> for hot-path use while {@link #createFeatures} is running on
 * other threads — it does not coordinate with in-flight reads beyond the volatile publish, so a
 * caller racing dictionary replacement against feature generation may observe either the old or the
 * new dictionary's features for any given call.
 *
 * @see Dictionary
 * @see DictionaryNameFinder
 * @see InSpanGenerator
 */
@ThreadSafe
public class DictionaryFeatureGenerator implements AdaptiveFeatureGenerator {

  private volatile InSpanGenerator isg;

  /**
   * Initializes a {@link DictionaryFeatureGenerator} with the specified {@link Dictionary}.
   *
   * @param dict The {@link Dictionary} to use. Must not be {@code null}.
   */
  public DictionaryFeatureGenerator(Dictionary dict) {
    this("", dict);
  }

  /**
   * Initializes a {@link DictionaryFeatureGenerator} with the specified parameters.
   *
   * @param prefix The prefix to set. Must not be {@code null} but may be empty.
   * @param dict The {@link Dictionary} to use. Must not be {@code null}.
   */
  public DictionaryFeatureGenerator(String prefix, Dictionary dict) {
    setDictionary(prefix, dict);
  }

  public void setDictionary(Dictionary dict) {
    setDictionary("", dict);
  }

  public void setDictionary(String name, Dictionary dict) {
    isg = new InSpanGenerator(name, new DictionaryNameFinder(dict));
  }

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index, String[] previousOutcomes) {
    final InSpanGenerator current = isg;
    current.createFeatures(features, tokens, index, previousOutcomes);
  }

}
