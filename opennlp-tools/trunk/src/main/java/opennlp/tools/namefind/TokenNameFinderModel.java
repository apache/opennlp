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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.tools.util.BaseModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ModelUtil;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AggregatedFeatureGenerator;
import opennlp.tools.util.featuregen.Factory;
import opennlp.tools.util.featuregen.FactoryResourceManager;

/**
 * The {@link TokenNameFinderModel} is the model used
 * by a learnable {@link TokenNameFinder}.
 *
 * @see NameFinderME
 */
public class TokenNameFinderModel extends BaseModel {

  private class ByteArraySerializer implements ArtifactSerializer<byte[]> {

    public byte[] create(InputStream in) throws IOException,
        InvalidFormatException {
      
      return ModelUtil.read(in);
    }

    public void serialize(byte[] artifact, OutputStream out) throws IOException {
      out.write(artifact);
    }
  }
  
  private static final String MAXENT_MODEL_ENTRY_NAME = "nameFinder.model";

  private static final String GENERATOR_DESCRIPTOR_ENTRY_NAME = "generator.featuregen";

  private static Logger logger =
        Logger.getLogger(TokenNameFinderModel.class.getName());

  // TODO: Test create generators, to see if they are valid
  public TokenNameFinderModel(String languageCode, AbstractModel nameFinderModel,
      InputStream generatorDescriptorIn, Map<String, Object> resources) throws IOException {
    
    super(languageCode);
    
    if (!isModelValid(nameFinderModel)) {
      throw new IllegalArgumentException("Model not compatible with name finder!");
    }

    artifactMap.put(MAXENT_MODEL_ENTRY_NAME, nameFinderModel);

    artifactMap.put(GENERATOR_DESCRIPTOR_ENTRY_NAME, ModelUtil.read(generatorDescriptorIn));
    
    // The resource map must not contain key which are already taken
    // like the name finder maxent model name
    if (resources.containsKey(MAXENT_MODEL_ENTRY_NAME) ||
        resources.containsKey(GENERATOR_DESCRIPTOR_ENTRY_NAME)) {
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

  /**
   * Creates the {@link AdaptiveFeatureGenerator}. Usually this
   * is a set of generators contained in the {@link AggregatedFeatureGenerator}.
   *
   * Note:
   * The generators are created on every call to this method.
   *
   * @return
   */
  public AdaptiveFeatureGenerator createFeatureGenerators() {

    InputStream descriptorIn = new ByteArrayInputStream(
        (byte[]) artifactMap.get(GENERATOR_DESCRIPTOR_ENTRY_NAME));

    AdaptiveFeatureGenerator generator = null;
    try {
      generator = Factory.create(descriptorIn, new FactoryResourceManager() {

        public Object getResource(String key) {
          return artifactMap.get(key);
        }
      });
    } catch (IOException e) {
      logger.log(Level.SEVERE,
          "Sorry, that reading from memory can go wrong.", e);
    }
    catch (InvalidFormatException e) {
      // that should never happend to test against invalid format the
      // feature generators where created on instanciation
      
      // TODO: Log error
    }

    return generator;
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
    
    serializers.put("featuregen", new ByteArraySerializer());
  }
  
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();
    
    if (!(artifactMap.get(MAXENT_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Token Name Finder model is incomplete!");
    }
    
    if (!(artifactMap.get(GENERATOR_DESCRIPTOR_ENTRY_NAME) instanceof byte[])) {
      throw new InvalidFormatException("Token Name Finder model is incomplete!");
    }
  }
}
