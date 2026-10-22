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

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.depparse.FeedforwardDependencyParserTrainerTool.TrainerParams;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.depparse.FeedforwardDependencyModel;
import opennlp.tools.depparse.FeedforwardDependencyTrainer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Trains and saves a {@link FeedforwardDependencyModel}, optionally refining it with beam
 * search, or refines an existing model.
 *
 * <p>The {@code -params} and {@code -refineParams} files hold the keys that
 * {@link FeedforwardDependencyTrainer} reads; absent keys take the trainer's defaults.</p>
 */
public class FeedforwardDependencyParserTrainerTool
    extends AbstractTrainerTool<DependencySample, TrainerParams> {

  /** The training parameters plus the optional refinement step. */
  interface TrainerParams extends TrainingToolParams {

    @ParameterDescription(valueName = "paramsFile", description = "refines the model with beam "
        + "search, reading the refinement parameters from this file.")
    @OptionalParameter
    String getRefineParams();

    @ParameterDescription(valueName = "modelFile", description = "refines this model instead "
        + "of training a new one. Requires " + REFINE_PARAMS_OPTION + ".")
    @OptionalParameter
    File getBaseModel();
  }

  /** The option that names the training parameters file. */
  private static final String PARAMS_OPTION = "-params";

  /** The option that names the refinement parameters file. */
  private static final String REFINE_PARAMS_OPTION = "-refineParams";

  /** The option that names the model to refine. */
  private static final String BASE_MODEL_OPTION = "-baseModel";

  /** Creates the trainer tool. */
  public FeedforwardDependencyParserTrainerTool() {
    super(DependencySample.class, TrainerParams.class);
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Trains a feedforward dependency parser from CoNLL-U samples";
  }

  /** {@inheritDoc} */
  @Override
  public void run(String format, String[] args) {
    super.run(format, args);
    try (ObjectStream<DependencySample> samples = sampleStream) {
      if (params.getBaseModel() != null) {
        if (params.getRefineParams() == null) {
          throw new TerminateToolException(1,
              BASE_MODEL_OPTION + " requires " + REFINE_PARAMS_OPTION);
        }
        if (params.getParams() != null) {
          throw new TerminateToolException(1, PARAMS_OPTION + " does not apply with " + BASE_MODEL_OPTION
              + "; refinement reads " + REFINE_PARAMS_OPTION);
        }
      }
      CmdLineUtil.checkOutputFile("feedforward dependency parser model", params.getModel());
      final TrainingParameters refineParams = params.getRefineParams() == null
          ? null : CmdLineUtil.loadTrainingParameters(params.getRefineParams(), false);

      FeedforwardDependencyModel model;
      if (params.getBaseModel() != null) {
        model = new FeedforwardDependencyModelLoader().load(params.getBaseModel());
        if (!params.getLang().equals(model.getLanguage())) {
          throw new TerminateToolException(1, "-lang " + params.getLang()
              + " does not match the base model language " + model.getLanguage());
        }
      } else {
        // Empty parameters give every key the default of FeedforwardDependencyTrainer.
        mlParams = DependencyParserToolSupport.loadTrainingParameters(params.getParams(),
            new TrainingParameters());
        model = FeedforwardDependencyTrainer.train(params.getLang(), samples, mlParams);
        if (refineParams != null) {
          samples.reset();
        }
      }
      if (refineParams != null) {
        model = FeedforwardDependencyTrainer.refine(model, samples, refineParams);
      }
      CmdLineUtil.writeModel("feedforward dependency parser", params.getModel(), model);
    } catch (IOException e) {
      throw createTerminationIOException(e);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    }
  }
}
