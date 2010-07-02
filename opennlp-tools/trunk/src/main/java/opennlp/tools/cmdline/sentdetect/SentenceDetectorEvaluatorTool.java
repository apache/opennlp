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
import java.io.FileInputStream;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.sentdetect.SentenceSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class SentenceDetectorEvaluatorTool implements CmdLineTool {

  public String getName() {
    return "SentenceDetectorEvaluator";
  }
  
  public String getShortDescription() {
    return "evaluator for the learnable sentence detector";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " -encoding charset model testData";
  }

  public void run(String[] args) {
    if (args.length != 4) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    Charset encoding = CmdLineUtil.getEncodingParameter(args);
    
    if (encoding == null) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    SentenceModel model = SentenceDetectorTool.loadModel(new File(args[args.length - 2]));
    
    File trainingDataInFile = new File(args[args.length - 1]);
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    opennlp.tools.sentdetect.SentenceDetectorEvaluator evaluator = 
        new opennlp.tools.sentdetect.SentenceDetectorEvaluator(new SentenceDetectorME(model));
    
    try {
      System.out.print("Evaluating ... ");
      FileInputStream trainingDataIn = new FileInputStream(trainingDataInFile);
      ObjectStream<String> lineStream = new PlainTextByLineStream(trainingDataIn.getChannel(),
          encoding);
      ObjectStream<SentenceSample> sampleStream = new SentenceSampleStream(lineStream);
      
      evaluator.evaluate(sampleStream);
      sampleStream.close();
      System.err.println("done");
      
      System.out.println();
      
      System.out.println(evaluator.getFMeasure());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
