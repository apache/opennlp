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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.maxent.io.BinaryGISModelReader;
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
 */
public final class TokenizerModel extends BaseModel {

  private static final String COMPONENT_NAME = "TokenizerME";

  private static final String TOKENIZER_MODEL_ENTRY = "token.model";

  /**
   * Initializes the current instance.
   *
   * @param tokenizerModel the model
   * @param manifestInfoEntries the manifest
   * @param tokenizerFactory the factory
   */
  public TokenizerModel(MaxentModel tokenizerModel,
      Map<String, String> manifestInfoEntries, TokenizerFactory tokenizerFactory) {
    super(COMPONENT_NAME, tokenizerFactory.getLanguageCode(), manifestInfoEntries, tokenizerFactory);
    artifactMap.put(TOKENIZER_MODEL_ENTRY, tokenizerModel);
    checkArtifactMap();
  }

  /**
   * Initializes the current instance.
   *
   * @param language the language the tokenizer should use
   * @param tokenizerMaxentModel the statistical model of the tokenizer
   * @param abbreviations the dictionary containing the abbreviations
   * @param useAlphaNumericOptimization if true alpha numeric optimization is enabled, otherwise not
   * @param manifestInfoEntries the additional meta data which should be written into manifest
   *
   * @deprecated Use
   *             {@link TokenizerModel#TokenizerModel(MaxentModel, Map, TokenizerFactory)}
   *             instead and pass in a {@link TokenizerFactory}.
   */
  public TokenizerModel(String language, MaxentModel tokenizerMaxentModel,
      Dictionary abbreviations, boolean useAlphaNumericOptimization,
      Map<String, String> manifestInfoEntries) {
    this(tokenizerMaxentModel, manifestInfoEntries,
        new TokenizerFactory(language, abbreviations, useAlphaNumericOptimization, null));
  }

  /**
   * Initializes the current instance.
   *
   * @param language the language the tokenizer should use
   * @param tokenizerMaxentModel the statistical model of the tokenizer
   * @param useAlphaNumericOptimization if true alpha numeric optimization is enabled, otherwise not
   * @param manifestInfoEntries the additional meta data which should be written into manifest
   *
   * @deprecated Use
   *             {@link TokenizerModel#TokenizerModel(MaxentModel, Map, TokenizerFactory)}
   *             instead and pass in a {@link TokenizerFactory}.
   */
  public TokenizerModel(String language, AbstractModel tokenizerMaxentModel,
      boolean useAlphaNumericOptimization, Map<String, String> manifestInfoEntries) {
    this(language, tokenizerMaxentModel, null, useAlphaNumericOptimization, manifestInfoEntries);
  }

  /**
   * Initializes the current instance.
   *
   * @param language the language the tokenizer should use
   * @param tokenizerMaxentModel the statistical model of the tokenizer
   * @param useAlphaNumericOptimization if true alpha numeric optimization is enabled, otherwise not
   *
   * @deprecated Use
   *             {@link TokenizerModel#TokenizerModel(MaxentModel, Map, TokenizerFactory)}
   *             instead and pass in a {@link TokenizerFactory}.
   */
  public TokenizerModel(String language, AbstractModel tokenizerMaxentModel,
      boolean useAlphaNumericOptimization) {
    this(language, tokenizerMaxentModel, useAlphaNumericOptimization, null);
  }

  /**
   * Initializes the current instance.
   *
   * @param in the Input Stream to load the model from
   *
   * @throws IOException if reading from the stream fails in anyway
   * @throws InvalidFormatException if the stream doesn't have the expected format
   */
  public TokenizerModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  /**
   * Initializes the current instance.
   *
   * @param modelFile the file containing the tokenizer model
   *
   * @throws IOException if reading from the stream fails in anyway
   */
  public TokenizerModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  /**
   * Initializes the current instance.
   *
   * @param modelURL the URL pointing to the tokenizer model
   *
   * @throws IOException if reading from the stream fails in anyway
   */
  public TokenizerModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  /**
   * Checks if the tokenizer model has the right outcomes.
   *
   * @param model
   * @return
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

  public TokenizerFactory getFactory() {
    return (TokenizerFactory) this.toolFactory;
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return TokenizerFactory.class;
  }

  public MaxentModel getMaxentModel() {
    return (MaxentModel) artifactMap.get(TOKENIZER_MODEL_ENTRY);
  }

  public Dictionary getAbbreviations() {
    if (getFactory() != null) {
      return getFactory().getAbbreviationDictionary();
    }
    return null;
  }

  public boolean useAlphaNumericOptimization() {
    return getFactory() != null && getFactory().isUseAlphaNumericOptmization();
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 3){
      System.err.println("TokenizerModel [-alphaNumericOptimization] languageCode packageName modelName");
      System.exit(1);
    }

    int ai = 0;

    boolean alphaNumericOptimization = false;

    if ("-alphaNumericOptimization".equals(args[ai])) {
      alphaNumericOptimization = true;
      ai++;
    }

    String languageCode = args[ai++];
    String packageName = args[ai++];
    String modelName = args[ai];

    AbstractModel model = new BinaryGISModelReader(new DataInputStream(
        new FileInputStream(modelName))).getModel();

    TokenizerModel packageModel = new TokenizerModel(model, null,
      TokenizerFactory.create(null, languageCode, null, alphaNumericOptimization, null));

    OutputStream out = null;
    try {
      out = new FileOutputStream(packageName);
      packageModel.serialize(out);
    }
    finally {
      if (out != null)
        out.close();
    }
  }
}
