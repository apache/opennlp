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


package opennlp.tools.namefind;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.featuregen.BrownCluster;
import opennlp.tools.util.featuregen.WordClusterDictionary;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;
import opennlp.tools.util.model.ModelUtil;

/**
 * The {@link TokenNameFinderModel} is the model used
 * by a learnable {@link TokenNameFinder}.
 *
 * @see NameFinderME
 */
// TODO: Fix the model validation, on loading via constructors and input streams
public class TokenNameFinderModel extends BaseModel {

  public static class FeatureGeneratorCreationError extends RuntimeException {
    FeatureGeneratorCreationError(Throwable t) {
      super(t);
    }
  }

  private static class ByteArraySerializer implements ArtifactSerializer<byte[]> {

    public byte[] create(InputStream in) throws IOException {
      return ModelUtil.read(in);
    }

    public void serialize(byte[] artifact, OutputStream out) throws IOException {
      out.write(artifact);
    }
  }

  private static final String COMPONENT_NAME = "NameFinderME";
  private static final String MAXENT_MODEL_ENTRY_NAME = "nameFinder.model";

  static final String GENERATOR_DESCRIPTOR_ENTRY_NAME = "generator.featuregen";

  static final String SEQUENCE_CODEC_CLASS_NAME_PARAMETER = "sequenceCodecImplName";

  public TokenNameFinderModel(String languageCode, SequenceClassificationModel<String> nameFinderModel,
      byte[] generatorDescriptor, Map<String, Object> resources, Map<String, String> manifestInfoEntries,
      SequenceCodec<String> seqCodec, TokenNameFinderFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);

    init(nameFinderModel, generatorDescriptor, resources, manifestInfoEntries, seqCodec);

    if (!seqCodec.areOutcomesCompatible(nameFinderModel.getOutcomes())) {
      throw new IllegalArgumentException("Model not compatible with name finder!");
    }
  }

  public TokenNameFinderModel(String languageCode, MaxentModel nameFinderModel, int beamSize,
      byte[] generatorDescriptor, Map<String, Object> resources, Map<String, String> manifestInfoEntries,
      SequenceCodec<String> seqCodec, TokenNameFinderFactory factory) {
    super(COMPONENT_NAME, languageCode, manifestInfoEntries, factory);


    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    manifest.put(BeamSearch.BEAM_SIZE_PARAMETER, Integer.toString(beamSize));

    init(nameFinderModel, generatorDescriptor, resources, manifestInfoEntries, seqCodec);

    if (!isModelValid(nameFinderModel)) {
      throw new IllegalArgumentException("Model not compatible with name finder!");
    }
  }

  // TODO: Extend this one with beam size!
  public TokenNameFinderModel(String languageCode, MaxentModel nameFinderModel,
      byte[] generatorDescriptor, Map<String, Object> resources, Map<String, String> manifestInfoEntries) {
    this(languageCode, nameFinderModel, NameFinderME.DEFAULT_BEAM_SIZE,
        generatorDescriptor, resources, manifestInfoEntries, new BioCodec(), new TokenNameFinderFactory());
  }

  public TokenNameFinderModel(String languageCode, MaxentModel nameFinderModel,
      Map<String, Object> resources, Map<String, String> manifestInfoEntries) {
    this(languageCode, nameFinderModel, null, resources, manifestInfoEntries);
  }

  public TokenNameFinderModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  public TokenNameFinderModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  public TokenNameFinderModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  private void init(Object nameFinderModel,
      byte[] generatorDescriptor, Map<String, Object> resources, Map<String, String> manifestInfoEntries,
      SequenceCodec<String> seqCodec) {

    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    manifest.put(SEQUENCE_CODEC_CLASS_NAME_PARAMETER, seqCodec.getClass().getName());

    artifactMap.put(MAXENT_MODEL_ENTRY_NAME, nameFinderModel);

    if (generatorDescriptor != null && generatorDescriptor.length > 0)
      artifactMap.put(GENERATOR_DESCRIPTOR_ENTRY_NAME, generatorDescriptor);

    if (resources != null) {
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
    checkArtifactMap();
  }

  public SequenceClassificationModel<String> getNameFinderSequenceModel() {

    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);

    if (artifactMap.get(MAXENT_MODEL_ENTRY_NAME) instanceof MaxentModel) {
      String beamSizeString = manifest.getProperty(BeamSearch.BEAM_SIZE_PARAMETER);

      int beamSize = NameFinderME.DEFAULT_BEAM_SIZE;
      if (beamSizeString != null) {
        beamSize = Integer.parseInt(beamSizeString);
      }

      return new BeamSearch<>(beamSize, (MaxentModel) artifactMap.get(MAXENT_MODEL_ENTRY_NAME));
    }
    else if (artifactMap.get(MAXENT_MODEL_ENTRY_NAME) instanceof SequenceClassificationModel) {
      return (SequenceClassificationModel) artifactMap.get(MAXENT_MODEL_ENTRY_NAME);
    }
    else {
      return null;
    }
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return TokenNameFinderFactory.class;
  }

  public SequenceCodec<String> getSequenceCodec() {
    return this.getFactory().getSequenceCodec();
  }

  public TokenNameFinderFactory getFactory() {
    return (TokenNameFinderFactory) this.toolFactory;
  }

  @Override
  protected void createArtifactSerializers(Map<String, ArtifactSerializer> serializers) {
    super.createArtifactSerializers(serializers);

    serializers.put("featuregen", new ByteArraySerializer());
  }

  /**
   * Create the artifact serializers. Currently for serializers related to
   * features that require external resources, such as {@code W2VClassesDictionary}
   * objects, the convention is to add its element tag name as key of the serializer map.
   * For example, the element tag name for the {@code WordClusterFeatureGenerator} which
   * uses {@code W2VClassesDictionary} objects serialized by the {@code W2VClassesDictionarySerializer}
   * is 'wordcluster', which is the key used to add the serializer to the map.
   * @return the map containing the added serializers
   */
  public static Map<String, ArtifactSerializer> createArtifactSerializers()  {

    // TODO: Not so nice, because code cannot really be reused by the other create serializer method
    //       Has to be redesigned, we need static access to default serializers
    //       and these should be able to extend during runtime ?!
    //
    //       The XML feature generator factory should provide these mappings.
    //       Usually the feature generators should know what type of resource they expect.

    Map<String, ArtifactSerializer> serializers = BaseModel.createArtifactSerializers();

    serializers.put("featuregen", new ByteArraySerializer());
    serializers.put("wordcluster", new WordClusterDictionary.WordClusterDictionarySerializer());
    serializers.put("brownclustertoken", new BrownCluster.BrownClusterSerializer());
    serializers.put("brownclustertokenclass", new BrownCluster.BrownClusterSerializer());
    serializers.put("brownclusterbigram", new BrownCluster.BrownClusterSerializer());

    return serializers;
  }

  private boolean isModelValid(MaxentModel model) {

    String outcomes[] = new String[model.getNumOutcomes()];

    for (int i = 0; i < model.getNumOutcomes(); i++) {
      outcomes[i] = model.getOutcome(i);
    }

    return getFactory().createSequenceCodec().areOutcomesCompatible(outcomes);
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(MAXENT_MODEL_ENTRY_NAME) instanceof MaxentModel) &&
        !(artifactMap.get(MAXENT_MODEL_ENTRY_NAME) instanceof SequenceClassificationModel)) {
      throw new InvalidFormatException("Token Name Finder model is incomplete!");
    }
  }
}
