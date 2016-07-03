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
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

/**
 * Class for the basis of the Sentiment Analysis model.
 */
public class SentimentModel extends BaseModel {

  private static final String COMPONENT_NAME = "SentimentME";
  private static final String SENTIMENT_MODEL_ENTRY_NAME = "sentiment.model";

  /**
   * Initializes the Sentiment Analysis model.
   *
   * @param languageCode
   *          the code for the language of the text, e.g. "en"
   * @param sentimentModel
   *          a MaxEnt sentiment model
   * @param manifestInfoEntries
   *          additional information in the manifest
   * @param factory
   *          a Sentiment Analysis factory
   */
  public SentimentModel(String languageCode, MaxentModel sentimentModel,
      Map<String, String> manifestInfoEntries, SentimentFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);
    artifactMap.put(SENTIMENT_MODEL_ENTRY_NAME, sentimentModel);
    checkArtifactMap();
  }

  /**
   * Initializes the Sentiment Analysis model.
   *
   * @param modelURL
   *          the URL to a file required for the model
   */
  public SentimentModel(URL modelURL)
      throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelURL);
  }

  /**
   * Initializes the Sentiment Analysis model.
   *
   * @param file
   *          the file required for the model
   */
  public SentimentModel(File file) throws InvalidFormatException, IOException {
    super(COMPONENT_NAME, file);
  }

  /**
   * Return the model
   *
   * @return the model
   */
  @Deprecated
  public SequenceClassificationModel<String> getSentimentModel() {
    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);

    String beamSizeString = manifest
        .getProperty(BeamSearch.BEAM_SIZE_PARAMETER);

    int beamSize = SentimentME.DEFAULT_BEAM_SIZE;
    if (beamSizeString != null) {
      beamSize = Integer.parseInt(beamSizeString);
    }

    return new BeamSearch<>(beamSize,
        (MaxentModel) artifactMap.get(SENTIMENT_MODEL_ENTRY_NAME));
  }

  /**
   * Returns the sentiment factory
   *
   * @return the sentiment factory for the model
   */
  public SentimentFactory getFactory() {
    return (SentimentFactory) this.toolFactory;
  }

  /**
   * Returns the MaxEntropy model
   *
   * @return the MaxEnt model
   */
  public MaxentModel getMaxentModel() {
    return (MaxentModel) artifactMap.get(SENTIMENT_MODEL_ENTRY_NAME);
  }

}
