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
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import opennlp.model.AbstractModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;
import opennlp.tools.util.model.UncloseableInputStream;

/**
 * The {@link POSModel} is the model used
 * by a learnable {@link POSTagger}.
 *
 * @see POSTaggerME
 */
public final class POSModel extends BaseModel {

  static class POSDictionarySerializer implements ArtifactSerializer<POSDictionary> {

    public POSDictionary create(InputStream in) throws IOException,
        InvalidFormatException {
      return POSDictionary.create(new UncloseableInputStream(in));
    }

    public void serialize(POSDictionary artifact, OutputStream out)
        throws IOException {
      artifact.serialize(out);
    }

    @SuppressWarnings("unchecked")
    static void register(Map<String, ArtifactSerializer> factories) {
      factories.put("tagdict", new POSDictionarySerializer());
    }
  }

  private static final String COMPONENT_NAME = "POSTaggerME";
  
  private static final String POS_MODEL_ENTRY_NAME = "pos.model";
  private static final String TAG_DICTIONARY_ENTRY_NAME = "tags.tagdict";
  private static final String NGRAM_DICTIONARY_ENTRY_NAME = "ngram.dictionary";

  public POSModel(String languageCode, AbstractModel posModel,
      POSDictionary tagDictionary, Dictionary ngramDict, Map<String, String> manifestInfoEntries) {

    super(COMPONENT_NAME, languageCode, manifestInfoEntries);

    if (posModel == null)
        throw new IllegalArgumentException("The maxentPosModel param must not be null!");

    artifactMap.put(POS_MODEL_ENTRY_NAME, posModel);

    if (tagDictionary != null)
      artifactMap.put(TAG_DICTIONARY_ENTRY_NAME, tagDictionary);

    if (ngramDict != null)
      artifactMap.put(NGRAM_DICTIONARY_ENTRY_NAME, ngramDict);
    
    checkArtifactMap();
  }

  public POSModel(String languageCode, AbstractModel posModel,
      POSDictionary tagDictionary, Dictionary ngramDict) {
    this (languageCode, posModel, tagDictionary, ngramDict, null);
  }
  
  public POSModel(InputStream in) throws IOException, InvalidFormatException {
    super(COMPONENT_NAME, in);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void createArtifactSerializers(
      Map<String, ArtifactSerializer> serializers) {

    super.createArtifactSerializers(serializers);

    POSDictionarySerializer.register(serializers);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(POS_MODEL_ENTRY_NAME) instanceof AbstractModel)) {
      throw new InvalidFormatException("POS model is incomplete!");
    }

    // Ensure that the tag dictionary is compatible with the model
    Object tagdictEntry = artifactMap.get(TAG_DICTIONARY_ENTRY_NAME);

    if (tagdictEntry != null) {
      if (tagdictEntry instanceof POSDictionary) {
        POSDictionary posDict = (POSDictionary) tagdictEntry;
        
        Set<String> dictTags = new HashSet<String>();
        
        for (String word : posDict) {
          Collections.addAll(dictTags, posDict.getTags(word)); 
        }
        
        Set<String> modelTags = new HashSet<String>();
        
        AbstractModel posModel = getPosModel();
        
        for  (int i = 0; i < posModel.getNumOutcomes(); i++) {
          modelTags.add(posModel.getOutcome(i));
        }
        
        if (!modelTags.containsAll(dictTags)) {
          throw new InvalidFormatException("Tag dictioinary contains tags " +
          		"which are unkown by the model!");
        }
      }
      else {
        throw new InvalidFormatException("Abbreviations dictionary has wrong type!");
      }
    }

    Object ngramDictEntry = artifactMap.get(NGRAM_DICTIONARY_ENTRY_NAME);

    if (ngramDictEntry != null && !(ngramDictEntry instanceof Dictionary)) {
      throw new InvalidFormatException("NGram dictionary has wrong type!");
    }
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
    return (POSDictionary) artifactMap.get(TAG_DICTIONARY_ENTRY_NAME);
  }

  /**
   * Retrieves the ngram dictionary.
   *
   * @return ngram dictionary or null if not used
   */
  public Dictionary getNgramDictionary() {
    return (Dictionary) artifactMap.get(NGRAM_DICTIONARY_ENTRY_NAME);
  }
}
