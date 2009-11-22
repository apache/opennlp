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


package opennlp.tools.namefind;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ModelUtil;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;

/**
 * The {@link TokenNameFinderModel} is the model used
 * by a learnable {@link TokenNameFinder}.
 *
 * @see NameFinderME
 */
public class TokenNameFinderModel extends BaseModel {

  private static final String MAXENT_MODEL_ENTRY_NAME = "nameFinder.model";

  private static Logger logger =
        Logger.getLogger(TokenNameFinderModel.class.getName());
  
  public TokenNameFinderModel(String languageCode, AbstractModel nameFinderModel,
      Map<String, Object> resources) throws IOException, InvalidFormatException {
    
    super(languageCode);
    
    if (!isModelValid(nameFinderModel)) {
      throw new IllegalArgumentException("Model not compatible with name finder!");
    }

    artifactMap.put(MAXENT_MODEL_ENTRY_NAME, nameFinderModel);
    
    // The resource map must not contain key which are already taken
    // like the name finder maxent model name
    if (resources.containsKey(MAXENT_MODEL_ENTRY_NAME)) {
      throw new IllegalArgumentException();
    }
    
    // TODO: Add checks to not put resources where no serializer exists,
    // make that case fail here, should be done in the BaseModel
    artifactMap.putAll(resources);
  }

  public TokenNameFinderModel(InputStream in) throws IOException, InvalidFormatException {
    super(in);
  }
  
  /**
   * Retrieves the {@link TokenNameFinder} model.
   *
   * @return
   */
  public AbstractModel getNameFinderModel() {
    return (AbstractModel) artifactMap.get(MAXENT_MODEL_ENTRY_NAME);
  }

  private static boolean isModelValid(MaxentModel model) {

    return ModelUtil.validateOutcomes(model, NameFinderME.START) ||
        ModelUtil.validateOutcomes(model, NameFinderME.OTHER) ||
        ModelUtil.validateOutcomes(model, NameFinderME.START, NameFinderME.OTHER) ||
        ModelUtil.validateOutcomes(model, NameFinderME.START, NameFinderME.CONTINUE) ||
        ModelUtil.validateOutcomes(model, NameFinderME.START, NameFinderME.CONTINUE,
            NameFinderME.OTHER);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void createArtifactSerializers(Map<String, ArtifactSerializer> serializers) {
    super.createArtifactSerializers(serializers);
  }
  
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();
    
    if (!(artifactMap.get(MAXENT_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Token Name Finder model is incomplete!");
    }
  }
}
