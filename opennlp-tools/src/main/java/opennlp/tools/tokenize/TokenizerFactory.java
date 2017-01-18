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
 * The factory that provides {@link Tokenizer} default implementations and
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
   * Creates a {@link TokenizerFactory} that provides the default implementation
   * of the resources.
   */
  public TokenizerFactory() {
  }

  /**
   * Creates a {@link TokenizerFactory}. Use this constructor to
   * programmatically create a factory.
   *
   * @param languageCode
   *          the language of the natural text
   * @param abbreviationDictionary
   *          an abbreviations dictionary
   * @param useAlphaNumericOptimization
   *          if true alpha numerics are skipped
   * @param alphaNumericPattern
   *          null or a custom alphanumeric pattern (default is:
   *          "^[A-Za-z0-9]+$", provided by {@link Factory#DEFAULT_ALPHANUMERIC}
   */
  public TokenizerFactory(String languageCode,
      Dictionary abbreviationDictionary, boolean useAlphaNumericOptimization,
      Pattern alphaNumericPattern) {
    this.init(languageCode, abbreviationDictionary,
        useAlphaNumericOptimization, alphaNumericPattern);
  }

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
        Boolean.toString(isUseAlphaNumericOptmization()));

    // alphanumeric pattern is optional
    if (getAlphaNumericPattern() != null) {
      manifestEntries.put(ALPHA_NUMERIC_PATTERN, getAlphaNumericPattern().pattern());
    }

    return manifestEntries;
  }

  /**
   * Factory method the framework uses create a new {@link TokenizerFactory}.
   *
   * @param subclassName the name of the class implementing the {@link TokenizerFactory}
   * @param languageCode the language code the tokenizer should use
   * @param abbreviationDictionary an optional dictionary containing abbreviations, or null if not present
   * @param useAlphaNumericOptimization indicate if the alpha numeric optimization
   *     should be enabled or disabled
   * @param alphaNumericPattern the pattern the alpha numeric optimization should use
   *
   * @return the instance of the Tokenizer Factory
   *
   * @throws InvalidFormatException if once of the input parameters doesn't comply if the expected format
   */
  public static TokenizerFactory create(String subclassName,
      String languageCode, Dictionary abbreviationDictionary,
      boolean useAlphaNumericOptimization, Pattern alphaNumericPattern)
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
      System.err.println(msg);
      e.printStackTrace();
      throw new InvalidFormatException(msg, e);
    }
  }

  /**
   * Gets the alpha numeric pattern.
   *
   * @return the user specified alpha numeric pattern or a default.
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
   * Gets whether to use alphanumeric optimization.
   *
   * @return true if the alpha numeric optimization is enabled, otherwise false
   */
  public boolean isUseAlphaNumericOptmization() {
    if (artifactProvider != null) {
      this.useAlphaNumericOptimization = Boolean.valueOf(this.artifactProvider
          .getManifestProperty(USE_ALPHA_NUMERIC_OPTIMIZATION));
    }
    return this.useAlphaNumericOptimization;
  }

  /**
   * Gets the abbreviation dictionary
   *
   * @return null or the abbreviation dictionary
   */
  public Dictionary getAbbreviationDictionary() {
    if (this.abbreviationDictionary == null && artifactProvider != null) {
      this.abbreviationDictionary = this.artifactProvider.getArtifact(ABBREVIATIONS_ENTRY_NAME);
    }
    return this.abbreviationDictionary;
  }

  /**
   * Retrieves the language code.
   *
   * @return the language code
   */
  public String getLanguageCode() {
    if (this.languageCode == null && this.artifactProvider != null) {
      this.languageCode = this.artifactProvider.getLanguage();
    }
    return this.languageCode;
  }

  /**
   * Gets the context generator
   *
   * @return a new instance of the context generator
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
