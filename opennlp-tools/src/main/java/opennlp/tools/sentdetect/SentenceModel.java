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


package opennlp.tools.sentdetect;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;
import opennlp.tools.util.model.ModelUtil;

/**
 * The {@link SentenceModel} is the model used
 * by a learnable {@link SentenceDetector}.
 *
 * @see SentenceDetectorME
 */
public class SentenceModel extends BaseModel {

  private static final String COMPONENT_NAME = "SentenceDetectorME";

  private static final String MAXENT_MODEL_ENTRY_NAME = "sent.model";

  public SentenceModel(String languageCode, MaxentModel sentModel,
      Map<String, String> manifestInfoEntries, SentenceDetectorFactory sdFactory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, sdFactory);
    artifactMap.put(MAXENT_MODEL_ENTRY_NAME, sentModel);
    checkArtifactMap();
  }

  /**
   * TODO: was added in 1.5.3 -&gt; remove
   * @deprecated Use
   *             {@link #SentenceModel(String, MaxentModel, Map, SentenceDetectorFactory)}
   *             instead and pass in a {@link SentenceDetectorFactory}
   */
  public SentenceModel(String languageCode, MaxentModel sentModel, boolean useTokenEnd,
      Dictionary abbreviations, char[] eosCharacters, Map<String, String> manifestInfoEntries) {
    this(languageCode, sentModel, manifestInfoEntries,
        new SentenceDetectorFactory(languageCode, useTokenEnd, abbreviations,
            eosCharacters));
  }

  /**
   * TODO: was added in 1.5.3 -&gt; remove
   *
   * @deprecated Use
   *             {@link #SentenceModel(String, MaxentModel, Map, SentenceDetectorFactory)}
   *             instead and pass in a {@link SentenceDetectorFactory}
   */
  public SentenceModel(String languageCode, MaxentModel sentModel,
      boolean useTokenEnd, Dictionary abbreviations, char[] eosCharacters) {
    this(languageCode, sentModel, useTokenEnd, abbreviations, eosCharacters,
        null);
  }

  public SentenceModel(String languageCode, MaxentModel sentModel,
      boolean useTokenEnd, Dictionary abbreviations, Map<String, String> manifestInfoEntries) {
    this(languageCode, sentModel, useTokenEnd, abbreviations, null,
        manifestInfoEntries);
  }

  public SentenceModel(String languageCode, MaxentModel sentModel,
      boolean useTokenEnd, Dictionary abbreviations) {
    this (languageCode, sentModel, useTokenEnd, abbreviations, null, null);
  }

  public SentenceModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  public SentenceModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  public SentenceModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(MAXENT_MODEL_ENTRY_NAME) instanceof MaxentModel)) {
      throw new InvalidFormatException("Unable to find " + MAXENT_MODEL_ENTRY_NAME +
          " maxent model!");
    }

    if (!ModelUtil.validateOutcomes(getMaxentModel(), SentenceDetectorME.SPLIT,
        SentenceDetectorME.NO_SPLIT)) {
      throw new InvalidFormatException("The maxent model is not compatible " +
          "with the sentence detector!");
    }
  }

  public SentenceDetectorFactory getFactory() {
    return (SentenceDetectorFactory) this.toolFactory;
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return SentenceDetectorFactory.class;
  }

  public MaxentModel getMaxentModel() {
    return (MaxentModel) artifactMap.get(MAXENT_MODEL_ENTRY_NAME);
  }

  public Dictionary getAbbreviations() {
    if (getFactory() != null) {
      return getFactory().getAbbreviationDictionary();
    }
    return null;
  }

  public boolean useTokenEnd() {
    return getFactory() == null || getFactory().isUseTokenEnd();
  }

  public char[] getEosCharacters() {
    if (getFactory() != null) {
      return getFactory().getEOSCharacters();
    }
    return null;
  }
}
