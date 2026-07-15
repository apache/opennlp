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

package opennlp.tools.depparse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;

/**
 * The persisted form of a trained {@link DependencyParserME}: the transition
 * classification model plus the standard model manifest, serialized and loaded through
 * the same {@link BaseModel} machinery as every other tool model.
 *
 * @see DependencyParserME
 * @since 3.0.0
 */
public class DependencyModel extends BaseModel {

  private static final long serialVersionUID = -2928968185269611443L;

  private static final String COMPONENT_NAME = "DependencyParserME";
  static final String PARSER_MODEL_ENTRY_NAME = "depparse.model";

  /**
   * Initializes a {@link DependencyModel} from a trained transition model.
   *
   * @param languageCode The ISO language code of the training data. Must not be
   *                     {@code null}.
   * @param parserModel The transition classification model. Must not be {@code null}.
   * @param manifestInfoEntries Additional entries for the manifest, or {@code null}.
   * @throws IllegalArgumentException Thrown if {@code parserModel} is {@code null}.
   */
  public DependencyModel(String languageCode, MaxentModel parserModel,
      Map<String, String> manifestInfoEntries) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries);
    if (parserModel == null) {
      throw new IllegalArgumentException("parserModel must not be null");
    }
    artifactMap.put(PARSER_MODEL_ENTRY_NAME, parserModel);
    checkArtifactMap();
  }

  /**
   * Initializes a {@link DependencyModel} from a serialized model.
   *
   * @param in The stream to read the model from. Must not be {@code null}.
   * @throws IOException Thrown if reading fails or the content is not a valid model.
   */
  public DependencyModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  /**
   * Initializes a {@link DependencyModel} from a serialized model file.
   *
   * @param modelFile The file to read the model from. Must not be {@code null}.
   * @throws IOException Thrown if reading fails or the content is not a valid model.
   */
  public DependencyModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  /**
   * Initializes a {@link DependencyModel} from a serialized model file.
   *
   * @param modelPath The path to read the model from. Must not be {@code null}.
   * @throws IOException Thrown if reading fails or the content is not a valid model.
   */
  public DependencyModel(Path modelPath) throws IOException {
    super(COMPONENT_NAME, modelPath);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();
    if (!(artifactMap.get(PARSER_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("The " + PARSER_MODEL_ENTRY_NAME
          + " artifact is missing or not a supported transition model.");
    }
  }

  /**
   * @return The transition classification model. Never {@code null}.
   */
  public MaxentModel getParserModel() {
    return (MaxentModel) artifactMap.get(PARSER_MODEL_ENTRY_NAME);
  }
}
