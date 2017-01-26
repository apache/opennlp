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
import java.util.Map;
import java.util.Objects;

import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Version;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;
import opennlp.tools.util.model.UncloseableInputStream;

/**
 * This is an abstract base class for {@link ParserModel} implementations.
 */
// TODO: Model should validate the artifact map
public class ParserModel extends BaseModel {

  private static class POSModelSerializer implements ArtifactSerializer<POSModel> {

    public POSModel create(InputStream in) throws IOException {
      POSModel posModel = new POSModel(new UncloseableInputStream(in));

      // The 1.6.x models write the non-default beam size into the model itself.
      // In 1.5.x the parser configured the beam size when the model was loaded,
      // this is not possible anymore with the new APIs
      Version version = posModel.getVersion();
      if (version.getMajor() == 1 && version.getMinor() == 5) {
        if (posModel.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER) == null) {
          posModel = new POSModel(posModel.getLanguage(), posModel.getPosModel(), 10,
              null, posModel.getFactory());
        }
      }

      return posModel;
    }

    public void serialize(POSModel artifact, OutputStream out)
        throws IOException {
      artifact.serialize(out);
    }
  }

  private static class ChunkerModelSerializer implements ArtifactSerializer<ChunkerModel> {

    public ChunkerModel create(InputStream in) throws IOException {

      ChunkerModel model = new ChunkerModel(new UncloseableInputStream(in));

      Version version = model.getVersion();
      if (version.getMajor() == 1 && version.getMinor() == 5) {

        model = new ChunkerModel(model.getLanguage(), model.getChunkerModel(), new ParserChunkerFactory());

      }

      return model;
    }

    public void serialize(ChunkerModel artifact, OutputStream out)
        throws IOException {
      artifact.serialize(out);
    }
  }

  private static class HeadRulesSerializer implements
      ArtifactSerializer<opennlp.tools.parser.lang.en.HeadRules> {

    public opennlp.tools.parser.lang.en.HeadRules create(InputStream in)
        throws IOException, InvalidFormatException {
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

  public ParserModel(String languageCode, MaxentModel buildModel, MaxentModel checkModel,
      MaxentModel attachModel, POSModel parserTagger,
      ChunkerModel chunkerTagger, opennlp.tools.parser.HeadRules headRules,
      ParserType modelType, Map<String, String> manifestInfoEntries) {

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

  public ParserModel(String languageCode, MaxentModel buildModel, MaxentModel checkModel,
      MaxentModel attachModel, POSModel parserTagger,
      ChunkerModel chunkerTagger, opennlp.tools.parser.HeadRules headRules,
      ParserType modelType) {
    this (languageCode, buildModel, checkModel, attachModel, parserTagger,
        chunkerTagger, headRules, modelType, null);
  }

  public ParserModel(String languageCode, MaxentModel buildModel, MaxentModel checkModel,
      POSModel parserTagger, ChunkerModel chunkerTagger,
      opennlp.tools.parser.HeadRules headRules, ParserType type,
      Map<String, String> manifestInfoEntries) {
    this (languageCode, buildModel, checkModel, null, parserTagger,
        chunkerTagger, headRules, type, manifestInfoEntries);
  }

  public ParserModel(InputStream in) throws IOException {
    super(COMPONENT_NAME, in);
  }

  public ParserModel(File modelFile) throws IOException {
    super(COMPONENT_NAME, modelFile);
  }

  public ParserModel(URL modelURL) throws IOException {
    super(COMPONENT_NAME, modelURL);
  }

  @Override
  protected void createArtifactSerializers(
      Map<String, ArtifactSerializer> serializers) {

    super.createArtifactSerializers(serializers);

    // In 1.6.x the headrules artifact is serialized with the new API
    // which uses the Serializeable interface
    // This change is not backward compatible with the 1.5.x models.
    // In order to laod 1.5.x model the English headrules serializer must be
    // put on the serializer map.

    if (getVersion().getMajor() == 1 && getVersion().getMinor() == 5) {
      serializers.put("headrules", new HeadRulesSerializer());
    }

    serializers.put("postagger", new POSModelSerializer());
    serializers.put("chunker", new ChunkerModelSerializer());
  }

  public ParserType getParserType() {
    return ParserType.parse(getManifestProperty(PARSER_TYPE));
  }

  public MaxentModel getBuildModel() {
    return (MaxentModel) artifactMap.get(BUILD_MODEL_ENTRY_NAME);
  }

  public MaxentModel getCheckModel() {
    return (MaxentModel) artifactMap.get(CHECK_MODEL_ENTRY_NAME);
  }

  public MaxentModel getAttachModel() {
    return (MaxentModel) artifactMap.get(ATTACH_MODEL_ENTRY_NAME);
  }

  public POSModel getParserTaggerModel() {
    return (POSModel) artifactMap.get(PARSER_TAGGER_MODEL_ENTRY_NAME);
  }

  public ChunkerModel getParserChunkerModel() {
    return (ChunkerModel) artifactMap.get(CHUNKER_TAGGER_MODEL_ENTRY_NAME);
  }

  public opennlp.tools.parser.HeadRules getHeadRules() {
    return (opennlp.tools.parser.HeadRules)
        artifactMap.get(HEAD_RULES_MODEL_ENTRY_NAME);
  }

  // TODO: Update model methods should make sure properties are copied correctly ...
  public ParserModel updateBuildModel(MaxentModel buildModel) {
    return new ParserModel(getLanguage(), buildModel, getCheckModel(), getAttachModel(),
        getParserTaggerModel(), getParserChunkerModel(),
        getHeadRules(), getParserType());
  }

  public ParserModel updateCheckModel(MaxentModel checkModel) {
    return new ParserModel(getLanguage(), getBuildModel(), checkModel,
        getAttachModel(), getParserTaggerModel(),
        getParserChunkerModel(), getHeadRules(), getParserType());
  }

  public ParserModel updateTaggerModel(POSModel taggerModel) {
    return new ParserModel(getLanguage(), getBuildModel(), getCheckModel(), getAttachModel(),
        taggerModel, getParserChunkerModel(), getHeadRules(), getParserType());
  }

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
}
