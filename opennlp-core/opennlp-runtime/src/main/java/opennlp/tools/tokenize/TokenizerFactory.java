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

package opennlp.tools.tokenize;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import opennlp.tools.commons.SuppressForbidden;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.tokenize.lang.Factory;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ext.ExtensionLoader;

/**
 * The factory that provides {@link Tokenizer} default implementation and
 * resources. Users can extend this class if their application requires
 * overriding the {@link TokenContextGenerator}, {@link Dictionary} etc.
 */
public class TokenizerFactory extends BaseToolFactory {

  private String languageCode;
  private Dictionary abbreviationDictionary;
  private Boolean useAlphaNumericOptimization = false;
  private TokenizerCharacterPolicy characterPolicy;
  @SuppressForbidden("user-supplied alphanumeric pattern, kept for the deprecated API")
  private Pattern alphaNumericPattern;

  private static final String ABBREVIATIONS_ENTRY_NAME = "abbreviations.dictionary";
  private static final String USE_ALPHA_NUMERIC_OPTIMIZATION = "useAlphaNumericOptimization";
  private static final String CHARACTER_POLICY = "characterPolicy";
  private static final String ALPHA_NUMERIC_PATTERN = "alphaNumericPattern";

  /**
   * Instantiates a {@link TokenizerFactory} that provides the default implementation
   * of the resources.
   */
  public TokenizerFactory() {
  }

  /**
   * Instantiates a {@link TokenizerFactory} with the {@link TokenizerCharacterPolicy} of its
   * language. Use this constructor to programmatically create a factory.
   *
   * @param languageCode The ISO language code to be used for this factory.
   * @param abbreviationDictionary The {@link Dictionary} which holds abbreviations.
   * @param useAlphaNumericOptimization Whether alphanumerics are skipped, or not.
   */
  public TokenizerFactory(String languageCode, Dictionary abbreviationDictionary,
                          boolean useAlphaNumericOptimization) {
    this(languageCode, abbreviationDictionary, useAlphaNumericOptimization,
        (TokenizerCharacterPolicy) null);
  }

  /**
   * Instantiates a {@link TokenizerFactory}. Use this constructor to
   * programmatically create a factory.
   *
   * @param languageCode The ISO language code to be used for this factory.
   * @param abbreviationDictionary The {@link Dictionary} which holds abbreviations.
   * @param useAlphaNumericOptimization Whether alphanumerics are skipped, or not.
   * @param characterPolicy {@code null} or a custom {@link TokenizerCharacterPolicy}
   *                        (default is the policy for {@code languageCode}, provided by
   *                        {@link Factory#getCharacterPolicy(String)}).
   */
  public TokenizerFactory(String languageCode, Dictionary abbreviationDictionary,
                          boolean useAlphaNumericOptimization,
                          TokenizerCharacterPolicy characterPolicy) {
    this.init(languageCode, abbreviationDictionary,
        useAlphaNumericOptimization, characterPolicy);
  }

  /**
   * Instantiates a {@link TokenizerFactory} whose alphanumeric optimization tests candidate
   * tokens with a regular expression.
   *
   * @param languageCode The ISO language code to be used for this factory.
   * @param abbreviationDictionary The {@link Dictionary} which holds abbreviations.
   * @param useAlphaNumericOptimization Whether alphanumerics are skipped, or not.
   * @param alphaNumericPattern {@code null} for the policy of {@code languageCode}, or a custom
   *                            alphanumeric {@link Pattern}.
   * @deprecated Use
   *     {@link #TokenizerFactory(String, Dictionary, boolean, TokenizerCharacterPolicy)}. This
   *     constructor will be removed in 4.0.
   */
  @Deprecated(since = "3.0.0", forRemoval = true)
  @SuppressForbidden("user-supplied alphanumeric pattern, kept for the deprecated API")
  public TokenizerFactory(String languageCode, Dictionary abbreviationDictionary,
                          boolean useAlphaNumericOptimization, Pattern alphaNumericPattern) {
    this(languageCode, abbreviationDictionary, useAlphaNumericOptimization);
    setAlphaNumericPattern(alphaNumericPattern);
  }

