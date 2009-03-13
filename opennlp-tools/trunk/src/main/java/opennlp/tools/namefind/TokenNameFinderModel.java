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
import java.util.logging.Logger;

import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ModelUtil;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AggregatedFeatureGenerator;
import opennlp.tools.util.featuregen.FeatureGeneratorFactory;
import opennlp.tools.util.featuregen.FeatureGeneratorResourceProvider;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;
import opennlp.tools.util.model.FeatureGeneratorFactorySerializer;

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

  /**
   * The name of the {@link FeatureGeneratorFactory} implementation class resource. The name
   * must not match the actual class name, but the class must implement the interface.
   */
  private static final String GENERATOR_DESCRIPTOR_ENTRY_NAME = "GeneratorFactory.class";

  private static Logger logger =
        Logger.getLogger(TokenNameFinderModel.class.getName());

  private byte featureGeneratorFactoryClassBytes[];
  
  
  // TODO: Test create generators, to see if they are valid
  public TokenNameFinderModel(String languageCode, AbstractModel nameFinderModel,
      InputStream generatorDescriptorIn, Map<String, Object> resources) throws IOException, InvalidFormatException {
    
    super(languageCode);
    
    if (!isModelValid(nameFinderModel)) {
      throw new IllegalArgumentException("Model not compatible with name finder!");
    }

    artifactMap.put(MAXENT_MODEL_ENTRY_NAME, nameFinderModel);

    // The Class resouce handling is a bit tricky because it is not possible
    // to create a .class file from a Class object.
    // The ClassSerializer is asked to write out the FeatureGeneratorFactory,
    // instead of serializing the Class object it serializes a byte array which
    // was remembered on loading the Class from an InputStream
    
    // If we initialize 
    // In this constructor the serializer will only be asked to write the
    // Class object out, thats why the bytes are stored in a field
    // and then passed to the serializer in the createArtifactSerializer method.
    
    featureGeneratorFactoryClassBytes = ModelUtil.read(generatorDescriptorIn);
    
    FeatureGeneratorFactorySerializer factorySerializer = 
        new FeatureGeneratorFactorySerializer();
    
    FeatureGeneratorFactory factory = 
        factorySerializer.create(new ByteArrayInputStream(featureGeneratorFactoryClassBytes));
    
    artifactMap.put(GENERATOR_DESCRIPTOR_ENTRY_NAME, factory);
    
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
    
   FeatureGeneratorFactory factory = (FeatureGeneratorFactory)
       artifactMap.get(GENERATOR_DESCRIPTOR_ENTRY_NAME);
    
   FeatureGeneratorResourceProvider resourceProvider = new FeatureGeneratorResourceProvider() {

    public Object getResource(String resourceIdentifier) {
      return artifactMap.get(resourceIdentifier);
    }
   };
   
   return factory.createFeatureGenerator(resourceProvider); 
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
    
    FeatureGeneratorFactorySerializer factorySerializer;
    
    if (featureGeneratorFactoryClassBytes != null) {
      factorySerializer = new FeatureGeneratorFactorySerializer(
          featureGeneratorFactoryClassBytes);
    }
    else {
      factorySerializer = new FeatureGeneratorFactorySerializer();
    }
    
    serializers.put("class", factorySerializer);
  }
  
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();
    
    if (!(artifactMap.get(MAXENT_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("Token Name Finder model is incomplete!");
    }
    
    if (!(artifactMap.get(GENERATOR_DESCRIPTOR_ENTRY_NAME) instanceof FeatureGeneratorFactory)) {
      throw new InvalidFormatException("Token Name Finder model is incomplete!");
    }
  }
}
