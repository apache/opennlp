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
import java.util.regex.Pattern;

import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.StringUtil;

/**
 * Partitions tokens into sub-tokens based on character classes and generates
 * class features for each of the sub-tokens and combinations of those sub-tokens.
 */
public class TokenPatternFeatureGenerator implements AdaptiveFeatureGenerator {

  private Pattern noLetters = Pattern.compile("[^a-zA-Z]");
  private Tokenizer tokenizer;

  /**
   * Initializes a new instance.
   * For tokinization the {@link SimpleTokenizer} is used.
   */
  public TokenPatternFeatureGenerator() {
      this(SimpleTokenizer.INSTANCE);
  }

  /**
   * Initializes a new instance.
   *
   * @param supportTokenizer
   */
  public TokenPatternFeatureGenerator(Tokenizer supportTokenizer) {
    tokenizer = supportTokenizer;
  }

  public void createFeatures(List<String> feats, String[] toks, int index, String[] preds) {

    String[] tokenized = tokenizer.tokenize(toks[index]);

    if (tokenized.length == 1) {
      feats.add("st=" + StringUtil.toLowerCase(toks[index]));
      return;
    }

    feats.add("stn=" + tokenized.length);

    StringBuilder pattern = new StringBuilder();

    for (int i = 0; i < tokenized.length; i++) {

      if (i < tokenized.length - 1) {
        feats.add("pt2=" + FeatureGeneratorUtil.tokenFeature(tokenized[i]) +
            FeatureGeneratorUtil.tokenFeature(tokenized[i + 1]));
      }

      if (i < tokenized.length - 2) {
        feats.add("pt3=" + FeatureGeneratorUtil.tokenFeature(tokenized[i]) +
            FeatureGeneratorUtil.tokenFeature(tokenized[i + 1]) +
            FeatureGeneratorUtil.tokenFeature(tokenized[i + 2]));
      }

      pattern.append(FeatureGeneratorUtil.tokenFeature(tokenized[i]));

      if (!noLetters.matcher(tokenized[i]).find()) {
        feats.add("st=" + StringUtil.toLowerCase(tokenized[i]));
      }
    }

    feats.add("pta=" + pattern.toString());
  }
}
