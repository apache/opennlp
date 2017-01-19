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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.UncloseableInputStream;

/**
 * The factory that provides POS Tagger default implementations and resources
 */
public class POSTaggerFactory extends BaseToolFactory {

  private static final String TAG_DICTIONARY_ENTRY_NAME = "tags.tagdict";
  private static final String NGRAM_DICTIONARY_ENTRY_NAME = "ngram.dictionary";

  protected Dictionary ngramDictionary;
  protected TagDictionary posDictionary;

  /**
   * Creates a {@link POSTaggerFactory} that provides the default implementation
   * of the resources.
   */
  public POSTaggerFactory() {
  }

  /**
   * Creates a {@link POSTaggerFactory}. Use this constructor to
   * programmatically create a factory.
   *
   * @param ngramDictionary
   * @param posDictionary
   */
  public POSTaggerFactory(Dictionary ngramDictionary,
      TagDictionary posDictionary) {
    this.init(ngramDictionary, posDictionary);
  }

  protected void init(Dictionary ngramDictionary, TagDictionary posDictionary) {
    this.ngramDictionary = ngramDictionary;
    this.posDictionary = posDictionary;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    Map<String, ArtifactSerializer> serializers = super.createArtifactSerializersMap();
    POSDictionarySerializer.register(serializers);
    // the ngram Dictionary uses a base serializer, we don't need to add it here.
    return serializers;
  }

  @Override
  public Map<String, Object> createArtifactMap() {
    Map<String, Object> artifactMap = super.createArtifactMap();

    if (posDictionary != null)
      artifactMap.put(TAG_DICTIONARY_ENTRY_NAME, posDictionary);

    if (ngramDictionary != null)
      artifactMap.put(NGRAM_DICTIONARY_ENTRY_NAME, ngramDictionary);

    return artifactMap;
  }

  public TagDictionary createTagDictionary(File dictionary)
      throws IOException {
    return createTagDictionary(new FileInputStream(dictionary));
  }

  public TagDictionary createTagDictionary(InputStream in)
      throws IOException {
    return POSDictionary.create(in);
  }

  public void setTagDictionary(TagDictionary dictionary) {
    if (artifactProvider != null) {
      throw new IllegalStateException(
          "Can not set tag dictionary while using artifact provider.");
    }
    this.posDictionary = dictionary;
  }

  public TagDictionary getTagDictionary() {
    if (this.posDictionary == null && artifactProvider != null)
      this.posDictionary = artifactProvider.getArtifact(TAG_DICTIONARY_ENTRY_NAME);
    return this.posDictionary;
  }

  public Dictionary getDictionary() {
    if (this.ngramDictionary == null && artifactProvider != null)
      this.ngramDictionary = artifactProvider.getArtifact(NGRAM_DICTIONARY_ENTRY_NAME);
    return this.ngramDictionary;
  }

  public void setDictionary(Dictionary ngramDict) {
    if (artifactProvider != null) {
      throw new IllegalStateException(
          "Can not set ngram dictionary while using artifact provider.");
    }
    this.ngramDictionary = ngramDict;
  }

  public POSContextGenerator getPOSContextGenerator() {
    return new DefaultPOSContextGenerator(0, getDictionary());
  }

  public POSContextGenerator getPOSContextGenerator(int cacheSize) {
    return new DefaultPOSContextGenerator(cacheSize, getDictionary());
  }

  public SequenceValidator<String> getSequenceValidator() {
    return new DefaultPOSSequenceValidator(getTagDictionary());
  }

  static class POSDictionarySerializer implements ArtifactSerializer<POSDictionary> {

    public POSDictionary create(InputStream in) throws IOException {
      return POSDictionary.create(new UncloseableInputStream(in));
    }

    public void serialize(POSDictionary artifact, OutputStream out)
        throws IOException {
      artifact.serialize(out);
    }

    @SuppressWarnings("rawtypes")
    static void register(Map<String, ArtifactSerializer> factories) {
      factories.put("tagdict", new POSDictionarySerializer());
    }
  }

  protected void validatePOSDictionary(POSDictionary posDict,
      AbstractModel posModel) throws InvalidFormatException {
    Set<String> dictTags = new HashSet<>();

    for (String word : posDict) {
      Collections.addAll(dictTags, posDict.getTags(word));
    }

    Set<String> modelTags = new HashSet<>();

    for (int i = 0; i < posModel.getNumOutcomes(); i++) {
      modelTags.add(posModel.getOutcome(i));
    }

    if (!modelTags.containsAll(dictTags)) {
      StringBuilder unknownTag = new StringBuilder();
      for (String d : dictTags) {
        if (!modelTags.contains(d)) {
          unknownTag.append(d).append(" ");
        }
      }
      throw new InvalidFormatException("Tag dictionary contains tags "
          + "which are unknown by the model! The unknown tags are: "
          + unknownTag.toString());
    }
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {

    // Ensure that the tag dictionary is compatible with the model

    Object tagdictEntry = this.artifactProvider
        .getArtifact(TAG_DICTIONARY_ENTRY_NAME);

    if (tagdictEntry != null) {
      if (tagdictEntry instanceof POSDictionary) {
        if (!this.artifactProvider.isLoadedFromSerialized()) {
          AbstractModel posModel = this.artifactProvider
              .getArtifact(POSModel.POS_MODEL_ENTRY_NAME);
          POSDictionary posDict = (POSDictionary) tagdictEntry;
          validatePOSDictionary(posDict, posModel);
        }
      } else {
        throw new InvalidFormatException(
            "POSTag dictionary has wrong type!");
      }
    }

    Object ngramDictEntry = this.artifactProvider
        .getArtifact(NGRAM_DICTIONARY_ENTRY_NAME);

    if (ngramDictEntry != null && !(ngramDictEntry instanceof Dictionary)) {
      throw new InvalidFormatException("NGram dictionary has wrong type!");
    }

  }

  public static POSTaggerFactory create(String subclassName,
      Dictionary ngramDictionary, TagDictionary posDictionary)
      throws InvalidFormatException {
    if (subclassName == null) {
      // will create the default factory
      return new POSTaggerFactory(ngramDictionary, posDictionary);
    }
    try {
      POSTaggerFactory theFactory = ExtensionLoader.instantiateExtension(
          POSTaggerFactory.class, subclassName);
      theFactory.init(ngramDictionary, posDictionary);
      return theFactory;
    } catch (Exception e) {
      String msg = "Could not instantiate the " + subclassName
          + ". The initialization throw an exception.";
      System.err.println(msg);
      e.printStackTrace();
      throw new InvalidFormatException(msg, e);
    }

  }

  public TagDictionary createEmptyTagDictionary() {
    this.posDictionary = new POSDictionary(true);
    return this.posDictionary;
  }
}