  /**
   * @param languageCode The ISO language code to be used for this factory.
   * @param abbreviationDictionary The {@link Dictionary} which holds abbreviations.
   * @param useAlphaNumericOptimization Whether alphanumerics are skipped, or not.
   * @param characterPolicy {@code null} or a custom {@link TokenizerCharacterPolicy}
   *                        (default is the policy for {@code languageCode}, provided by
   *                        {@link Factory#getCharacterPolicy(String)}).
   */
  protected void init(String languageCode, Dictionary abbreviationDictionary,
      boolean useAlphaNumericOptimization, TokenizerCharacterPolicy characterPolicy) {
    this.languageCode = languageCode;
    this.useAlphaNumericOptimization = useAlphaNumericOptimization;
    this.characterPolicy = characterPolicy;
    this.abbreviationDictionary = abbreviationDictionary;
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    if (this.artifactProvider.getManifestProperty(USE_ALPHA_NUMERIC_OPTIMIZATION) == null)
      throw new InvalidFormatException(USE_ALPHA_NUMERIC_OPTIMIZATION
          + " is a mandatory property!");

    Object abbreviationsEntry = this.artifactProvider.getArtifact(ABBREVIATIONS_ENTRY_NAME);

    if (abbreviationsEntry != null && !(abbreviationsEntry instanceof Dictionary)) {
      throw new InvalidFormatException("Abbreviations dictionary '" + abbreviationsEntry +
              "' has wrong type, needs to be of type Dictionary!");
    }

    try {
      if (this.characterPolicy == null && this.alphaNumericPattern == null) {
        readManifest();
      }
    } catch (IllegalArgumentException e) {
      throw new InvalidFormatException(e.getMessage(), e);
    }
  }

  @Override
  public Map<String, Object> createArtifactMap() {
    Map<String, Object> artifactMap = super.createArtifactMap();

    // Abbreviations are optional
    if (abbreviationDictionary != null) {
      artifactMap.put(ABBREVIATIONS_ENTRY_NAME, abbreviationDictionary);
    }

    return artifactMap;
  }

  @Override
  public Map<String, String> createManifestEntries() {
    Map<String, String> manifestEntries = super.createManifestEntries();

    manifestEntries.put(USE_ALPHA_NUMERIC_OPTIMIZATION,
        Boolean.toString(isUseAlphaNumericOptimization()));
    resolve();
    if (this.alphaNumericPattern != null) {
      manifestEntries.put(ALPHA_NUMERIC_PATTERN, patternText(this.alphaNumericPattern));
    } else {
      manifestEntries.put(CHARACTER_POLICY, this.characterPolicy.toString());
      // Releases before 3.0 read only the pattern; give them the one with the same characters.
      final String legacy = Factory.legacyExpressionOf(this.characterPolicy);
      if (legacy != null) {
        manifestEntries.put(ALPHA_NUMERIC_PATTERN, legacy);
      }
    }

    return manifestEntries;
  }

  /**
   * Factory method the framework uses to instantiate a new {@link TokenizerFactory}.
   *
   * @param subclassName The name of the class implementing the {@link TokenizerFactory}.
   * @param languageCode The ISO language code the {@link Tokenizer} should use.
   * @param abbreviationDictionary An optional {@link Dictionary} containing abbreviations,
   *                               or {@code null} if not present.
   * @param useAlphaNumericOptimization Whether the alphanumeric optimization is enabled or not.
   * @param characterPolicy The {@link TokenizerCharacterPolicy} the alphanumeric optimization
   *                        should use, if enabled, or {@code null} for the language default.
   *
   * @return A valid {@link TokenizerFactory} instance.
   *
   * @throws InvalidFormatException Thrown if one of the input parameters doesn't comply the expected format.
   */
  public static TokenizerFactory create(String subclassName, String languageCode,
                                        Dictionary abbreviationDictionary,
                                        boolean useAlphaNumericOptimization,
                                        TokenizerCharacterPolicy characterPolicy)
      throws InvalidFormatException {
    if (subclassName == null) {
      // will create the default factory
      return new TokenizerFactory(languageCode, abbreviationDictionary,
          useAlphaNumericOptimization, characterPolicy);
    }
    try {
      TokenizerFactory theFactory = ExtensionLoader.instantiateExtension(
          TokenizerFactory.class, subclassName);
      theFactory.init(languageCode, abbreviationDictionary,
          useAlphaNumericOptimization, characterPolicy);
      return theFactory;
    } catch (Exception e) {
      String msg = "Could not instantiate the " + subclassName
          + ". The initialization throw an exception.";
      throw new InvalidFormatException(msg, e);
    }
  }

  /**
   * Factory method the framework uses to instantiate a new {@link TokenizerFactory} with the
   * {@link TokenizerCharacterPolicy} of its language.
   *
   * @param subclassName The name of the class implementing the {@link TokenizerFactory}.
   * @param languageCode The ISO language code the {@link Tokenizer} should use.
   * @param abbreviationDictionary An optional {@link Dictionary} containing abbreviations,
   *                               or {@code null} if not present.
   * @param useAlphaNumericOptimization Whether the alphanumeric optimization is enabled or not.
   *
   * @return A valid {@link TokenizerFactory} instance.
   *
   * @throws InvalidFormatException Thrown if one of the input parameters doesn't comply the expected format.
   */
  public static TokenizerFactory create(String subclassName, String languageCode,
                                        Dictionary abbreviationDictionary,
                                        boolean useAlphaNumericOptimization)
      throws InvalidFormatException {
    return create(subclassName, languageCode, abbreviationDictionary,
        useAlphaNumericOptimization, (TokenizerCharacterPolicy) null);
  }

