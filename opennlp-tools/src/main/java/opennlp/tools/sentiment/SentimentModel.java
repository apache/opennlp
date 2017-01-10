/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentiment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.model.BaseModel;

/**
 * Class for the basis of the Sentiment Analysis model.
 */
public class SentimentModel extends BaseModel {

  private static final String COMPONENT_NAME = "SentimentME";
  private static final String SENTIMENT_MODEL_ENTRY_NAME = "sentiment.model";

  /**
   * Instantiates a {@link SentimentModel} model.
   *
   * @param languageCode
   *          The code for the language of the text, e.g. "en"
   * @param sentimentModel
   *          A {@link MaxentModel} sentiment model
   * @param manifestInfoEntries
   *          Additional information in the manifest
   * @param factory
   *          A {@link SentimentFactory} instance
   */
  public SentimentModel(String languageCode, MaxentModel sentimentModel,
      Map<String, String> manifestInfoEntries, SentimentFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);
    artifactMap.put(SENTIMENT_MODEL_ENTRY_NAME, sentimentModel);
    checkArtifactMap();
  }

  /**
   * Instantiates a {@link SentimentModel} model via a {@link URL} reference.
   *
   * @param modelURL
   *          The {@link URL} to a file required to load the model.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public SentimentModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  /**
   * Instantiates a {@link SentimentModel} model via a {@link File} reference.
   *
   * @param file
   *          The {@link File} required to load the model.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public SentimentModel(File file) throws IOException {
    super(COMPONENT_NAME, file);
  }

  /**
   * Instantiates a {@link SentimentModel} model via a {@link InputStream} reference.
   *
   * @param modelIn
   *          The {@link InputStream} required to load the model.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public SentimentModel(InputStream modelIn) throws IOException {
    super(COMPONENT_NAME, modelIn);
  }

  /**
   * @return Retrieves the {@link SequenceClassificationModel} model.
   */
  @Deprecated
  public SequenceClassificationModel getSentimentModel() {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);

    String beamSizeString = manifest.getProperty(BeamSearch.BEAM_SIZE_PARAMETER);

    int beamSize = SentimentME.DEFAULT_BEAM_SIZE;
    if (beamSizeString != null) {
      beamSize = Integer.parseInt(beamSizeString);
    }

    return new BeamSearch(beamSize,
        (MaxentModel) artifactMap.get(SENTIMENT_MODEL_ENTRY_NAME));
  }

  /**
   * @return Retrieves the {@link SentimentFactory} for the model.
   */
  public SentimentFactory getFactory() {
    return (SentimentFactory) this.toolFactory;
  }

  /**
   * @return Retrieves the {@link MaxentModel}.
   */
  public MaxentModel getMaxentModel() {
    return (MaxentModel) artifactMap.get(SENTIMENT_MODEL_ENTRY_NAME);
  }

}
