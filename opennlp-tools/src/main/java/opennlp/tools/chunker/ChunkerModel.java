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


package opennlp.tools.chunker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.TokenTag;
import opennlp.tools.util.model.BaseModel;

/**
 * The {@link ChunkerModel} is the model used
 * by a learnable {@link Chunker}.
 *
 * @see ChunkerME
 */
public class ChunkerModel extends BaseModel {

  private static final String COMPONENT_NAME = "ChunkerME";
  private static final String CHUNKER_MODEL_ENTRY_NAME = "chunker.model";

  public ChunkerModel(String languageCode, SequenceClassificationModel<String> chunkerModel,
      Map<String, String> manifestInfoEntries, ChunkerFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);
    artifactMap.put(CHUNKER_MODEL_ENTRY_NAME, chunkerModel);
    checkArtifactMap();
  }

  public ChunkerModel(String languageCode, MaxentModel chunkerModel,
      Map<String, String> manifestInfoEntries, ChunkerFactory factory) {
    this(languageCode, chunkerModel, ChunkerME.DEFAULT_BEAM_SIZE, manifestInfoEntries, factory);
  }

  public ChunkerModel(String languageCode, MaxentModel chunkerModel, int beamSize,
      Map<String, String> manifestInfoEntries, ChunkerFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);
    artifactMap.put(CHUNKER_MODEL_ENTRY_NAME, chunkerModel);

    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    manifest.put(BeamSearch.BEAM_SIZE_PARAMETER, Integer.toString(beamSize));

    checkArtifactMap();
  }

  public ChunkerModel(String languageCode, MaxentModel chunkerModel, ChunkerFactory factory) {
    this(languageCode, chunkerModel, null, factory);
  }

  public ChunkerModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
  }

  public ChunkerModel(File modelFile) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelFile);
  }

  public ChunkerModel(Path modelPath) throws IOException, InvalidFormatException {
    this(modelPath.toFile());
  }

  public ChunkerModel(URL modelURL) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(CHUNKER_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Chunker model is incomplete!");
    }

    // Since 1.8.0 we changed the ChunkerFactory signature. This will check the if the model
    // declares a not default factory, and if yes, check if it was created before 1.8
    if ( (getManifestProperty(FACTORY_NAME) != null
            && !getManifestProperty(FACTORY_NAME).equals("opennlp.tools.chunker.ChunkerFactory") )
        && this.getVersion().getMajor() <= 1
        && this.getVersion().getMinor() < 8) {
      throw new InvalidFormatException("The Chunker factory '" + getManifestProperty(FACTORY_NAME) +
      "' is no longer compatible. Please update it to match the latest ChunkerFactory.");
    }

  }

  /**
   * @deprecated use getChunkerSequenceModel instead. This method will be removed soon.
   */
  @Deprecated
  public MaxentModel getChunkerModel() {
    if (artifactMap.get(CHUNKER_MODEL_ENTRY_NAME) instanceof MaxentModel) {
      return (MaxentModel) artifactMap.get(CHUNKER_MODEL_ENTRY_NAME);
    }
    else {
      return null;
    }
  }

  public SequenceClassificationModel<TokenTag> getChunkerSequenceModel() {

    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);

    if (artifactMap.get(CHUNKER_MODEL_ENTRY_NAME) instanceof MaxentModel) {
      String beamSizeString = manifest.getProperty(BeamSearch.BEAM_SIZE_PARAMETER);

      int beamSize = ChunkerME.DEFAULT_BEAM_SIZE;
      if (beamSizeString != null) {
        beamSize = Integer.parseInt(beamSizeString);
      }

      return new BeamSearch<>(beamSize, (MaxentModel) artifactMap.get(CHUNKER_MODEL_ENTRY_NAME));
    }
    else if (artifactMap.get(CHUNKER_MODEL_ENTRY_NAME) instanceof SequenceClassificationModel) {
      return (SequenceClassificationModel) artifactMap.get(CHUNKER_MODEL_ENTRY_NAME);
    }
    else {
      return null;
    }
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return ChunkerFactory.class;
  }


  public ChunkerFactory getFactory() {
    return (ChunkerFactory) this.toolFactory;
  }
}
