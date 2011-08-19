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

package opennlp.tools.cmdline.chunker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkSampleStream;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.chunker.DefaultChunkerContextGenerator;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class ChunkerTrainerTool implements CmdLineTool {
  
  interface TrainerToolParams extends TrainingParams, TrainingToolParams{

  }

  public String getName() {
    return "ChunkerTrainerME";
  }

  public String getShortDescription() {
    return "trainer for the learnable chunker";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
        + ArgumentParser.createUsage(TrainerToolParams.class);
  }

  static ObjectStream<ChunkSample> openSampleData(String sampleDataName,
      File sampleDataFile, Charset encoding) {
    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn
        .getChannel(), encoding);

    return new ChunkSampleStream(lineStream);
  }
  
  public void run(String[] args) {
    
    if (!ArgumentParser.validateArguments(args, TrainerToolParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    TrainerToolParams params = ArgumentParser.parse(args,
        TrainerToolParams.class);
    
    opennlp.tools.util.TrainingParameters mlParams = 
      CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    
    File trainingDataInFile = params.getData();
    File modelOutFile = params.getModel();

    CmdLineUtil.checkOutputFile("sentence detector model", modelOutFile);
    ObjectStream<ChunkSample> sampleStream = 
      openSampleData("Training", trainingDataInFile, params.getEncoding());
    
    ChunkerModel model;
    try {
      if (mlParams == null) {
        model = ChunkerME.train(params.getLang(), sampleStream, 
            params.getCutoff(), params.getIterations());
      }
      else {
        model = ChunkerME.train(params.getLang(), sampleStream, 
            new DefaultChunkerContextGenerator(), mlParams);
      }
    } catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }
    
    CmdLineUtil.writeModel("chunker", modelOutFile, model);
  }
}
