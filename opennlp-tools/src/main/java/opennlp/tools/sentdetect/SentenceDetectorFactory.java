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

import java.util.Map;
import java.util.Set;

import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactProvider;
import opennlp.tools.util.model.ArtifactSerializer;

/**
 * The factory that provides SentenceDetecor default implementations and
 * resources
 */
public class SentenceDetectorFactory extends BaseToolFactory {

  private String languageCode;
  private char[] eosCharacters;
  private Set<String> abbreviations;

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
   * @param abbreviations
   * @param eosCharacters
   */
  public SentenceDetectorFactory(String languageCode,
      Set<String> abbreviations, char[] eosCharacters) {
    this.languageCode = languageCode;
    this.eosCharacters = eosCharacters;
    this.abbreviations = abbreviations;
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    // TODO: implement
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    Map<String, ArtifactSerializer> serializers = super
        .createArtifactSerializersMap();
    // TODO: include serializers
    return serializers;
  }

  @Override
  public Map<String, Object> createArtifactMap() {
    Map<String, Object> artifactMap = super.createArtifactMap();
    // TODO: include artifacts
    return artifactMap;
  }

  public static SentenceDetectorFactory create(String subclassName,
      String languageCode, Set<String> abbreviations, char[] eosCharacters) {
    // TODO: implement
    return null;
  }

  public char[] getEOSCharacters() {
    // TODO: load it from the model
    return this.eosCharacters;
  }

  public Set<String> getAbbreviations() {
    // TODO: load it from the model
    return this.abbreviations;
  }

  public String getLanguageCode() {
    // TODO: load it from the model
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
    if (eosChars != null && eosChars.length > 0) {
      return f.createSentenceContextGenerator(getAbbreviations(), eosChars);
    } else {
      return f.createSentenceContextGenerator(this.languageCode,
          getAbbreviations());
    }
  }
}