  /**
   * Factory method the framework uses to instantiate a new {@link TokenizerFactory} whose
   * alphanumeric optimization tests candidate tokens with a regular expression.
   *
   * @param subclassName The name of the class implementing the {@link TokenizerFactory}.
   * @param languageCode The ISO language code the {@link Tokenizer} should use.
   * @param abbreviationDictionary An optional {@link Dictionary} containing abbreviations,
   *                               or {@code null} if not present.
   * @param useAlphaNumericOptimization Whether the alphanumeric optimization is enabled or not.
   * @param alphaNumericPattern {@code null} for the policy of {@code languageCode}, or a custom
   *                            alphanumeric {@link Pattern}.
   *
   * @return A valid {@link TokenizerFactory} instance.
   *
   * @throws InvalidFormatException Thrown if one of the input parameters doesn't comply the expected format.
   * @deprecated Use
   *     {@link #create(String, String, Dictionary, boolean, TokenizerCharacterPolicy)}. This
   *     method will be removed in 4.0.
   */
  @Deprecated(since = "3.0.0", forRemoval = true)
  @SuppressForbidden("user-supplied alphanumeric pattern, kept for the deprecated API")
  public static TokenizerFactory create(String subclassName, String languageCode,
                                        Dictionary abbreviationDictionary,
                                        boolean useAlphaNumericOptimization,
                                        Pattern alphaNumericPattern)
      throws InvalidFormatException {
    TokenizerFactory factory = create(subclassName, languageCode, abbreviationDictionary,
        useAlphaNumericOptimization);
    factory.setAlphaNumericPattern(alphaNumericPattern);
    return factory;
  }

  /**
   * @return Retrieves the (user-)specified {@link TokenizerCharacterPolicy}, the one read from
   *         the model manifest, or the default for the language in use; {@code null} if this
   *         factory tests candidate tokens with a custom alphanumeric {@link Pattern}.
   * @throws IllegalArgumentException Thrown if the manifest has a character policy or an
   *         alphanumeric pattern that cannot be read.
   */
  public TokenizerCharacterPolicy getCharacterPolicy() {
    resolve();
    return this.characterPolicy;
  }

  /**
   * @return Retrieves the custom alphanumeric {@link Pattern} of this factory, or else the pattern
   *         of earlier releases that accepts the same characters as its
   *         {@link TokenizerCharacterPolicy}; {@code null} for a custom policy that no such
   *         pattern matches.
   * @throws IllegalArgumentException Thrown if the manifest has a character policy or an
   *         alphanumeric pattern that cannot be read.
   * @deprecated Use {@link #getCharacterPolicy()}. This method will be removed in 4.0.
   */
  @Deprecated(since = "3.0.0", forRemoval = true)
  @SuppressForbidden("user-supplied alphanumeric pattern, kept for the deprecated API")
  public Pattern getAlphaNumericPattern() {
    resolve();
    if (this.alphaNumericPattern != null) {
      return this.alphaNumericPattern;
    }
    final String legacy = Factory.legacyExpressionOf(this.characterPolicy);
    return legacy == null ? null : Pattern.compile(legacy);
  }

  /**
   * {@return the test the alphanumeric optimization applies to a candidate token}
   *
   * @throws IllegalArgumentException Thrown if the manifest has a character policy or an
   *         alphanumeric pattern that cannot be read.
   */
  Predicate<CharSequence> alphanumericTest() {
    resolve();
    return this.alphaNumericPattern != null
        ? new PatternTest(this.alphaNumericPattern) : this.characterPolicy;
  }

  /**
   * @return {@code true} if the alphanumeric optimization is enabled, otherwise {@code false}.
   */
  public boolean isUseAlphaNumericOptimization() {
    if (artifactProvider != null) {
      this.useAlphaNumericOptimization = Boolean.valueOf(this.artifactProvider
          .getManifestProperty(USE_ALPHA_NUMERIC_OPTIMIZATION));
    }
    return this.useAlphaNumericOptimization;
  }

  /**
   * @return The abbreviation {@link Dictionary} or {@code null} if none is active.
   */
  public Dictionary getAbbreviationDictionary() {
    if (this.abbreviationDictionary == null && artifactProvider != null) {
      this.abbreviationDictionary = this.artifactProvider.getArtifact(ABBREVIATIONS_ENTRY_NAME);
    }
    return this.abbreviationDictionary;
  }

