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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenSampleStream;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.PlainTextByLineStream;

public class TokenizerTrainer implements CmdLineTool {

  public String getName() {
    return "TokenizerTrainer";
  }

  public String getShortDescription() {
    return "trainer for the learnable tokenizer";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName()
        + TrainingParameters.getParameterUsage() + " trainingData model";
  }

  static ObjectStream<TokenSample> openSampleData(String sampleDataName,
      File sampleDataFile, String encoding) {
    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn
        .getChannel(), encoding);

    return new TokenSampleStream(lineStream);
  }

  public void run(String[] args) {
    if (args.length < 4) {
      System.out.println(getHelp());
      System.exit(1);
    }

    TrainingParameters parameters = new TrainingParameters(args);

    if (!parameters.isValid()) {
      System.out.println(getHelp());
      System.exit(1);
    }

    File trainingDataInFile = new File(args[args.length - 2]);

    ObjectStream<TokenSample> sampleStream = openSampleData("Training",
        trainingDataInFile, parameters.getEncoding());

    TokenizerModel model;
    try {
      model = opennlp.tools.tokenize.TokenizerME.train(
          parameters.getLanguage(), sampleStream, parameters
              .isAlphaNumericOptimizationEnabled());
    } catch (IOException e) {
      System.err.println("Training io error: " + e.getMessage());
      System.exit(-1);
      model = null;
    } finally {
      try {
        sampleStream.close();
      } catch (ObjectStreamException e) {
        // sorry that this can fail
      }
    }

    File modelOutFile = new File(args[args.length - 1]);

    CmdLineUtil.checkOutputFile("tokenizer model", modelOutFile);

    OutputStream modelOut = null;
    try {
      modelOut = new FileOutputStream(modelOutFile);
      model.serialize(modelOut);
    } catch (IOException e) {
      CmdLineUtil.handleIOExceptionDuringModelWriting(e);
    } finally {
      if (modelOut != null) {
        try {
          modelOut.close();
        } catch (IOException e) {
          // sorry that this can fail
        }
      }
    }
    
    System.out.println("Wrote tokenizer model.");
    System.out.println("Path: " + modelOutFile.getAbsolutePath());
  }
}
