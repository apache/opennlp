/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.sentdetect;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactProvider;

/**
 * The factory that provides SentenceDetecor default implementations and
 * resources
 */
public class SentenceDetectorFactory extends BaseToolFactory {

  private String languageCode;
  private char[] eosCharacters;
  private Dictionary abbreviationDictionary;
  private Boolean useTokenEnd = null;

  private static final String ABBREVIATIONS_ENTRY_NAME = "abbreviations.dictionary";
  private static final String EOS_CHARACTERS_PROPERTY = "eosCharacters";
  private static final String TOKEN_END_PROPERTY = "useTokenEnd";

  /**
   * Creates a {@link SentenceDetectorFactory} that provides the default
   * implementation of the resources.
   */
  public SentenceDetectorFactory() {
  }

  /**
   * Creates a {@link SentenceDetectorFactory} with an {@link ArtifactProvider}
   * that will be used to retrieve artifacts. This constructor will try to get
   * the language code, abbreviation dictionary and EOS characters from the
   * {@link ArtifactProvider}.
   * <p>
   * Sub-classes should implement a constructor with this signatures and call
   * this constructor.
   * <p>
   * This will be used to load the factory from a serialized
   * {@link SentenceModel}.
   */
  public SentenceDetectorFactory(ArtifactProvider artifactProvider) {
    super(artifactProvider);
  }

  /**
   * Creates a {@link SentenceDetectorFactory}. Use this constructor to
   * programmatically create a factory.
   * 
   * @param languageCode
   * @param abbreviationDictionary
   * @param eosCharacters
   */
  public SentenceDetectorFactory(String languageCode, boolean useTokenEnd,
      Dictionary abbreviationDictionary, char[] eosCharacters) {
    this.languageCode = languageCode;
    this.useTokenEnd = useTokenEnd;
    this.eosCharacters = eosCharacters;
    this.abbreviationDictionary = abbreviationDictionary;
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {

    if (this.artifactProvider.getManifestProperty(TOKEN_END_PROPERTY) == null)
      throw new InvalidFormatException(TOKEN_END_PROPERTY
          + " is a mandatory property!");

    Object abbreviationsEntry = this.artifactProvider
        .getArtifact(ABBREVIATIONS_ENTRY_NAME);

    if (abbreviationsEntry != null
        && !(abbreviationsEntry instanceof Dictionary)) {
      throw new InvalidFormatException(
          "Abbreviations dictionary has wrong type!");
    }
  }

  @Override
  public Map<String, Object> createArtifactMap() {
    Map<String, Object> artifactMap = super.createArtifactMap();

    // Abbreviations are optional
    if (abbreviationDictionary != null)
      artifactMap.put(ABBREVIATIONS_ENTRY_NAME, abbreviationDictionary);

    return artifactMap;
  }

  @Override
  public Map<String, String> createManifestEntries() {
    Map<String, String> manifestEntries = super.createManifestEntries();

    manifestEntries.put(TOKEN_END_PROPERTY, Boolean.toString(useTokenEnd));

    // EOS characters are optional
    if (eosCharacters != null)
      manifestEntries.put(EOS_CHARACTERS_PROPERTY,
          eosCharArrayToString(eosCharacters));

    return manifestEntries;
  }

  public static SentenceDetectorFactory create(String subclassName,
      String languageCode, boolean useTokenEnd,
      Dictionary abbreviationDictionary, char[] eosCharacters)
      throws InvalidFormatException {
    if (subclassName == null) {
      // will create the default factory
      return new SentenceDetectorFactory(languageCode, useTokenEnd,
          abbreviationDictionary, eosCharacters);
    }
    SentenceDetectorFactory theFactory = null;
    Class<? extends BaseToolFactory> factoryClass = loadSubclass(subclassName);
    if (factoryClass != null) {
      try {
        Constructor<?> constructor = null;
        constructor = factoryClass.getConstructor(String.class, boolean.class,
            Dictionary.class, char[].class);
        theFactory = (SentenceDetectorFactory) constructor.newInstance(
            languageCode, useTokenEnd, abbreviationDictionary, eosCharacters);
      } catch (NoSuchMethodException e) {
        String msg = "Could not instantiate the "
            + subclassName
            + ". The mandatory constructor (String, boolean, Dictionary, char[])) is missing.";
        System.err.println(msg);
        throw new IllegalArgumentException(msg);
      } catch (Exception e) {
        String msg = "Could not instantiate the "
            + subclassName
            + ". The constructor (String, boolean, Dictionary, char[]) throw an exception.";
        System.err.println(msg);
        e.printStackTrace();
        throw new InvalidFormatException(msg);
      }
    }
    return theFactory;
  }

  public char[] getEOSCharacters() {
    if (this.eosCharacters == null) {
      if (artifactProvider != null) {
        String prop = this.artifactProvider
            .getManifestProperty(EOS_CHARACTERS_PROPERTY);
        if (prop != null) {
          this.eosCharacters = eosStringToCharArray(prop);
        }
      } else {
        // get from language dependent factory
        Factory f = new Factory();
        this.eosCharacters = f.getEOSCharacters(languageCode);
      }
    }
    return this.eosCharacters;
  }

  public boolean isUseTokenEnd() {
    if (this.useTokenEnd == null && artifactProvider != null) {
      this.useTokenEnd = Boolean.valueOf(artifactProvider
          .getManifestProperty(TOKEN_END_PROPERTY));
    }
    return this.useTokenEnd;
  }

  public Dictionary getAbbreviationDictionary() {
    if (this.abbreviationDictionary == null && artifactProvider != null) {
      this.abbreviationDictionary = artifactProvider
          .getArtifact(ABBREVIATIONS_ENTRY_NAME);
    }
    return this.abbreviationDictionary;
  }

  public String getLanguageCode() {
    if (this.languageCode == null && artifactProvider != null) {
      this.languageCode = this.artifactProvider.getLanguage();
    }
    return this.languageCode;
  }

  public EndOfSentenceScanner getEndOfSentenceScanner() {
    Factory f = new Factory();
    char[] eosChars = getEOSCharacters();
    if (eosChars != null && eosChars.length > 0) {
      return f.createEndOfSentenceScanner(eosChars);
    } else {
      return f.createEndOfSentenceScanner(this.languageCode);
    }
  }

  public SDContextGenerator getSDContextGenerator() {
    Factory f = new Factory();
    char[] eosChars = getEOSCharacters();
    Set<String> abbs = null;
    Dictionary abbDict = getAbbreviationDictionary();
    if (abbDict != null) {
      abbs = abbDict.asStringSet();
    } else {
      abbs = Collections.emptySet();
    }
    if (eosChars != null && eosChars.length > 0) {
      return f.createSentenceContextGenerator(abbs, eosChars);
    } else {
      return f.createSentenceContextGenerator(this.languageCode, abbs);
    }
  }

  private String eosCharArrayToString(char[] eosCharacters) {
    String eosString = "";
    for (int i = 0; i < eosCharacters.length; i++)
      eosString += eosCharacters[i];
    return eosString;
  }

  private char[] eosStringToCharArray(String eosCharacters) {
    return eosCharacters.toCharArray();
  }
}
