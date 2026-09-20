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

package opennlp.tools.cmdline.depparse;

import java.io.IOException;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.depparse.DependencyParserTrainerTool.TrainerParams;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.depparse.DependencyModel;
import opennlp.tools.depparse.DependencyParserFactory;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/** Trains and saves an arc-standard dependency parser model. */
public class DependencyParserTrainerTool extends AbstractTrainerTool<DependencySample, TrainerParams> {

  interface TrainerParams extends TrainingParams, TrainingToolParams {
  }

  /** Creates the trainer tool. */
  public DependencyParserTrainerTool() {
    super(DependencySample.class, TrainerParams.class);
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "DependencyParserTrainerME";
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Trains a dependency parser from CoNLL-U samples";
  }

  /** {@inheritDoc} */
  @Override
  public void run(String format, String[] args) {
    super.run(format, args);
    try (ObjectStream<DependencySample> samples = sampleStream) {
      CmdLineUtil.checkOutputFile("dependency parser model", params.getModel());
      mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
      if (mlParams == null) {
        mlParams = TrainingParameters.defaultParams();
      }
      DependencyModel model = DependencyParserME.train(params.getLang(), samples, mlParams,
          DependencyParserFactory.create(params.getFactory()));
      CmdLineUtil.writeModel("dependency parser", params.getModel(), model);
    } catch (IOException e) {
      throw createTerminationIOException(e);
    }
  }
}
