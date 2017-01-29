/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.cmdline.sentiment;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.sentiment.SentimentFactory;
import opennlp.tools.sentiment.SentimentME;
import opennlp.tools.sentiment.SentimentModel;
import opennlp.tools.sentiment.SentimentSample;
import opennlp.tools.util.model.ModelUtil;

/**
 * Class for helping train a sentiment analysis model.
 */
public class SentimentTrainerTool
    extends AbstractTrainerTool<SentimentSample, TrainingToolParams> {

  /**
   * Constructor
   */
  public SentimentTrainerTool() {
    super(SentimentSample.class, TrainingToolParams.class);
  }

  /**
   * Runs the trainer
   *
   * @param format
   *          the format to be used
   * @param args
   *          the arguments
   */
  @Override
  public void run(String format, String[] args) {
    super.run(format, args);
    if (0 == args.length) {
      System.out.println(getHelp());
    } else {

      mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
      if (mlParams == null) {
        mlParams = ModelUtil.createDefaultTrainingParameters();
      }

      File modelOutFile = params.getModel();

      CmdLineUtil.checkOutputFile("sentiment analysis model", modelOutFile);

      SentimentModel model;
      try {
        SentimentFactory factory = new SentimentFactory();
        model = SentimentME.train(params.getLang(), sampleStream, mlParams,
            factory);
      } catch (IOException e) {
        throw new TerminateToolException(-1,
            "IO error while reading training data or indexing data: "
                + e.getMessage(),
            e);
      } finally {
        try {
          sampleStream.close();
        } catch (IOException e) {
          // sorry that this can fail
        }
      }

      CmdLineUtil.writeModel("sentiment analysis", modelOutFile, model);
    }
  }

  /**
   * Returns the help message
   *
   * @return the message
   */
  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < documents";
  }

  /**
   * Returns the short description of the programme
   *
   * @return the description
   */
  @Override
  public String getShortDescription() {
    return "learnable sentiment analysis";
  }

}
