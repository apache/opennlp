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
import java.io.IOException;
import java.util.Map;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.namefind.TokenNameFinderTrainerTool;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.cmdline.postag.POSTaggerTrainerTool.TrainerToolParams;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.postag.MutableTagDictionary;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.TagDictionary;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ModelUtil;

public final class POSTaggerTrainerTool
    extends AbstractTrainerTool<POSSample, TrainerToolParams> {

  interface TrainerToolParams extends TrainingParams, TrainingToolParams {
  }

  public POSTaggerTrainerTool() {
    super(POSSample.class, TrainerToolParams.class);
  }

  public String getShortDescription() {
    return "trains a model for the part-of-speech tagger";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), true);
    if (mlParams != null && !TrainerFactory.isValid(mlParams)) {
      throw new TerminateToolException(1, "Training parameters file '" + params.getParams() +
          "' is invalid!");
    }

    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    File modelOutFile = params.getModel();
    CmdLineUtil.checkOutputFile("pos tagger model", modelOutFile);

    Map<String, Object> resources;

    try {
      resources = TokenNameFinderTrainerTool.loadResources(
          params.getResources(), params.getFeaturegen());
    }
    catch (IOException e) {
      throw new TerminateToolException(-1,"IO error while loading resources", e);
    }

    byte[] featureGeneratorBytes =
        TokenNameFinderTrainerTool.openFeatureGeneratorBytes(params.getFeaturegen());

    POSTaggerFactory postaggerFactory;
    try {
      postaggerFactory = POSTaggerFactory.create(params.getFactory(), featureGeneratorBytes,
          resources, null);
    } catch (InvalidFormatException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    }

    if (params.getDict() != null) {
      try {
        postaggerFactory.setTagDictionary(postaggerFactory
            .createTagDictionary(params.getDict()));
      } catch (IOException e) {
        throw new TerminateToolException(-1,
            "IO error while loading POS Dictionary", e);
      }
    }

    if (params.getTagDictCutoff() != null) {
      try {
        TagDictionary dict = postaggerFactory.getTagDictionary();
        if (dict == null) {
          dict = postaggerFactory.createEmptyTagDictionary();
          postaggerFactory.setTagDictionary(dict);
        }
        if (dict instanceof MutableTagDictionary) {
          POSTaggerME.populatePOSDictionary(sampleStream, (MutableTagDictionary)dict,
              params.getTagDictCutoff());
        } else {
          throw new IllegalArgumentException(
              "Can't extend a POSDictionary that does not implement MutableTagDictionary.");
        }
        sampleStream.reset();
      } catch (IOException e) {
        throw new TerminateToolException(-1,
            "IO error while creating/extending POS Dictionary: "
                + e.getMessage(), e);
      }
    }

    POSModel model;
    try {
      model = opennlp.tools.postag.POSTaggerME.train(params.getLang(),
          sampleStream, mlParams, postaggerFactory);
    }
    catch (IOException e) {
      throw createTerminationIOException(e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    CmdLineUtil.writeModel("pos tagger", modelOutFile, model);
  }
}
