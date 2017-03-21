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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.Version;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AggregatedFeatureGenerator;
import opennlp.tools.util.featuregen.GeneratorFactory;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.UncloseableInputStream;

/**
 * The factory that provides POS Tagger default implementations and resources
 */
public class POSTaggerFactory extends BaseToolFactory {

  private static final String TAG_DICTIONARY_ENTRY_NAME = "tags.tagdict";
  private static final String NGRAM_DICTIONARY_ENTRY_NAME = "ngram.dictionary";


  protected Dictionary ngramDictionary;
  private byte[] featureGeneratorBytes;
  private Map<String, Object> resources;
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
   *
   * @deprecated this constructor is here for backward compatibility and
   *             is not functional anymore in the training of 1.8.x series models
   */
  @Deprecated
  public POSTaggerFactory(Dictionary ngramDictionary, TagDictionary posDictionary) {
    this.init(ngramDictionary, posDictionary);

    // TODO: This could be made functional by creating some default feature generation
    // which uses the dictionary ...
  }

  public POSTaggerFactory(byte[] featureGeneratorBytes, final Map<String, Object> resources,
                          TagDictionary posDictionary) {
    this.featureGeneratorBytes = featureGeneratorBytes;

    if (this.featureGeneratorBytes == null) {
      this.featureGeneratorBytes = loadDefaultFeatureGeneratorBytes();
    }

    this.resources = resources;
    this.posDictionary = posDictionary;
  }

  @Deprecated // will be removed when only 8 series models are supported
  protected void init(Dictionary ngramDictionary, TagDictionary posDictionary) {
    this.ngramDictionary = ngramDictionary;
    this.posDictionary = posDictionary;
  }

  protected void init(byte[] featureGeneratorBytes, final Map<String, Object> resources,
                      TagDictionary posDictionary) {
    this.featureGeneratorBytes = featureGeneratorBytes;
    this.resources = resources;
    this.posDictionary = posDictionary;
  }
  private static byte[] loadDefaultFeatureGeneratorBytes() {

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = TokenNameFinderFactory.class.getResourceAsStream(
        "/opennlp/tools/postag/pos-default-features.xml")) {

      if (in == null) {
        throw new IllegalStateException("Classpath must contain pos-default-features.xml file!");
      }

      byte[] buf = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        bytes.write(buf, 0, len);
      }
    }
    catch (IOException e) {
      throw new IllegalStateException("Failed reading from pos-default-features.xml file on classpath!");
    }

    return bytes.toByteArray();
  }

  /**
   * Creates the {@link AdaptiveFeatureGenerator}. Usually this
   * is a set of generators contained in the {@link AggregatedFeatureGenerator}.
   *
   * Note:
   * The generators are created on every call to this method.
   *
   * @return the feature generator or null if there is no descriptor in the model
   */
  public AdaptiveFeatureGenerator createFeatureGenerators() {

    if (featureGeneratorBytes == null && artifactProvider != null) {
      featureGeneratorBytes = artifactProvider.getArtifact(
          POSModel.GENERATOR_DESCRIPTOR_ENTRY_NAME);
    }

    if (featureGeneratorBytes == null) {
      featureGeneratorBytes = loadDefaultFeatureGeneratorBytes();
    }

    InputStream descriptorIn = new ByteArrayInputStream(featureGeneratorBytes);

    AdaptiveFeatureGenerator generator;
    try {
      generator = GeneratorFactory.create(descriptorIn, key -> {
        if (artifactProvider != null) {
          return artifactProvider.getArtifact(key);
        }
        else {
          return resources.get(key);
        }
      });
    } catch (InvalidFormatException e) {
      // It is assumed that the creation of the feature generation does not
      // fail after it succeeded once during model loading.

      // But it might still be possible that such an exception is thrown,
      // in this case the caller should not be forced to handle the exception
      // and a Runtime Exception is thrown instead.

      // If the re-creation of the feature generation fails it is assumed
      // that this can only be caused by a programming mistake and therefore
      // throwing a Runtime Exception is reasonable

      throw new IllegalStateException(); // FeatureGeneratorCreationError(e);
    } catch (IOException e) {
      throw new IllegalStateException("Reading from mem cannot result in an I/O error", e);
    }

    return generator;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    Map<String, ArtifactSerializer> serializers = super.createArtifactSerializersMap();


    // NOTE: This is only needed for old models and this if can be removed if support is dropped
    POSDictionarySerializer.register(serializers);

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

  protected Map<String, Object> getResources() {


    if (resources != null) {
      return resources;
    }

    return Collections.emptyMap();
  }

  protected byte[] getFeatureGenerator() {
    return featureGeneratorBytes;
  }

  public TagDictionary getTagDictionary() {
    if (this.posDictionary == null && artifactProvider != null)
      this.posDictionary = artifactProvider.getArtifact(TAG_DICTIONARY_ENTRY_NAME);
    return this.posDictionary;
  }

  /**
   * @deprecated this will be reduced in visibility and later removed
   */
  @Deprecated
  public Dictionary getDictionary() {
    if (this.ngramDictionary == null && artifactProvider != null)
      this.ngramDictionary = artifactProvider.getArtifact(NGRAM_DICTIONARY_ENTRY_NAME);
    return this.ngramDictionary;
  }

  @Deprecated
  public void setDictionary(Dictionary ngramDict) {
    if (artifactProvider != null) {
      throw new IllegalStateException(
          "Can not set ngram dictionary while using artifact provider.");
    }
    this.ngramDictionary = ngramDict;
  }

  public POSContextGenerator getPOSContextGenerator() {
    return getPOSContextGenerator(0);
  }

  public POSContextGenerator getPOSContextGenerator(int cacheSize) {

    if (artifactProvider != null) {
      Properties manifest = (Properties) artifactProvider.getArtifact("manifest.properties");

      String version = manifest.getProperty("OpenNLP-Version");

      if (Version.parse(version).getMinor() < 8) {
        return new DefaultPOSContextGenerator(cacheSize, getDictionary());
      }
    }
    
    return new ConfigurablePOSContextGenerator(cacheSize, createFeatureGenerators());

  }

  public SequenceValidator<String> getSequenceValidator() {
    return new DefaultPOSSequenceValidator(getTagDictionary());
  }

  // TODO: This should not be done anymore for 8 models, they can just
  // use the SerializableArtifact interface
  public static class POSDictionarySerializer implements ArtifactSerializer<POSDictionary> {

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

  @Deprecated
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
      throw new InvalidFormatException(msg, e);
    }
  }

  public static POSTaggerFactory create(String subclassName, byte[] featureGeneratorBytes,
                                        Map<String, Object> resources, TagDictionary posDictionary)
      throws InvalidFormatException {

    POSTaggerFactory theFactory;

    if (subclassName == null) {
      // will create the default factory
      theFactory = new POSTaggerFactory(null, posDictionary);
    }
    else {
      try {
        theFactory = ExtensionLoader.instantiateExtension(
            POSTaggerFactory.class, subclassName);
      } catch (Exception e) {
        String msg = "Could not instantiate the " + subclassName
            + ". The initialization throw an exception.";
        throw new InvalidFormatException(msg, e);
      }
    }

    theFactory.init(featureGeneratorBytes, resources, posDictionary);

    return theFactory;
  }

  public TagDictionary createEmptyTagDictionary() {
    this.posDictionary = new POSDictionary(true);
    return this.posDictionary;
  }
}
