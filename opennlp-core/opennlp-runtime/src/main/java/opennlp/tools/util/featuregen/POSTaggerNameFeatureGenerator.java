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

import java.util.Arrays;
import java.util.List;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagFormatMapper;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;

/**
 * Adds the token POS tag as feature. Requires a {@link POSTagger} at runtime.
 * <p>
 * The "is this still the same sentence?" cache (a {@code String[]} of POS tags reused across the
 * tokens of one sentence) is held per-thread via {@link ThreadLocal}, so a single instance is safe
 * to share across multiple threads — for example when the enclosing
 * {@link opennlp.tools.namefind.NameFinderME} is shared. Cache identity uses
 * {@link Arrays#equals(Object[], Object[])} (content equality), preserving the original
 * single-threaded semantics.
 *
 * @see AdaptiveFeatureGenerator
 * @see POSTagger
 * @see POSModel
 */
@ThreadSafe
public class POSTaggerNameFeatureGenerator implements AdaptiveFeatureGenerator {

  private final POSTagger posTagger;

  private static final class CacheState {
    private String[] cachedTokens;
    private String[] cachedTags;
  }

  /** Per-thread sentence cache; the previous {@code String[] cachedTokens / cachedTags} fields raced
   *  under concurrent {@code find()} calls when the enclosing {@code NameFinderME} was shared. */
  private final ThreadLocal<CacheState> threadState = ThreadLocal.withInitial(CacheState::new);

  /**
   * Initializes a {@link POSTaggerNameFeatureGenerator} with the specified {@link POSTagger}.
   *
   * @param aPosTagger A POSTagger instance to be used.
   */
  public POSTaggerNameFeatureGenerator(POSTagger aPosTagger) {
    this.posTagger = aPosTagger;
  }

  /**
   * Initializes a {@link POSTaggerNameFeatureGenerator} with the specified {@link POSModel}.
   *
   * @param aPosModel A {@link POSModel} to be used for the internal {@link POSTagger}.
   */
  public POSTaggerNameFeatureGenerator(POSModel aPosModel) {
    this.posTagger = new POSTaggerME(aPosModel, POSTagFormatMapper.guessFormat(aPosModel));
  }

  @Override
  public void createFeatures(List<String> feats, String[] toks, int index, String[] preds) {
    CacheState state = threadState.get();
    if (!Arrays.equals(state.cachedTokens, toks)) {
      state.cachedTokens = toks;
      state.cachedTags = posTagger.tag(toks);
    }

    feats.add("pos=" + state.cachedTags[index]);
  }

}
