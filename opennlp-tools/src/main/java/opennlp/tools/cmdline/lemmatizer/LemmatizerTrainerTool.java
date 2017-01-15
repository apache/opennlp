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

package opennlp.tools.cmdline.lemmatizer;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.lemmatizer.LemmatizerTrainerTool.TrainerToolParams;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.lemmatizer.LemmaSample;
import opennlp.tools.lemmatizer.LemmatizerFactory;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.util.model.ModelUtil;

public class LemmatizerTrainerTool
    extends AbstractTrainerTool<LemmaSample, TrainerToolParams> {

  interface TrainerToolParams extends TrainingParams, TrainingToolParams {
  }

  public LemmatizerTrainerTool() {
    super(LemmaSample.class, TrainerToolParams.class);
  }

  public String getName() {
    return "LemmatizerTrainerME";
  }

  public String getShortDescription() {
    return "trainer for the learnable lemmatizer";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    File modelOutFile = params.getModel();
    CmdLineUtil.checkOutputFile("lemmatizer model", modelOutFile);

    LemmatizerModel model;
    try {
      LemmatizerFactory lemmatizerFactory = LemmatizerFactory
          .create(params.getFactory());
      model = LemmatizerME.train(params.getLang(), sampleStream, mlParams,
          lemmatizerFactory);
    } catch (IOException e) {
      throw createTerminationIOException(e);
    } finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }
    CmdLineUtil.writeModel("lemmatizer", modelOutFile, model);
  }
}
