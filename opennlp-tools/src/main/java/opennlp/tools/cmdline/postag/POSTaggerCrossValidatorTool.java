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

package opennlp.tools.cmdline.postag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerCrossValidator;
import opennlp.tools.util.ObjectStream;

public final class POSTaggerCrossValidatorTool implements CmdLineTool {

  public String getName() {
    return "POSTaggerCrossValidator";
  }

  public String getShortDescription() {
    return "10-fold cross validator for the learnable POS tagger";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
        + TrainingParameters.getParameterUsage() + " -data trainData\n"
        + TrainingParameters.getDescription();
  }

  public void run(String[] args) {
    if (args.length < 5) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }

    TrainingParameters parameters = new TrainingParameters(args);

    if (!parameters.isValid()) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }

    opennlp.tools.util.TrainingParameters mlParams = CmdLineUtil
        .loadTrainingParameters(CmdLineUtil.getParameter("-params", args),
            false);

    File trainingDataInFile = new File(CmdLineUtil.getParameter("-data", args));
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);

    ObjectStream<POSSample> sampleStream = POSTaggerTrainerTool.openSampleData(
        "Training Data", trainingDataInFile, parameters.getEncoding());

    POSTaggerCrossValidator validator;
    try {
      // TODO: Move to util method ...
      POSDictionary tagdict = null;
      if (parameters.getDictionaryPath() != null) {
        tagdict = POSDictionary.create(new FileInputStream(parameters
            .getDictionaryPath()));
      }

      if (mlParams == null) {
        validator = new POSTaggerCrossValidator(parameters.getLanguage(),
            parameters.getModel(), tagdict, null, parameters.getCutoff(),
            parameters.getNumberOfIterations());
      } else {
        validator = new POSTaggerCrossValidator(parameters.getLanguage(),
            mlParams, tagdict, null);
      }

      validator.evaluate(sampleStream, 10);
    } catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
    } finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    System.out.println("done");

    System.out.println();

    System.out.println("Accuracy: " + validator.getWordAccuracy());
  }
}