  /**
   * @return Retrieves the ISO language code in use.
   */
  public String getLanguageCode() {
    if (this.languageCode == null && this.artifactProvider != null) {
      this.languageCode = this.artifactProvider.getLanguage();
    }
    return this.languageCode;
  }

  /**
   * @return Retrieves a {@link TokenContextGenerator} instance.
   */
  public TokenContextGenerator getContextGenerator() {
    Factory f = new Factory();
    Set<String> abbs;
    Dictionary abbDict = getAbbreviationDictionary();
    if (abbDict != null) {
      abbs = abbDict.asStringSet();
    } else {
      abbs = Collections.emptySet();
    }
    return f.createTokenContextGenerator(getLanguageCode(), abbs);
  }

  /**
   * Sets the character policy or the alphanumeric pattern from the model manifest if neither is
   * set yet, and falls back to the policy of the language.
   *
   * @throws IllegalArgumentException Thrown if the manifest has a character policy or an
   *         alphanumeric pattern that cannot be read.
   */
  private void resolve() {
    if (this.characterPolicy == null && this.alphaNumericPattern == null) {
      if (this.artifactProvider != null) {
        readManifest();
      }
      if (this.characterPolicy == null && this.alphaNumericPattern == null) {
        this.characterPolicy = new Factory().getCharacterPolicy(getLanguageCode());
      }
    }
  }

  /**
   * Reads the character policy, or else the alphanumeric pattern, from the model manifest. A
   * pattern that releases before 3.0 wrote for a language is replaced by the policy that accepts
   * the same characters; any other pattern is kept and evaluated as a regular expression.
   *
   * @throws IllegalArgumentException Thrown if the character policy text cannot be read, or if
   *         the alphanumeric pattern is not a valid regular expression.
   */
  @SuppressForbidden("user-supplied alphanumeric pattern, kept for the deprecated API")
  private void readManifest() {
    String text = this.artifactProvider.getManifestProperty(CHARACTER_POLICY);
    if (text != null) {
      try {
        this.characterPolicy = TokenizerCharacterPolicy.parse(text);
        return;
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(describeModel() + " has an unreadable "
            + CHARACTER_POLICY + ": " + e.getMessage(), e);
      }
    }
    String pattern = this.artifactProvider.getManifestProperty(ALPHA_NUMERIC_PATTERN);
    if (pattern == null) {
      return;
    }
    this.characterPolicy = Factory.policyOfLegacyExpression(pattern);
    if (this.characterPolicy == null) {
      try {
        this.alphaNumericPattern = Pattern.compile(pattern);
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException(describeModel() + " has an invalid "
            + ALPHA_NUMERIC_PATTERN + " '" + pattern + "'", e);
      }
    }
  }

  /**
   * Keeps a deprecated alphanumeric pattern as the pattern of this factory, unless an earlier
   * release wrote it for a language: then the policy of that language replaces it, so the
   * alphanumeric optimization takes the policy path.
   *
   * @param pattern The alphanumeric pattern, or {@code null} for the policy of the language.
   */
  @SuppressForbidden("user-supplied alphanumeric pattern, kept for the deprecated API")
  private void setAlphaNumericPattern(Pattern pattern) {
    if (pattern == null) {
      return;
    }
    TokenizerCharacterPolicy known = Factory.policyOfLegacyExpression(patternText(pattern));
    if (known != null) {
      this.characterPolicy = known;
    } else {
      this.alphaNumericPattern = pattern;
    }
  }

  /**
   * {@return the text of {@code pattern}}
   *
   * @param pattern An alphanumeric pattern.
   */
  @SuppressForbidden("user-supplied alphanumeric pattern, kept for the deprecated API")
  private static String patternText(Pattern pattern) {
    return pattern.pattern();
  }

  /**
   * Tests a candidate token against a whole-match regular expression, for factories and event
   * streams configured with a custom alphanumeric {@link Pattern}.
   */
  @SuppressForbidden("user-supplied alphanumeric pattern, kept for the deprecated API")
  static final class PatternTest implements Predicate<CharSequence> {

    private final Pattern pattern;

    /**
     * @param pattern The alphanumeric pattern.
     */
    PatternTest(Pattern pattern) {
      this.pattern = pattern;
    }

    @Override
    public boolean test(CharSequence candidate) {
      return pattern.matcher(candidate).matches();
    }
  }

  /** {@return a description of the loaded model for error messages} */
  private String describeModel() {
    return "The tokenizer model for language '" + this.artifactProvider.getLanguage() + "'";
  }
}
