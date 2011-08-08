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


package opennlp.tools.tokenize;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import opennlp.maxent.io.BinaryGISModelReader;
import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.tools.dictionary.Dictionary;
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
  private static final String ABBREVIATIONS_ENTRY_NAME = "abbreviations.dictionary";

  private static final String USE_ALPHA_NUMERIC_OPTIMIZATION =
      "useAlphaNumericOptimization";

  /**
   * Initializes the current instance.
   *
   * @param tokenizerMaxentModel
   * @param useAlphaNumericOptimization
   */
  public TokenizerModel(String language, AbstractModel tokenizerMaxentModel,
      Dictionary abbreviations, boolean useAlphaNumericOptimization,
      Map<String, String> manifestInfoEntries) {
    super(COMPONENT_NAME, language, manifestInfoEntries);

    artifactMap.put(TOKENIZER_MODEL_ENTRY, tokenizerMaxentModel);

    setManifestProperty(USE_ALPHA_NUMERIC_OPTIMIZATION,
        Boolean.toString(useAlphaNumericOptimization));

    // Abbreviations are optional
    if (abbreviations != null)
      artifactMap.put(ABBREVIATIONS_ENTRY_NAME, abbreviations);
    
    checkArtifactMap();
  }

  /**
   * Initializes the current instance.
   *
   * @param language
   * @param tokenizerMaxentModel
   * @param useAlphaNumericOptimization
   * @param manifestInfoEntries
   */
  public TokenizerModel(String language, AbstractModel tokenizerMaxentModel,
      boolean useAlphaNumericOptimization, Map<String, String> manifestInfoEntries) {
    this(language, tokenizerMaxentModel, null, useAlphaNumericOptimization, manifestInfoEntries);
  }

  /**
   * Initializes the current instance.
   *
   * @param language
   * @param tokenizerMaxentModel
   * @param useAlphaNumericOptimization
   */
  public TokenizerModel(String language, AbstractModel tokenizerMaxentModel,
      boolean useAlphaNumericOptimization) {
    this(language, tokenizerMaxentModel, useAlphaNumericOptimization, null);
  }
  
  /**
   * Initializes the current instance.
   *
   * @param in
   *
   * @throws IOException
   * @throws InvalidFormatException
   */
  public TokenizerModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
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

    if (getManifestProperty(USE_ALPHA_NUMERIC_OPTIMIZATION) == null) {
      throw new InvalidFormatException("The " + USE_ALPHA_NUMERIC_OPTIMIZATION + " parameter " +
          "cannot be found!");
    }
    
    Object abbreviationsEntry = artifactMap.get(ABBREVIATIONS_ENTRY_NAME);

    if (abbreviationsEntry != null && !(abbreviationsEntry instanceof Dictionary)) {
      throw new InvalidFormatException("Abbreviations dictionary has wrong type!");
    }
  }

  public AbstractModel getMaxentModel() {
    return (AbstractModel) artifactMap.get(TOKENIZER_MODEL_ENTRY);
  }
  
  public Dictionary getAbbreviations() {
    return (Dictionary) artifactMap.get(ABBREVIATIONS_ENTRY_NAME);
  }

  public boolean useAlphaNumericOptimization() {
    String optimization = getManifestProperty(USE_ALPHA_NUMERIC_OPTIMIZATION);

    return Boolean.parseBoolean(optimization);
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

    TokenizerModel packageModel = new TokenizerModel(languageCode, model,
        alphaNumericOptimization);

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
