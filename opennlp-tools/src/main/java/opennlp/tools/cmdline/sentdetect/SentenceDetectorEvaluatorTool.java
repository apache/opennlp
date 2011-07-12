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

package opennlp.tools.cmdline.sentdetect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;

public final class SentenceDetectorEvaluatorTool implements CmdLineTool {
  
  /**
   * Create a list of expected parameters.
   */
  interface Parameters {
    
    @ParameterDescription(valueName = "charsetName", description = "specifies the encoding which should be used for reading and writing text")
    @OptionalParameter(defaultValue="UTF-8")
    String getEncoding();
    
    @ParameterDescription(valueName = "model")
    String getModel();
    
    @ParameterDescription(valueName = "data")
    String getData();
  }

  public String getName() {
    return "SentenceDetectorEvaluator";
  }
  
  public String getShortDescription() {
    return "evaluator for the learnable sentence detector";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + ArgumentParser.createUsage(Parameters.class);
  }

  public void run(String[] args) {
    
    if (!ArgumentParser.validateArguments(args, Parameters.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    Parameters params = ArgumentParser.parse(args, Parameters.class);
    
    Charset encoding = Charset.forName(params.getEncoding());
    
    if (encoding == null) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    SentenceModel model = new SentenceModelLoader().load(new File(params.getModel()));
    
    File trainingDataInFile = new File(params.getData());
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    opennlp.tools.sentdetect.SentenceDetectorEvaluator evaluator = 
        new opennlp.tools.sentdetect.SentenceDetectorEvaluator(new SentenceDetectorME(model));
    
    System.out.print("Evaluating ... ");
      ObjectStream<SentenceSample> sampleStream = SentenceDetectorTrainerTool.openSampleData("Test",
          trainingDataInFile, encoding);
      
      try {
      evaluator.evaluate(sampleStream);
      }
      catch (IOException e) {
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
      
      System.err.println("done");
      
      System.out.println();
      
      System.out.println(evaluator.getFMeasure());
  }
}
