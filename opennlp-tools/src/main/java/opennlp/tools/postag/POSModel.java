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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;
import opennlp.tools.util.model.ByteArraySerializer;
import opennlp.tools.util.model.POSModelSerializer;
import opennlp.tools.util.model.SerializableArtifact;

/**
 * The {@link POSModel} is the model used
 * by a learnable {@link POSTagger}.
 *
 * @see POSTaggerME
 */
public final class POSModel extends BaseModel implements SerializableArtifact {

  private static final long serialVersionUID = -6014331858195322339L;
  private static final String COMPONENT_NAME = "POSTaggerME";
  static final String POS_MODEL_ENTRY_NAME = "pos.model";
  static final String GENERATOR_DESCRIPTOR_ENTRY_NAME = "generator.featuregen";

  public POSModel(String languageCode, SequenceClassificationModel<String> posModel,
      Map<String, String> manifestInfoEntries, POSTaggerFactory posFactory) {

    super(COMPONENT_NAME, languageCode, manifestInfoEntries, posFactory);

    artifactMap.put(POS_MODEL_ENTRY_NAME,
        Objects.requireNonNull(posModel, "posModel must not be null"));

    artifactMap.put(GENERATOR_DESCRIPTOR_ENTRY_NAME, posFactory.getFeatureGenerator());

    for (Map.Entry<String, Object> resource : posFactory.getResources().entrySet()) {
      artifactMap.put(resource.getKey(), resource.getValue());
    }

    // TODO: This fails probably for the sequence model ... ?!
    // checkArtifactMap();
  }

  public POSModel(String languageCode, MaxentModel posModel,
      Map<String, String> manifestInfoEntries, POSTaggerFactory posFactory) {
    this(languageCode, posModel, POSTaggerME.DEFAULT_BEAM_SIZE, manifestInfoEntries, posFactory);
  }

  public POSModel(String languageCode, MaxentModel posModel, int beamSize,
      Map<String, String> manifestInfoEntries, POSTaggerFactory posFactory) {

    super(COMPONENT_NAME, languageCode, manifestInfoEntries, posFactory);

    Objects.requireNonNull(posModel, "posModel must not be null");

    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);
    manifest.setProperty(BeamSearch.BEAM_SIZE_PARAMETER, Integer.toString(beamSize));

    artifactMap.put(POS_MODEL_ENTRY_NAME, posModel);
    artifactMap.put(GENERATOR_DESCRIPTOR_ENTRY_NAME, posFactory.getFeatureGenerator());

    for (Map.Entry<String, Object> resource : posFactory.getResources().entrySet()) {
      artifactMap.put(resource.getKey(), resource.getValue());
    }

    checkArtifactMap();
  }

  public POSModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  public POSModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  public POSModel(Path modelPath) throws IOException {
    this(modelPath.toFile());
  }

  public POSModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected Class<? extends BaseToolFactory> getDefaultFactory() {
    return POSTaggerFactory.class;
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(POS_MODEL_ENTRY_NAME) instanceof MaxentModel)) {
      throw new InvalidFormatException("POS model is incomplete!");
    }
  }

  /**
   * @deprecated use getPosSequenceModel instead. This method will be removed soon.
   * Only required for Parser 1.5.x backward compatibility. Newer models don't need this anymore.
   */
  @Deprecated
  public MaxentModel getPosModel() {
    if (artifactMap.get(POS_MODEL_ENTRY_NAME) instanceof MaxentModel) {
      return (MaxentModel) artifactMap.get(POS_MODEL_ENTRY_NAME);
    }
    else {
      return null;
    }
  }

  public SequenceClassificationModel<String> getPosSequenceModel() {

    Properties manifest = (Properties) artifactMap.get(MANIFEST_ENTRY);

    if (artifactMap.get(POS_MODEL_ENTRY_NAME) instanceof MaxentModel) {
      String beamSizeString = manifest.getProperty(BeamSearch.BEAM_SIZE_PARAMETER);

      int beamSize = POSTaggerME.DEFAULT_BEAM_SIZE;
      if (beamSizeString != null) {
        beamSize = Integer.parseInt(beamSizeString);
      }

      return new BeamSearch<>(beamSize, (MaxentModel) artifactMap.get(POS_MODEL_ENTRY_NAME));
    }
    else if (artifactMap.get(POS_MODEL_ENTRY_NAME) instanceof SequenceClassificationModel) {
      return (SequenceClassificationModel) artifactMap.get(POS_MODEL_ENTRY_NAME);
    }
    else {
      return null;
    }
  }

  public POSTaggerFactory getFactory() {
    return (POSTaggerFactory) this.toolFactory;
  }

  @Override
  protected void createArtifactSerializers(Map<String, ArtifactSerializer> serializers) {
    super.createArtifactSerializers(serializers);

    serializers.put("featuregen", new ByteArraySerializer());
  }

  /**
   * Retrieves the ngram dictionary.
   *
   * @return ngram dictionary or null if not used
   */
  public Dictionary getNgramDictionary() {
    if (getFactory() != null)
      return getFactory().getDictionary();
    return null;
  }

  @Override
  public Class<POSModelSerializer> getArtifactSerializerClass() {
    return POSModelSerializer.class;
  }

  @Override
  public int hashCode() {
    return Objects.hash(artifactMap.get("manifest.properties"), artifactMap.get("pos.model"),
            Arrays.hashCode((byte[]) artifactMap.get("generator.featuregen"))
    );
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof POSModel) {
      POSModel model = (POSModel) obj;
      Map<String, Object> artifactMapToCheck = model.artifactMap;
      AbstractModel abstractModel = (AbstractModel) artifactMapToCheck.get("pos.model");

      return artifactMap.get("manifest.properties").equals(artifactMapToCheck.get("manifest.properties")) &&
              artifactMap.get("pos.model").equals(abstractModel) &&
              Arrays.equals((byte[]) artifactMap.get("generator.featuregen"),
                            (byte[]) artifactMapToCheck.get("generator.featuregen"));
    }
    return false;
  }
}
