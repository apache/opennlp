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

package opennlp.tools.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;
import opennlp.tools.util.model.ChunkerModelSerializer;
import opennlp.tools.util.model.POSModelSerializer;

/**
 * This is the default {@link ParserModel} implementation.
 */
public class ParserModel extends BaseModel {

  private static class HeadRulesSerializer implements
      ArtifactSerializer<opennlp.tools.parser.lang.en.HeadRules> {

    public opennlp.tools.parser.lang.en.HeadRules create(InputStream in)
        throws IOException {
      return new opennlp.tools.parser.lang.en.HeadRules(new BufferedReader(
          new InputStreamReader(in, StandardCharsets.UTF_8)));
    }

    public void serialize(opennlp.tools.parser.lang.en.HeadRules artifact,
        OutputStream out) throws IOException {
      artifact.serialize(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }
  }

  private static final String COMPONENT_NAME = "Parser";

  private static final String BUILD_MODEL_ENTRY_NAME = "build.model";

  private static final String CHECK_MODEL_ENTRY_NAME = "check.model";

  private static final String ATTACH_MODEL_ENTRY_NAME = "attach.model";

  private static final String PARSER_TAGGER_MODEL_ENTRY_NAME = "parsertager.postagger";

  private static final String CHUNKER_TAGGER_MODEL_ENTRY_NAME = "parserchunker.chunker";

  private static final String HEAD_RULES_MODEL_ENTRY_NAME = "head-rules.headrules";

  private static final String PARSER_TYPE = "parser-type";

  /**
   * Initializes a {@link ParserModel} instance via given parameters.
   *
   * @param languageCode An ISO conform language code.
   * @param buildModel A valid {@link MaxentModel} used to build.
   * @param checkModel A valid {@link MaxentModel} used to check.
   * @param attachModel A valid {@link MaxentModel} used to attach.
   * @param parserTagger A valid {@link POSModel} to parse.
   * @param chunkerTagger A valid {@link ChunkerModel} to chunk.
   * @param headRules The {@link HeadRules} to to use for parsing.
   * @param modelType The {@link ParserType} to use.
   * @param manifestInfoEntries Additional information kept in the manifest.
   */
  public ParserModel(String languageCode, MaxentModel buildModel, MaxentModel checkModel,
      MaxentModel attachModel, POSModel parserTagger, ChunkerModel chunkerTagger,
      HeadRules headRules, ParserType modelType, Map<String, String> manifestInfoEntries) {

    super(COMPONENT_NAME, languageCode, manifestInfoEntries);

    setManifestProperty(PARSER_TYPE, modelType.name());

    artifactMap.put(BUILD_MODEL_ENTRY_NAME, buildModel);

    artifactMap.put(CHECK_MODEL_ENTRY_NAME, checkModel);

    if (ParserType.CHUNKING.equals(modelType)) {
      if (attachModel != null)
          throw new IllegalArgumentException("attachModel must be null for chunking parser!");
    }
    else if (ParserType.TREEINSERT.equals(modelType)) {
      Objects.requireNonNull(attachModel, "attachModel must not be null");
      artifactMap.put(ATTACH_MODEL_ENTRY_NAME, attachModel);
    }
    else {
      throw new IllegalStateException("Unknown ParserType '" + modelType + "'!");
    }

    artifactMap.put(PARSER_TAGGER_MODEL_ENTRY_NAME, parserTagger);

    artifactMap.put(CHUNKER_TAGGER_MODEL_ENTRY_NAME, chunkerTagger);

    artifactMap.put(HEAD_RULES_MODEL_ENTRY_NAME, headRules);
    checkArtifactMap();
  }

  /**
   * Initializes a {@link ParserModel} instance via given parameters.
   *
   * @param languageCode An ISO conform language code.
   * @param buildModel A valid {@link MaxentModel} used to build.
   * @param checkModel A valid {@link MaxentModel} used to check.
   * @param parserTagger A valid {@link POSModel} to parse.
   * @param chunkerTagger A valid {@link ChunkerModel} to chunk.
   * @param headRules The {@link HeadRules} to to use for parsing.
   * @param modelType The {@link ParserType} to use.
   */
  public ParserModel(String languageCode, MaxentModel buildModel, MaxentModel checkModel,
      MaxentModel attachModel, POSModel parserTagger, ChunkerModel chunkerTagger,
      HeadRules headRules, ParserType modelType) {
    this (languageCode, buildModel, checkModel, attachModel, parserTagger,
        chunkerTagger, headRules, modelType, null);
  }

  /**
   * Initializes a {@link ParserModel} instance via given parameters.
   *
   * @param languageCode An ISO conform language code.
   * @param buildModel A valid {@link MaxentModel} used to build.
   * @param checkModel A valid {@link MaxentModel} used to check.
   * @param parserTagger A valid {@link POSModel} to parse.
   * @param chunkerTagger A valid {@link ChunkerModel} to chunk.
   * @param headRules The {@link HeadRules} to to use for parsing.
   * @param type The {@link ParserType} to use.
   * @param manifestInfoEntries Additional information kept in the manifest.
   */
  public ParserModel(String languageCode, MaxentModel buildModel, MaxentModel checkModel,
      POSModel parserTagger, ChunkerModel chunkerTagger, HeadRules headRules,
      ParserType type, Map<String, String> manifestInfoEntries) {
    this (languageCode, buildModel, checkModel, null, parserTagger,
        chunkerTagger, headRules, type, manifestInfoEntries);
  }

  /**
   * Initializes a {@link ParserModel} instance via a valid {@link InputStream}.
   *
   * @param in The {@link InputStream} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public ParserModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  /**
   * Initializes a {@link ParserModel} instance via a valid {@link File}.
   *
   * @param modelFile The {@link File} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public ParserModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  /**
   * Initializes a {@link ParserModel} instance via a valid {@link Path}.
   *
   * @param modelPath The {@link Path} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public ParserModel(Path modelPath) throws IOException {
    this(modelPath.toFile());
  }

  /**
   * Initializes a {@link ParserModel} instance via a valid {@link URL}.
   *
   * @param modelURL The {@link URL} used for loading the model.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public ParserModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected void createArtifactSerializers(Map<String, ArtifactSerializer> serializers) {

    super.createArtifactSerializers(serializers);

    // In 1.6.x the head rules artifact is serialized with the new API
    // which uses the Serializable interface
    // This change is not backward compatible with the 1.5.x models.
    // In order to load 1.5.x model the English head rules serializer must be
    // put on the serializer map.

    if (getVersion().getMajor() == 1 && getVersion().getMinor() == 5) {
      serializers.put("headrules", new HeadRulesSerializer());
    }

    serializers.put("postagger", new POSModelSerializer());
    serializers.put("chunker", new ChunkerModelSerializer());
  }

  /**
   * @return Retrieves the {@link ParserType} as configured in the manifest.
   */
  public ParserType getParserType() {
    return ParserType.parse(getManifestProperty(PARSER_TYPE));
  }

  /**
   * @return Retrieves the {@link MaxentModel build model} as configured in the manifest.
   */
  public MaxentModel getBuildModel() {
    return (MaxentModel) artifactMap.get(BUILD_MODEL_ENTRY_NAME);
  }

  /**
   * @return Retrieves the {@link MaxentModel check model} as configured in the manifest.
   */
  public MaxentModel getCheckModel() {
    return (MaxentModel) artifactMap.get(CHECK_MODEL_ENTRY_NAME);
  }

  /**
   * @return Retrieves the {@link MaxentModel attach model} as configured in the manifest.
   */
  public MaxentModel getAttachModel() {
    return (MaxentModel) artifactMap.get(ATTACH_MODEL_ENTRY_NAME);
  }

  /**
   * @return Retrieves the {@link POSModel} as configured in the manifest.
   */
  public POSModel getParserTaggerModel() {
    return (POSModel) artifactMap.get(PARSER_TAGGER_MODEL_ENTRY_NAME);
  }

  /**
   * @return Retrieves the {@link ChunkerModel} as configured in the manifest.
   */
  public ChunkerModel getParserChunkerModel() {
    return (ChunkerModel) artifactMap.get(CHUNKER_TAGGER_MODEL_ENTRY_NAME);
  }

  /**
   * @return Retrieves the {@link HeadRules} as configured in the manifest.
   */
  public HeadRules getHeadRules() {
    return (opennlp.tools.parser.HeadRules)
        artifactMap.get(HEAD_RULES_MODEL_ENTRY_NAME);
  }

  // TODO: (All!) Update model methods should make sure properties are copied correctly ...

  /**
   * Instantiates a new {@link ParserModel} instance from the existing configuration
   * with the specified {@code buildModel} for exchange.
   *
   * @param buildModel A valid {@link MaxentModel} used to build.
   * @return A valid {@link ParserModel}.
   */
  public ParserModel updateBuildModel(MaxentModel buildModel) {
    return new ParserModel(getLanguage(), buildModel, getCheckModel(), getAttachModel(),
        getParserTaggerModel(), getParserChunkerModel(),
        getHeadRules(), getParserType());
  }

  /**
   * Instantiates a new {@link ParserModel} instance from the existing configuration
   * with the specified {@code checkModel} for exchange.
   *
   * @param checkModel A valid {@link MaxentModel} used to check.
   * @return A valid {@link ParserModel}.
   */
  public ParserModel updateCheckModel(MaxentModel checkModel) {
    return new ParserModel(getLanguage(), getBuildModel(), checkModel,
        getAttachModel(), getParserTaggerModel(),
        getParserChunkerModel(), getHeadRules(), getParserType());
  }

  /**
   * Instantiates a new {@link ParserModel} instance from the existing configuration
   * with the specified {@code taggerModel} for exchange.
   *
   * @param taggerModel A valid {@link POSModel} used to tag.
   * @return A valid {@link ParserModel}.
   */
  public ParserModel updateTaggerModel(POSModel taggerModel) {
    return new ParserModel(getLanguage(), getBuildModel(), getCheckModel(), getAttachModel(),
        taggerModel, getParserChunkerModel(), getHeadRules(), getParserType());
  }

  /**
   * Instantiates a new {@link ParserModel} instance from the existing configuration
   * with the specified {@code chunkModel} for exchange.
   *
   * @param chunkModel A valid {@link ChunkerModel} used to tag.
   * @return A valid {@link ParserModel}.
   */
  public ParserModel updateChunkerModel(ChunkerModel chunkModel) {
    return new ParserModel(getLanguage(), getBuildModel(), getCheckModel(), getAttachModel(),
        getParserTaggerModel(), chunkModel, getHeadRules(), getParserType());
  }

  @Override
  protected void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();

    if (!(artifactMap.get(BUILD_MODEL_ENTRY_NAME)  instanceof AbstractModel)) {
      throw new InvalidFormatException("Missing the build model!");
    }

    ParserType modelType = getParserType();

    if (modelType != null) {
      if (ParserType.CHUNKING.equals(modelType)) {
        if (artifactMap.get(ATTACH_MODEL_ENTRY_NAME) != null)
            throw new InvalidFormatException("attachModel must be null for chunking parser!");
      }
      else if (ParserType.TREEINSERT.equals(modelType)) {
        if (!(artifactMap.get(ATTACH_MODEL_ENTRY_NAME)  instanceof AbstractModel))
          throw new InvalidFormatException("attachModel must not be null!");
      }
      else {
        throw new InvalidFormatException("Unknown ParserType '" + modelType + "'!");
      }
    }
    else {
      throw new InvalidFormatException("Missing the parser type property!");
    }

    if (!(artifactMap.get(CHECK_MODEL_ENTRY_NAME)  instanceof AbstractModel)) {
      throw new InvalidFormatException("Missing the check model!");
    }

    if (!(artifactMap.get(PARSER_TAGGER_MODEL_ENTRY_NAME)  instanceof POSModel)) {
      throw new InvalidFormatException("Missing the tagger model!");
    }

    if (!(artifactMap.get(CHUNKER_TAGGER_MODEL_ENTRY_NAME)  instanceof ChunkerModel)) {
      throw new InvalidFormatException("Missing the chunker model!");
    }

    if (!(artifactMap.get(HEAD_RULES_MODEL_ENTRY_NAME)  instanceof HeadRules)) {
      throw new InvalidFormatException("Missing the head rules!");
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(artifactMap.get(MANIFEST_ENTRY),
            artifactMap.get(PARSER_TAGGER_MODEL_ENTRY_NAME));
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof ParserModel model) {
      Map<String, Object> artifactMapToCheck = model.artifactMap;
      AbstractModel abstractModel = (AbstractModel) artifactMapToCheck.get(BUILD_MODEL_ENTRY_NAME);

      return artifactMap.get(MANIFEST_ENTRY).equals(artifactMapToCheck.get(MANIFEST_ENTRY)) &&
              artifactMap.get(BUILD_MODEL_ENTRY_NAME).equals(abstractModel);
    }
    return false;
  }
}
