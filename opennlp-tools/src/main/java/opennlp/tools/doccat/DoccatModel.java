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

package opennlp.tools.doccat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

/**
 * A model for document categorization
 */
public class DoccatModel extends BaseModel {

  private static final String COMPONENT_NAME = "DocumentCategorizerME";
  private static final String DOCCAT_MODEL_ENTRY_NAME = "doccat.model";

  /**
   * Initializes a {@link DoccatModel} instance via given parameters.
   *
   * @param languageCode An ISO conform language code.
   * @param doccatModel A valid {@link MaxentModel} to be used.
   * @param manifestInfoEntries Additional information kept in the manifest.
   * @param factory The {@link DoccatFactory} to be used.
   */
  public DoccatModel(String languageCode, MaxentModel doccatModel,
      Map<String, String> manifestInfoEntries, DoccatFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);

    artifactMap.put(DOCCAT_MODEL_ENTRY_NAME, doccatModel);
    checkArtifactMap();
  }

  /**
   * Initializes a {@link DoccatModel} instance via a valid {@link InputStream}.
   *
   * @param in The {@link InputStream} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public DoccatModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  /**
   * Initializes a {@link DoccatModel} instance via a valid {@link File}.
   *
   * @param modelFile The {@link File} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public DoccatModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  /**
   * Initializes a {@link DoccatModel} instance via a valid {@link Path}.
   *
   * @param modelPath The {@link Path} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public DoccatModel(Path modelPath) throws IOException {
    this(modelPath.toFile());
  }

  /**
   * Initializes a {@link DoccatModel} instance via a valid {@link URL}.
   *
   * @param modelURL The {@link URL} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public DoccatModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(DOCCAT_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Doccat model is incomplete!");
    }
  }

  /**
   * @return Retrieves the active {@link DoccatFactory}.
   */
  public DoccatFactory getFactory() {
    return (DoccatFactory) this.toolFactory;
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return DoccatFactory.class;
  }

  /**
   * @return Retrieves the active {@link MaxentModel}.
   */
  public MaxentModel getMaxentModel() {
    return (MaxentModel) artifactMap.get(DOCCAT_MODEL_ENTRY_NAME);
  }
}
