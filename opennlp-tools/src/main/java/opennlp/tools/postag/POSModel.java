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


package opennlp.tools.postag;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
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

  /**
   * @deprecated Use
   *             {@link #POSModel(String, AbstractModel, Map, POSTaggerFactory)}
   *             instead.
   */
  public POSModel(String languageCode, AbstractModel posModel,
      POSDictionary tagDictionary, Dictionary ngramDict, Map<String, String> manifestInfoEntries) {

    this(languageCode, posModel, manifestInfoEntries, new POSTaggerFactory(
        ngramDict, tagDictionary));
  }

  /**
   * @deprecated Use
   *             {@link #POSModel(String, AbstractModel, Map, POSTaggerFactory)}
   *             instead.
   */
  public POSModel(String languageCode, AbstractModel posModel,
      POSDictionary tagDictionary, Dictionary ngramDict) {
    this(languageCode, posModel, null, new POSTaggerFactory(ngramDict,
        tagDictionary));
  }
  
  public POSModel(String languageCode, AbstractModel posModel,
      Map<String, String> manifestInfoEntries, POSTaggerFactory posFactory) {

    super(COMPONENT_NAME, languageCode, manifestInfoEntries, posFactory);

    if (posModel == null)
        throw new IllegalArgumentException("The maxentPosModel param must not be null!");

    artifactMap.put(POS_MODEL_ENTRY_NAME, posModel);
    checkArtifactMap();
  }
  
  public POSModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
  }
  
  public POSModel(File modelFile) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelFile);
  }
  
  public POSModel(URL modelURL) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return POSTaggerFactory.class;
  }

  @Override
  @SuppressWarnings("rawtypes")
  protected void createArtifactSerializers(
      Map<String, ArtifactSerializer> serializers) {

    super.createArtifactSerializers(serializers);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(POS_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("POS model is incomplete!");
    }
  }

  public AbstractModel getPosModel() {
    return (AbstractModel) artifactMap.get(POS_MODEL_ENTRY_NAME);
  }

  /**
   * Retrieves the tag dictionary.
   * 
   * @return tag dictionary or null if not used
   * 
   * @deprecated Use {@link POSModel#getFactory()} to get a
   *             {@link POSTaggerFactory} and
   *             {@link POSTaggerFactory#getTagDictionary()} to get a
   *             {@link TagDictionary}.
   * 
   * @throws IllegalStateException
   *           if the TagDictionary is not an instance of POSDictionary
   */
  public POSDictionary getTagDictionary() {
    if (getFactory() != null) {
      TagDictionary dict = getFactory().getTagDictionary();
      if (dict != null) {
        if (dict instanceof POSDictionary) {
          return (POSDictionary) dict;
        }
        String clazz = dict.getClass().getCanonicalName();
        throw new IllegalStateException("Can not get a dictionary of type "
            + clazz
            + " using the deprecated method POSModel.getTagDictionary() "
            + "because it can only return dictionaries of type POSDictionary. "
            + "Use POSModel.getFactory().getTagDictionary() instead.");
      }
    }
    return null;
  }
  
  public POSTaggerFactory getFactory() {
    return (POSTaggerFactory) this.toolFactory;
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
