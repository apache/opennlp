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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.namefind.RegexNameFinderFactory;

/**
 * Normalizes the tokens before sending it down to the child {@link AdaptiveFeatureGenerator}.
 *
 * Default normalizations:
 *   Numbers: 9838749 -> 9999999
 *   URL: http://apache.opennlp.org -> $URL$
 *   Email: users@opennlp.apache.org -> $EMAIL$
 */
public class TokenNormalizerFeatureGenerator implements AdaptiveFeatureGenerator {

  private final AdaptiveFeatureGenerator generator;
  private final TokenNormalizer normalizer;

  // Warning: not thread safe
  private String[] lastTokens = null;
  private String[] lastNormalizedTokens = null;

  /**
   * Initializes the current instance with the given parameters.
   *
   * @param generator Feature generator to apply the normalization.
   * @param normalizer A token normalizer.
   */
  public TokenNormalizerFeatureGenerator(AdaptiveFeatureGenerator generator, TokenNormalizer normalizer) {
    this.generator = generator;
    this.normalizer = normalizer;
  }

  /**
   * Initializes the current instance with the given parameters.
   *
   * @param normalizer A token normalizer.
   * @param generators Feature generators to apply the normalization.
   */
  public TokenNormalizerFeatureGenerator(TokenNormalizer normalizer,
                                         AdaptiveFeatureGenerator... generators) {
    this(new AggregatedFeatureGenerator(generators), normalizer);
  }

  /**
   * Initializes the current instance with the given parameters.
   *
   * @param generator Feature generator to apply the normalization.
   */
  public TokenNormalizerFeatureGenerator(AdaptiveFeatureGenerator generator) {
    this(generator, new DefaultTokenNormalizer());
  }

  /**
   * Initializes the current instance with the given parameters.
   *
   * @param generators Feature generators to apply the normalization.
   */
  public TokenNormalizerFeatureGenerator(AdaptiveFeatureGenerator... generators) {
    this(new AggregatedFeatureGenerator(generators), new DefaultTokenNormalizer());
  }

  public void createFeatures(List<String> features, String[] tokens, int index, String[] preds) {

    String[] normalizedTokens;
    if (tokens == lastTokens) {
      normalizedTokens = lastNormalizedTokens;
    } else {
      normalizedTokens = new String[tokens.length];
      for (int i = 0; i < tokens.length; i++) {
        normalizedTokens[i] = this.normalizer.normalize(tokens[i]);
      }
      lastTokens = tokens;
      lastNormalizedTokens = normalizedTokens;
    }
    generator.createFeatures(features, normalizedTokens, index, preds);
  }

  public void updateAdaptiveData(String[] tokens, String[] outcomes) {
    generator.updateAdaptiveData(tokens, outcomes);
  }

  public void clearAdaptiveData() {
    generator.clearAdaptiveData();
  }

  @Override
  public String toString() {
    return super.toString() + ": Token normalizer: " + this.normalizer.getClass().toString();
  }

  // for test purpose
  TokenNormalizer getTokenNormalizer() {
    return this.normalizer;
  }

  public static class DefaultTokenNormalizer implements TokenNormalizer {

    private static final Pattern DIGIT = Pattern.compile("\\d");

    @Override
    public String normalize(String token) {

      StringPattern stringPattern = StringPattern.recognize(token);

      // numbers: replace all by 9
      if (stringPattern.containsDigit()) {
        Matcher digitMatcher = DIGIT.matcher(token);
        token = digitMatcher.replaceAll("9");
      }

      if (matches(token, RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.EMAIL.getRegexMap())) {
        token = "$EMAIL$";
      } else if (matches(token,
          RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.URL.getRegexMap())) {
        token = "$URL$";
      }
      return token;
    }

    private boolean matches(String token, Map<String, Pattern[]> regexMap) {
      for (Pattern[] patterns :
          regexMap.values()) {
        for (Pattern p :
            patterns) {
          if (p.matcher(token).matches()) {
            return true;
          }
        }
      }
      return false;
    }
  }
}

