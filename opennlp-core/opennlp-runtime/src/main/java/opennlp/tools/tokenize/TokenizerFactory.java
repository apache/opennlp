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
import java.util.regex.Pattern;

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
  private Pattern alphaNumericPattern;

  private static final String ABBREVIATIONS_ENTRY_NAME = "abbreviations.dictionary";
  private static final String USE_ALPHA_NUMERIC_OPTIMIZATION = "useAlphaNumericOptimization";
  private static final String ALPHA_NUMERIC_PATTERN = "alphaNumericPattern";

  /**
   * Instantiates a {@link TokenizerFactory} that provides the default implementation
   * of the resources.
   */
  public TokenizerFactory() {
  }
  
  /**
   * Instantiates a {@link TokenizerFactory}. Use this constructor to
   * programmatically create a factory.
   *
   * @param languageCode The ISO language code to be used for this factory.
   * @param abbreviationDictionary The {@link Dictionary} which holds abbreviations.
   * @param useAlphaNumericOptimization Whether alphanumerics are skipped, or not.
   * @param alphaNumericPattern {@code null} or a custom alphanumeric {@link Pattern}
   *                            (default is: {@code "^[A-Za-z0-9]+$"}, provided by
   *                            {@link Factory#DEFAULT_ALPHANUMERIC}.
   */
  public TokenizerFactory(String languageCode, Dictionary abbreviationDictionary,
                          boolean useAlphaNumericOptimization, Pattern alphaNumericPattern) {
    this.init(languageCode, abbreviationDictionary,
        useAlphaNumericOptimization, alphaNumericPattern);
  }

  /**
   * @param languageCode The ISO language code to be used for this factory.
   * @param abbreviationDictionary The {@link Dictionary} which holds abbreviations.
   * @param useAlphaNumericOptimization Whether alphanumerics are skipped, or not.
   * @param alphaNumericPattern {@code null} or a custom alphanumeric {@link Pattern}
   *                            (default is: {@code "^[A-Za-z0-9]+$"}, provided by
   *                            {@link Factory#DEFAULT_ALPHANUMERIC}.
   */
  protected void init(String languageCode, Dictionary abbreviationDictionary,
      boolean useAlphaNumericOptimization, Pattern alphaNumericPattern) {
    this.languageCode = languageCode;
    this.useAlphaNumericOptimization = useAlphaNumericOptimization;
    this.alphaNumericPattern = alphaNumericPattern;
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

    // alphanumeric pattern is optional
    if (getAlphaNumericPattern() != null) {
      manifestEntries.put(ALPHA_NUMERIC_PATTERN, getAlphaNumericPattern().pattern());
    }

    return manifestEntries;
  }

  /**
   * Factory method the framework uses instantiate a new {@link TokenizerFactory}.
   *
   * @param subclassName The name of the class implementing the {@link TokenizerFactory}.
   * @param languageCode The ISO language code the {@link Tokenizer} should use.
   * @param abbreviationDictionary An optional {@link Dictionary} containing abbreviations,
   *                               or {@code null} if not present.
   * @param useAlphaNumericOptimization Whether the alphanumeric optimization is be enabled or not.
   * @param alphaNumericPattern The {@link Pattern} the alphanumeric optimization should use,
   *                            if enabled.
   *
   * @return A valid {@link TokenizerFactory} instance.
   *
   * @throws InvalidFormatException Thrown if one of the input parameters doesn't comply the expected format.
   */
  public static TokenizerFactory create(String subclassName, String languageCode,
                                        Dictionary abbreviationDictionary,
                                        boolean useAlphaNumericOptimization,
                                        Pattern alphaNumericPattern)
      throws InvalidFormatException {
    if (subclassName == null) {
      // will create the default factory
      return new TokenizerFactory(languageCode, abbreviationDictionary,
          useAlphaNumericOptimization, alphaNumericPattern);
    }
    try {
      TokenizerFactory theFactory = ExtensionLoader.instantiateExtension(
          TokenizerFactory.class, subclassName);
      theFactory.init(languageCode, abbreviationDictionary,
          useAlphaNumericOptimization, alphaNumericPattern);
      return theFactory;
    } catch (Exception e) {
      String msg = "Could not instantiate the " + subclassName
          + ". The initialization throw an exception.";
      throw new InvalidFormatException(msg, e);
    }
  }

  /**
   * @return Retrieves the (user-)specified alphanumeric {@link Pattern} or a default.
   */
  public Pattern getAlphaNumericPattern() {
    if (this.alphaNumericPattern == null) {
      if (this.artifactProvider != null) {
        String prop = this.artifactProvider.getManifestProperty(ALPHA_NUMERIC_PATTERN);
        if (prop != null) {
          this.alphaNumericPattern = Pattern.compile(prop);
        }
      }
      // could not load from manifest, will get from language dependent factory
      if (this.alphaNumericPattern == null) {
        Factory f = new Factory();
        this.alphaNumericPattern = f.getAlphanumeric(languageCode);
      }
    }
    return this.alphaNumericPattern;
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
}
