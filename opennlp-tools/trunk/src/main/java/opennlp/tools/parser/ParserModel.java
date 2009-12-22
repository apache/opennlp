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


package opennlp.tools.parser;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.model.BinaryFileDataReader;
import opennlp.model.GenericModelReader;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.UncloseableInputStream;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;

/**
 * This is an abstract base class for {@link ParserModel} implementations.
 */
public class ParserModel extends BaseModel {

  private static class POSModelSerializer implements ArtifactSerializer<POSModel> {

    public POSModel create(InputStream in) throws IOException,
        InvalidFormatException {
      return new POSModel(new UncloseableInputStream(in));
    }

    public void serialize(POSModel artifact, OutputStream out)
        throws IOException {
      artifact.serialize(out);
    }
  }
  
  private static class ChunkerModelSerializer implements ArtifactSerializer<ChunkerModel> {

    public ChunkerModel create(InputStream in) throws IOException,
        InvalidFormatException {
      return new ChunkerModel(new UncloseableInputStream(in));
    }

    public void serialize(ChunkerModel artifact, OutputStream out)
        throws IOException {
      artifact.serialize(out);
    }
  }
  
  private static class HeadRulesSerializer implements ArtifactSerializer<opennlp.tools.parser.lang.en.HeadRules> {

    public opennlp.tools.parser.lang.en.HeadRules create(InputStream in) throws IOException,
        InvalidFormatException {
      return new opennlp.tools.parser.lang.en.HeadRules(new BufferedReader(new InputStreamReader(in, "UTF-8")));
    }

    public void serialize(opennlp.tools.parser.lang.en.HeadRules artifact, OutputStream out)
        throws IOException {
      artifact.serialize(new OutputStreamWriter(out, "UTF-8"));
    }
  }
    
  private static final String BUILD_MODEL_ENTRY_NAME = "build.model";

  private static final String CHECK_MODEL_ENTRY_NAME = "check.model";

  private static final String PARSER_TAGGER_MODEL_ENTRY_NAME = "parsertager.postagger";

  private static final String CHUNKER_TAGGER_MODEL_ENTRY_NAME = "parserchunker.chunker";

  private static final String HEAD_RULES_MODEL_ENTRY_NAME = "head-rules.headrules";

  private opennlp.tools.parser.lang.en.HeadRules headRules;

  public ParserModel(String languageCode, AbstractModel buildModel, AbstractModel checkModel, POSModel parserTagger,
      ChunkerModel chunkerTagger, opennlp.tools.parser.lang.en.HeadRules headRules) {

    super(languageCode);
    
    if (buildModel == null || checkModel == null || parserTagger == null ||
        chunkerTagger == null || headRules == null)
        throw new IllegalArgumentException("All parameters must not be null!");
    
    artifactMap.put(BUILD_MODEL_ENTRY_NAME, buildModel);
    artifactMap.put(CHECK_MODEL_ENTRY_NAME, checkModel);
    artifactMap.put(PARSER_TAGGER_MODEL_ENTRY_NAME, parserTagger);
    artifactMap.put(CHUNKER_TAGGER_MODEL_ENTRY_NAME, chunkerTagger);
    artifactMap.put(HEAD_RULES_MODEL_ENTRY_NAME, headRules);
  }

  public ParserModel(InputStream in) throws IOException, InvalidFormatException {
    super(in);
  }
  
  @Override
  protected void createArtifactSerializers(
      Map<String, ArtifactSerializer> serializers) {

    super.createArtifactSerializers(serializers);
    
    serializers.put("postager", new POSModelSerializer());
    serializers.put("chunker", new ChunkerModelSerializer());
    serializers.put("headrules", new HeadRulesSerializer());
    
  }
  
  public AbstractModel getBuildModel() {
    return (AbstractModel) artifactMap.get(BUILD_MODEL_ENTRY_NAME);
  }

  public AbstractModel getCheckModel() {
    return (AbstractModel) artifactMap.get(CHECK_MODEL_ENTRY_NAME);
  }

  public POSModel getParserTaggerModel() {
    return (POSModel) artifactMap.get(PARSER_TAGGER_MODEL_ENTRY_NAME);
  }

  public ChunkerModel getParserChunkerModel() {
    return (ChunkerModel) artifactMap.get(CHUNKER_TAGGER_MODEL_ENTRY_NAME);
  }

  public opennlp.tools.parser.lang.en.HeadRules getHeadRules() {
    return (opennlp.tools.parser.lang.en.HeadRules) 
        artifactMap.get(HEAD_RULES_MODEL_ENTRY_NAME);
  }

  public ParserModel updateBuildModel(AbstractModel buildModel) {
    return new ParserModel(getLanguage(), getBuildModel(), getCheckModel(), getParserTaggerModel(),
        getParserChunkerModel(), headRules);
  }

  private static AbstractModel readModel(String fileName) throws FileNotFoundException, IOException {
    return new GenericModelReader(new BinaryFileDataReader(new FileInputStream(fileName))).
        getModel();
  }

  public static void main(String[] args) throws FileNotFoundException, IOException, InvalidFormatException {
    if (args.length != 6){
      System.err.println("ParserModel packageName buildModel checkModel headRules chunkerModel posModel");
      System.exit(1);
    }

    AbstractModel buildModel = readModel(args[1]);

    AbstractModel checkModel = readModel(args[2]);

    opennlp.tools.parser.lang.en.HeadRules headRules =
        new opennlp.tools.parser.lang.en.HeadRules(args[3]);

    ChunkerModel chunkerModel = new ChunkerModel(new FileInputStream(args[4]));

    POSModel posModel = new POSModel(new FileInputStream(args[5]));

    ParserModel packageModel = new ParserModel("en", buildModel, checkModel, posModel,
        chunkerModel, headRules);

    packageModel.serialize(new FileOutputStream(args[0]));
  }
}