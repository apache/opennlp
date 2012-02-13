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


package opennlp.tools.postag;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactProvider;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;

/**
 * The {@link POSModel} is the model used
 * by a learnable {@link POSTagger}.
 *
 * @see POSTaggerME
 */
public final class POSModel extends BaseModel {

  private static final String COMPONENT_NAME = "POSTaggerME";
  
  public static final String POS_MODEL_ENTRY_NAME = "pos.model";
  private static final String FACTORY_NAME = "pos.factory";

  private POSTaggerFactory posTaggerFactory = null;

  public POSModel(String languageCode, AbstractModel posModel,
      POSDictionary tagDictionary, Dictionary ngramDict, Map<String, String> manifestInfoEntries) {

    this(languageCode, posModel, tagDictionary, ngramDict, manifestInfoEntries, null);
  }

  public POSModel(String languageCode, AbstractModel posModel,
      POSDictionary tagDictionary, Dictionary ngramDict) {
    this (languageCode, posModel, tagDictionary, ngramDict, null, null);
  }
  
  public POSModel(String languageCode, AbstractModel posModel,
      POSDictionary tagDictionary, Dictionary ngramDict, Map<String, String> manifestInfoEntries, POSTaggerFactory posFactory) {

    super(COMPONENT_NAME, languageCode, manifestInfoEntries);

    if (posModel == null)
        throw new IllegalArgumentException("The maxentPosModel param must not be null!");

    artifactMap.put(POS_MODEL_ENTRY_NAME, posModel);

    // The factory is optional
    if (posFactory!=null) {
      setManifestProperty(FACTORY_NAME, posFactory.getClass().getCanonicalName());
      artifactMap.putAll(posFactory.createArtifactMap());
    }
    
    loadArtifactSerializers();
    checkArtifactMap();
  }

  public POSModel(String languageCode, AbstractModel posModel,
      POSDictionary tagDictionary, Dictionary ngramDict, POSTaggerFactory f) {
    this (languageCode, posModel, tagDictionary, ngramDict, null, f);
  }
  
  public POSModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
    loadArtifactSerializers();
    finishLoadingArtifacts(in);
    checkArtifactMap();
  }

  @Override
  @SuppressWarnings("rawtypes")
  protected void createArtifactSerializers(
      Map<String, ArtifactSerializer> serializers) {

    super.createArtifactSerializers(serializers);

    if(getFactory() != null)
      serializers.putAll(getFactory().createArtifactSerializersMap());
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(POS_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("POS model is incomplete!");
    }
    
    // validate the factory
    String factoryName = getManifestProperty(FACTORY_NAME);
    if(factoryName != null) {
      try {
        Class.forName(factoryName);
      } catch (ClassNotFoundException e) {
        throw new InvalidFormatException("Could not find the POS factory class: " + factoryName);
      }
    }
    
    getFactory().validateArtifactMap();
  }

  public AbstractModel getPosModel() {
    return (AbstractModel) artifactMap.get(POS_MODEL_ENTRY_NAME);
  }

  /**
   * Retrieves the tag dictionary.
   *
   * @return tag dictionary or null if not used
   */
  public POSDictionary getTagDictionary() {
    if(getFactory() != null)
      return getFactory().getPOSDictionary();
    return null;
  }
  
  public POSTaggerFactory getFactory() {
    if(this.posTaggerFactory != null) 
      return this.posTaggerFactory;
    String factoryName = getManifestProperty(FACTORY_NAME);
    POSTaggerFactory theFactory = null;
    Class<?> factoryClass = null;
    if(factoryName != null) {
      try {
        factoryClass = Class.forName(factoryName);
      } catch (ClassNotFoundException e) {
        // already validated
        return null;
      }
    }
    
    Constructor<?> constructor = null;
    if(factoryClass != null) {
      try {
        constructor = factoryClass.getConstructor(ArtifactProvider.class);
        theFactory = (POSTaggerFactory) constructor.newInstance(this);
      } catch (NoSuchMethodException e) {
        // ignore, will try another constructor
      } catch (Exception e) {
        throw new IllegalArgumentException("Could not load POS Factory using Dictionary, POSDictionary constructor: " + factoryName, e);
      }
      if(theFactory == null) {
        try {
          factoryClass.getConstructor();
          try {
            theFactory = (POSTaggerFactory) constructor.newInstance();
          } catch (Exception e) {
            throw new IllegalArgumentException("Could not load POS Factory using default constructor: " + factoryName, e);
          }
        } catch (NoSuchMethodException e) {
          // we couldn't load the class... raise an exception
          throw new IllegalArgumentException("Could not load POS Factory: " + factoryName, e);
        }
      }
    }
    return theFactory;
  }

  /**
   * Retrieves the ngram dictionary.
   *
   * @return ngram dictionary or null if not used
   */
  public Dictionary getNgramDictionary() {
    if(getFactory() != null)
      return getFactory().getDictionary();
    return null;
  }
}
