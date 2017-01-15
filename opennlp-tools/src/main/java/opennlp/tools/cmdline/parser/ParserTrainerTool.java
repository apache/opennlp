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

package opennlp.tools.cmdline.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.EncodingParameter;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.cmdline.parser.ParserTrainerTool.TrainerToolParams;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.ParserType;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.ModelUtil;

public final class ParserTrainerTool extends AbstractTrainerTool<Parse, TrainerToolParams> {

  interface TrainerToolParams extends TrainingParams, TrainingToolParams, EncodingParameter {
  }

  public ParserTrainerTool() {
    super(Parse.class, TrainerToolParams.class);
  }

  public String getShortDescription() {
    return "trains the learnable parser";
  }

  static Dictionary buildDictionary(ObjectStream<Parse> parseSamples, HeadRules headRules, int cutoff) {
    System.err.print("Building dictionary ...");

    Dictionary mdict;
    try {
      mdict = Parser.
          buildDictionary(parseSamples, headRules, cutoff);
    } catch (IOException e) {
      System.err.println("Error while building dictionary: " + e.getMessage());
      mdict = null;
    }
    System.err.println("done");

    return mdict;
  }

  static ParserType parseParserType(String typeAsString) {
    ParserType type = null;
    if (typeAsString != null && typeAsString.length() > 0) {
      type = ParserType.parse(typeAsString);
      if (type == null) {
        throw new TerminateToolException(1, "ParserType training parameter '" + typeAsString +
            "' is invalid!");
      }
    }

    return type;
  }

  static HeadRules creaeHeadRules(TrainerToolParams params) throws IOException {

    ArtifactSerializer headRulesSerializer;

    if (params.getHeadRulesSerializerImpl() != null) {
      headRulesSerializer = ExtensionLoader.instantiateExtension(ArtifactSerializer.class,
              params.getHeadRulesSerializerImpl());
    }
    else {
      if ("en".equals(params.getLang())) {
        headRulesSerializer = new opennlp.tools.parser.lang.en.HeadRules.HeadRulesSerializer();
      }
      else if ("es".equals(params.getLang())) {
        headRulesSerializer = new opennlp.tools.parser.lang.es.AncoraSpanishHeadRules.HeadRulesSerializer();
      }
      else {
        // default for now, this case should probably cause an error ...
        headRulesSerializer = new opennlp.tools.parser.lang.en.HeadRules.HeadRulesSerializer();
      }
    }

    Object headRulesObject = headRulesSerializer.create(new FileInputStream(params.getHeadRules()));

    if (headRulesObject instanceof HeadRules) {
      return (HeadRules) headRulesObject;
    }
    else {
      throw new TerminateToolException(-1,
          "HeadRules Artifact Serializer must create an object of type HeadRules!");
    }
  }

  // TODO: Add param to train tree insert parser
  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), true);

    if (mlParams != null) {
      if (!TrainerFactory.isValid(mlParams.getSettings("build"))) {
        throw new TerminateToolException(1, "Build training parameters are invalid!");
      }

      if (!TrainerFactory.isValid(mlParams.getSettings("check"))) {
        throw new TerminateToolException(1, "Check training parameters are invalid!");
      }

      if (!TrainerFactory.isValid(mlParams.getSettings("attach"))) {
        throw new TerminateToolException(1, "Attach training parameters are invalid!");
      }

      if (!TrainerFactory.isValid(mlParams.getSettings("tagger"))) {
        throw new TerminateToolException(1, "Tagger training parameters are invalid!");
      }

      if (!TrainerFactory.isValid(mlParams.getSettings("chunker"))) {
        throw new TerminateToolException(1, "Chunker training parameters are invalid!");
      }
    }

    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    File modelOutFile = params.getModel();
    CmdLineUtil.checkOutputFile("parser model", modelOutFile);

    ParserModel model;
    try {
      HeadRules rules = creaeHeadRules(params);

      ParserType type = parseParserType(params.getParserType());
      if (params.getFun()) {
        Parse.useFunctionTags(true);
      }

      if (ParserType.CHUNKING.equals(type)) {
        model = opennlp.tools.parser.chunking.Parser.train(
            params.getLang(), sampleStream, rules,
            mlParams);
      }
      else if (ParserType.TREEINSERT.equals(type)) {
        model = opennlp.tools.parser.treeinsert.Parser.train(params.getLang(), sampleStream, rules,
            mlParams);
      }
      else {
        throw new IllegalStateException();
      }
    }
    catch (IOException e) {
      throw createTerminationIOException(e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    CmdLineUtil.writeModel("parser", modelOutFile, model);
  }
}
