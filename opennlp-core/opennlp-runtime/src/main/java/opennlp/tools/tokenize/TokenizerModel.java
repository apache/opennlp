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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.BaseModel;
import opennlp.tools.util.model.ModelUtil;

/**
 * The {@link TokenizerModel} is the model used
 * by a learnable {@link Tokenizer}.
 *
 * @see TokenizerME
 * @see TokenizerFactory
 */
public final class TokenizerModel extends BaseModel {

  private static final long serialVersionUID = 42334333400920419L;
  private static final String COMPONENT_NAME = "TokenizerME";
  private static final String TOKENIZER_MODEL_ENTRY = "token.model";

  /**
   * Initializes a {@link TokenizerModel} instance via a {@link MaxentModel} and related resources.
   *
   * @param tokenizerModel The {@link MaxentModel model} to be used.
   * @param manifestInfoEntries Additional information kept in the manifest.
   * @param tokenizerFactory The {@link TokenizerFactory} to be used internally.
   */
  public TokenizerModel(MaxentModel tokenizerModel,
      Map<String, String> manifestInfoEntries, TokenizerFactory tokenizerFactory) {
    super(COMPONENT_NAME, tokenizerFactory.getLanguageCode(), manifestInfoEntries, tokenizerFactory);
    artifactMap.put(TOKENIZER_MODEL_ENTRY, tokenizerModel);
    checkArtifactMap();
  }

  /**
   * Initializes a {@link TokenizerModel} instance via a valid {@link InputStream}.
   *
   * @param in The {@link InputStream} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public TokenizerModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  /**
   * Initializes a {@link TokenizerModel} instance via a valid {@link File}.
   *
   * @param modelFile The {@link File} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public TokenizerModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  /**
   * Initializes a {@link TokenizerModel} instance via a valid {@link Path}.
   *
   * @param modelPath The {@link Path} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public TokenizerModel(Path modelPath) throws IOException {
    this(modelPath.toFile());
  }

  /**
   * Initializes a {@link TokenizerModel} instance via a valid {@link URL}.
   *
   * @param modelURL The {@link URL} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public TokenizerModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  /**
   * Checks if the {@link TokenizerModel} has the right outcomes.
   *
   * @param model The {@link MaxentModel} to be checked.
   * @return {@code true} if the model could be validated, {@code false} otherwise.
   */
  private static boolean isModelCompatible(MaxentModel model) {
    return ModelUtil.validateOutcomes(model, TokenizerME.SPLIT, TokenizerME.NO_SPLIT);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(TOKENIZER_MODEL_ENTRY) instanceof AbstractModel)) {
      throw new InvalidFormatException("Token model is incomplete!");
    }

    if (!isModelCompatible(getMaxentModel())) {
      throw new InvalidFormatException("The maxent model is not compatible with the tokenizer!");
    }
  }

  /**
   * @return Retrieves the active {@link TokenizerFactory}.
   */
  public TokenizerFactory getFactory() {
    return (TokenizerFactory) this.toolFactory;
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return TokenizerFactory.class;
  }

  /**
   * @return Retrieves the model as {@link MaxentModel} instance.
   */
  public MaxentModel getMaxentModel() {
    return (MaxentModel) artifactMap.get(TOKENIZER_MODEL_ENTRY);
  }

  /**
   * @return Retrieves the active abbreviation {@link Dictionary}.
   */
  public Dictionary getAbbreviations() {
    if (getFactory() != null) {
      return getFactory().getAbbreviationDictionary();
    }
    return null;
  }

  /**
   * @return {@code true} if alphanumeric optimization is active, {@code false} otherwise.
   */
  public boolean useAlphaNumericOptimization() {
    return getFactory() != null && getFactory().isUseAlphaNumericOptimization();
  }

  @Override
  public int hashCode() {
    return Objects.hash(artifactMap.get(MANIFEST_ENTRY), artifactMap.get(TOKENIZER_MODEL_ENTRY));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof TokenizerModel model) {
      Map<String, Object> artifactMapToCheck = model.artifactMap;
      AbstractModel abstractModel = (AbstractModel) artifactMapToCheck.get(TOKENIZER_MODEL_ENTRY);

      return artifactMap.get(MANIFEST_ENTRY).equals(artifactMapToCheck.get(MANIFEST_ENTRY)) &&
              artifactMap.get(TOKENIZER_MODEL_ENTRY).equals(abstractModel);
    }
    return false;
  }
}
