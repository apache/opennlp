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
import java.io.IOException;

import opennlp.tools.cmdline.AbstractTypedParamTool;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.util.ObjectStream;

/**
 * Abstract base class for tools which update the parser model.
 */
abstract class ModelUpdaterTool
    extends AbstractTypedParamTool<Parse, ModelUpdaterTool.ModelUpdaterParams> {

  interface ModelUpdaterParams extends TrainingToolParams {
  }

  protected ModelUpdaterTool() {
    super(Parse.class, ModelUpdaterParams.class);
  }

  protected abstract ParserModel trainAndUpdate(ParserModel originalModel,
      ObjectStream<Parse> parseSamples, ModelUpdaterParams parameters)
      throws IOException;

  public final void run(String format, String[] args) {
    ModelUpdaterParams params = validateAndParseParams(
        ArgumentParser.filter(args, ModelUpdaterParams.class), ModelUpdaterParams.class);

    // Load model to be updated
    File modelFile = params.getModel();
    ParserModel originalParserModel = new ParserModelLoader().load(modelFile);

    ObjectStreamFactory<Parse> factory = getStreamFactory(format);
    String[] fargs = ArgumentParser.filter(args, factory.getParameters());
    validateFactoryArgs(factory, fargs);
    ObjectStream<Parse> sampleStream = factory.create(fargs);

    ParserModel updatedParserModel;
    try {
      updatedParserModel = trainAndUpdate(originalParserModel, sampleStream, params);
    }
    catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading training data or indexing data: "
          + e.getMessage(), e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    CmdLineUtil.writeModel("parser", modelFile, updatedParserModel);
  }
}
