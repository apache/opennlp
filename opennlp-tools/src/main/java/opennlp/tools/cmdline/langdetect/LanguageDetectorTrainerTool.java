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

package opennlp.tools.cmdline.langdetect;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.langdetect.LanguageDetectorFactory;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.langdetect.LanguageSample;
import opennlp.tools.util.model.ModelUtil;

public class LanguageDetectorTrainerTool
    extends AbstractTrainerTool<LanguageSample, LanguageDetectorTrainerTool.TrainerToolParams> {

  interface TrainerToolParams extends TrainingParams {
    @ArgumentParser.ParameterDescription(valueName = "modelFile", description = "output model file.")
    File getModel();

    @ArgumentParser.ParameterDescription(valueName = "paramsFile", description = "training parameters file.")
    @ArgumentParser.OptionalParameter()
    String getParams();
  }

  public LanguageDetectorTrainerTool() {
    super(LanguageSample.class, TrainerToolParams.class);
  }

  @Override
  public String getShortDescription() {
    return "trainer for the learnable language detector";
  }

  @Override
  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    File modelOutFile = params.getModel();

    CmdLineUtil.checkOutputFile("language detector model", modelOutFile);

    LanguageDetectorModel model;
    try {
      LanguageDetectorFactory factory = LanguageDetectorFactory.create(params.getFactory());
      model = LanguageDetectorME.train(sampleStream, mlParams, factory);
    } catch (IOException e) {
      throw createTerminationIOException(e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    CmdLineUtil.writeModel("language detector", modelOutFile, model);
  }
}
