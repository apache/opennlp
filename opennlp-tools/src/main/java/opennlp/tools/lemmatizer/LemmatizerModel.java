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

package opennlp.tools.lemmatizer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

/**
 * The {@link LemmatizerModel} is the model used by a learnable {@link Lemmatizer}.
 *
 * @see LemmatizerME
 */
public class LemmatizerModel extends BaseModel {

  private static final long serialVersionUID = -3362902631186156673L;
  private static final String COMPONENT_NAME = "StatisticalLemmatizer";
  private static final String LEMMATIZER_MODEL_ENTRY_NAME = "lemmatizer.model";

  /**
   * Initializes a {@link LemmatizerModel} instance via given parameters.
   *
   * @param languageCode An ISO conform language code.
   * @param lemmatizerModel A valid {@link SequenceClassificationModel}.
   * @param manifestInfoEntries Additional information kept in the manifest.
   * @param factory The {@link LemmatizerFactory} for creating related objects.
   */
  public LemmatizerModel(String languageCode, SequenceClassificationModel<String> lemmatizerModel,
      Map<String, String> manifestInfoEntries, LemmatizerFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);
    artifactMap.put(LEMMATIZER_MODEL_ENTRY_NAME, lemmatizerModel);
    checkArtifactMap();
  }

  /**
   * Initializes a {@link LemmatizerModel} instance via given parameters.
   *
   * @param languageCode An ISO conform language code.
   * @param lemmatizerModel A valid {@link MaxentModel}.
   * @param manifestInfoEntries Additional information kept in the manifest.
   * @param factory The {@link LemmatizerFactory} for creating related objects.
   */
  public LemmatizerModel(String languageCode, MaxentModel lemmatizerModel,
      Map<String, String> manifestInfoEntries, LemmatizerFactory factory) {
    this(languageCode, lemmatizerModel, LemmatizerME.DEFAULT_BEAM_SIZE, manifestInfoEntries, factory);
  }

  /**
   * Initializes a {@link LemmatizerModel} instance via given parameters.
   *
   * @param languageCode An ISO conform language code.
   * @param lemmatizerModel A valid {@link MaxentModel}.
   * @param beamSize The size of the beam that should be used when decoding sequences.
   * @param manifestInfoEntries Additional information kept in the manifest.
   * @param factory The {@link LemmatizerFactory} for creating related objects.
   */
  public LemmatizerModel(String languageCode, MaxentModel lemmatizerModel, int beamSize,
      Map<String, String> manifestInfoEntries, LemmatizerFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);
    artifactMap.put(LEMMATIZER_MODEL_ENTRY_NAME, lemmatizerModel);

    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    manifest.put(BeamSearch.BEAM_SIZE_PARAMETER, Integer.toString(beamSize));
    checkArtifactMap();
  }

  /**
   * Initializes a {@link LemmatizerModel} instance via given parameters.
   *
   * @param languageCode An ISO conform language code.
   * @param lemmatizerModel A valid {@link MaxentModel}.
   * @param factory The {@link LemmatizerFactory} for creating related objects.
   */
  public LemmatizerModel(String languageCode, MaxentModel lemmatizerModel, LemmatizerFactory factory) {
    this(languageCode, lemmatizerModel, null, factory);
  }

  /**
   * Initializes a {@link LemmatizerModel} instance via a valid {@link InputStream}.
   *
   * @param in The {@link InputStream} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public LemmatizerModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  /**
   * Initializes a {@link LemmatizerModel} instance via a valid {@link File}.
   *
   * @param modelFile The {@link File} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public LemmatizerModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  /**
   * Initializes a {@link LemmatizerModel} instance via a valid {@link Path}.
   *
   * @param modelPath The {@link Path} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public LemmatizerModel(Path modelPath) throws IOException {
    super(COMPONENT_NAME, Files.newInputStream(modelPath));
  }

  /**
   * Initializes a {@link LemmatizerModel} instance via a valid {@link URL}.
   *
   * @param modelURL The {@link URL} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public LemmatizerModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(LEMMATIZER_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Lemmatizer model is incomplete!");
    }
  }

  /**
   * @return Retrieves a {@link SequenceClassificationModel} instance.
   */
  public SequenceClassificationModel<String> getLemmatizerSequenceModel() {

    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);

    if (artifactMap.get(LEMMATIZER_MODEL_ENTRY_NAME) instanceof MaxentModel) {
      String beamSizeString = manifest.getProperty(BeamSearch.BEAM_SIZE_PARAMETER);

      int beamSize = LemmatizerME.DEFAULT_BEAM_SIZE;
      if (beamSizeString != null) {
        beamSize = Integer.parseInt(beamSizeString);
      }

      return new BeamSearch<>(beamSize, (MaxentModel) artifactMap.get(LEMMATIZER_MODEL_ENTRY_NAME));
    }
    else if (artifactMap.get(LEMMATIZER_MODEL_ENTRY_NAME) instanceof SequenceClassificationModel) {
      return (SequenceClassificationModel<String>) artifactMap.get(LEMMATIZER_MODEL_ENTRY_NAME);
    }
    else {
      return null;
    }
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return LemmatizerFactory.class;
  }

  /**
   * @return Retrieves the active {@link LemmatizerFactory}.
   */
  public LemmatizerFactory getFactory() {
    return (LemmatizerFactory) this.toolFactory;
  }
}
