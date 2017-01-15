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

package opennlp.tools.cmdline.doccat;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.doccat.DoccatTrainerTool.TrainerToolParams;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.model.ModelUtil;

public class DoccatTrainerTool
    extends AbstractTrainerTool<DocumentSample, TrainerToolParams> {

  interface TrainerToolParams extends TrainingParams, TrainingToolParams {
  }

  public DoccatTrainerTool() {
    super(DocumentSample.class, TrainerToolParams.class);
  }

  @Override
  public String getShortDescription() {
    return "trainer for the learnable document categorizer";
  }

  @Override
  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    File modelOutFile = params.getModel();

    CmdLineUtil.checkOutputFile("document categorizer model", modelOutFile);

    FeatureGenerator[] featureGenerators = createFeatureGenerators(params
        .getFeatureGenerators());

    Tokenizer tokenizer = createTokenizer(params.getTokenizer());

    DoccatModel model;
    try {
      DoccatFactory factory = DoccatFactory.create(params.getFactory(),
          tokenizer, featureGenerators);
      model = DocumentCategorizerME.train(params.getLang(), sampleStream,
          mlParams, factory);
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

    CmdLineUtil.writeModel("document categorizer", modelOutFile, model);
  }

  static Tokenizer createTokenizer(String tokenizer) {
    if (tokenizer != null) {
      return ExtensionLoader.instantiateExtension(Tokenizer.class, tokenizer);
    }
    return WhitespaceTokenizer.INSTANCE;
  }

  static FeatureGenerator[] createFeatureGenerators(String featureGeneratorsNames) {
    if (featureGeneratorsNames == null) {
      return new FeatureGenerator[]{new BagOfWordsFeatureGenerator()};
    }
    String[] classes = featureGeneratorsNames.split(",");
    FeatureGenerator[] featureGenerators = new FeatureGenerator[classes.length];
    for (int i = 0; i < featureGenerators.length; i++) {
      featureGenerators[i] = ExtensionLoader.instantiateExtension(
          FeatureGenerator.class, classes[i]);
    }
    return featureGenerators;
  }
}
