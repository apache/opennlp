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


package opennlp.tools.sentdetect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.model.GenericModelReader;
import opennlp.tools.dictionary.Dictionary;
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
  private static final String ABBREVIATIONS_ENTRY_NAME = "abbreviations.dictionary";

  private static final String TOKEN_END_PROPERTY = "useTokenEnd";

  public SentenceModel(String languageCode, AbstractModel sentModel,
      boolean useTokenEnd, Dictionary abbreviations, Map<String, String> manifestInfoEntries) {

    super(COMPONENT_NAME, languageCode, manifestInfoEntries);

    artifactMap.put(MAXENT_MODEL_ENTRY_NAME, sentModel);

    setManifestProperty(TOKEN_END_PROPERTY, Boolean.toString(useTokenEnd));

    // Abbreviations are optional
    if (abbreviations != null)
        artifactMap.put(ABBREVIATIONS_ENTRY_NAME, abbreviations);
    
    checkArtifactMap();
  }

  public SentenceModel(String languageCode, AbstractModel sentModel,
      boolean useTokenEnd, Dictionary abbreviations) {
    this (languageCode, sentModel, useTokenEnd, abbreviations, null);
  }
  
  public SentenceModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(MAXENT_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Unable to find " + MAXENT_MODEL_ENTRY_NAME +
          " maxent model!");
    }

    if (!ModelUtil.validateOutcomes(getMaxentModel(), SentenceDetectorME.SPLIT,
        SentenceDetectorME.NO_SPLIT)) {
      throw new InvalidFormatException("The maxent model is not compatible " +
      		"with the sentence detector!");
    }
    
    if (getManifestProperty(TOKEN_END_PROPERTY) == null)
      throw new InvalidFormatException(TOKEN_END_PROPERTY + " is a mandatory property!");

    Object abbreviationsEntry = artifactMap.get(ABBREVIATIONS_ENTRY_NAME);

    if (abbreviationsEntry != null && !(abbreviationsEntry instanceof Dictionary)) {
      throw new InvalidFormatException("Abbreviations dictionary has wrong type!");
    }
  }

  public AbstractModel getMaxentModel() {
    return (AbstractModel) artifactMap.get(MAXENT_MODEL_ENTRY_NAME);
  }

  public Dictionary getAbbreviations() {
    return (Dictionary) artifactMap.get(ABBREVIATIONS_ENTRY_NAME);
  }

  public boolean useTokenEnd() {
    return Boolean.parseBoolean(getManifestProperty(TOKEN_END_PROPERTY));
  }

  public static void main(String[] args) throws FileNotFoundException, IOException, InvalidFormatException {
    if (args.length < 3){
      System.err.println("SentenceModel [-abbreviationsDictionary] [-useTokenEnd] languageCode packageName modelName");
      System.exit(1);
    }

    int ai = 0;

    Dictionary abbreviations = null;
    if ("-abbreviationsDictionary".equals(args[ai])) {
      ai++;
      abbreviations = new Dictionary(new FileInputStream(args[ai++]));
    }

    boolean useTokenEnd = false;
    if ("-useTokenEnd".equals(args[ai])) {
      useTokenEnd = true;
      ai++;
    }

    String languageCode = args[ai++];
    String packageName = args[ai++];
    String modelName = args[ai];

    AbstractModel model = new GenericModelReader(new File(modelName)).getModel();
    SentenceModel packageModel = new SentenceModel(languageCode, model, useTokenEnd, abbreviations);
    packageModel.serialize(new FileOutputStream(packageName));
  }
}
