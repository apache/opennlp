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

package opennlp.tools.cmdline.tokenizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import opennlp.model.TrainUtil;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenSampleStream;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public final class TokenizerTrainerTool implements CmdLineTool {
  
  interface TrainerToolParams extends TrainingParams, TrainingToolParams{

  }

  public String getName() {
    return "TokenizerTrainer";
  }

  public String getShortDescription() {
    return "trainer for the learnable tokenizer";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
      + ArgumentParser.createUsage(TrainerToolParams.class);
  }

  static ObjectStream<TokenSample> openSampleData(String sampleDataName,
      File sampleDataFile, Charset encoding) {
    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn
        .getChannel(), encoding);

    return new TokenSampleStream(lineStream);
  }
  
  static Dictionary loadDict(File f) throws IOException {
    Dictionary dict = null;
    if (f != null) {
      CmdLineUtil.checkInputFile("abb dict", f);
      dict = new Dictionary(new FileInputStream(f));
    }
    return dict;
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
    
    if (mlParams != null) {
      if (!TrainUtil.isValid(mlParams.getSettings())) {
        System.err.println("Training parameters file is invalid!");
        throw new TerminateToolException(-1);
      }
      
      if (TrainUtil.isSequenceTraining(mlParams.getSettings())) {
        System.err.println("Sequence training is not supported!");
        throw new TerminateToolException(-1);
      }
    }
    
    File trainingDataInFile = params.getData();
    File modelOutFile = params.getModel();
    
    CmdLineUtil.checkOutputFile("tokenizer model", modelOutFile);
    ObjectStream<TokenSample> sampleStream = openSampleData("Training",
        trainingDataInFile, params.getEncoding());
    
    if(mlParams == null) 
      mlParams = createTrainingParameters(params.getIterations(), params.getCutoff());

    TokenizerModel model;
    try {
      Dictionary dict = loadDict(params.getAbbDict());
      model = opennlp.tools.tokenize.TokenizerME.train(params.getLang(),
          sampleStream, dict, params.getAlphaNumOpt(), mlParams);
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

    CmdLineUtil.writeModel("tokenizer", modelOutFile, model);
  }

  public static TrainingParameters createTrainingParameters(Integer iterations, Integer cutoff) {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
    mlParams.put(TrainingParameters.ITERATIONS_PARAM,
        iterations.toString());
    mlParams.put(TrainingParameters.CUTOFF_PARAM, cutoff.toString());
    return mlParams;
  }
}
