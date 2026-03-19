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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.doccat.DoccatSVMTrainerTool.SVMTrainerToolParams;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.ml.libsvm.doccat.DocumentCategorizerSVM;
import opennlp.tools.ml.libsvm.doccat.SvmDoccatConfiguration;
import opennlp.tools.ml.libsvm.doccat.SvmDoccatModel;
import opennlp.tools.util.ext.ExtensionLoader;

/**
 * CLI tool for training an SVM-based document categorization model.
 * <p>
 * Usage: {@code opennlp DoccatSVMTrainer -model model -lang lang -data sampleData [-featureGenerators fg]}
 */
public class DoccatSVMTrainerTool
    extends AbstractTrainerTool<DocumentSample, SVMTrainerToolParams> {

  private static final Logger logger = LoggerFactory.getLogger(DoccatSVMTrainerTool.class);

  interface SVMTrainerToolParams extends TrainingParams, TrainingToolParams {
  }

  public DoccatSVMTrainerTool() {
    super(DocumentSample.class, SVMTrainerToolParams.class);
  }

  @Override
  public String getShortDescription() {
    return "Trainer for the SVM-based document categorizer";
  }

  @Override
  public void run(String format, String[] args) {
    super.run(format, args);

    File modelOutFile = params.getModel();
    CmdLineUtil.checkOutputFile("SVM document categorizer model", modelOutFile);

    FeatureGenerator[] featureGenerators = createFeatureGenerators(params.getFeatureGenerators());

    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder().build();

    SvmDoccatModel model;
    try {
      model = DocumentCategorizerSVM.train(params.getLang(), sampleStream, config,
          featureGenerators);
    } catch (IOException e) {
      throw createTerminationIOException(e);
    } finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    try (BufferedOutputStream modelOut = new BufferedOutputStream(
        new FileOutputStream(modelOutFile))) {
      model.serialize(modelOut);
    } catch (IOException e) {
      throw createTerminationIOException(e);
    }

    logger.info("SVM Doccat model written to: {}", modelOutFile.getAbsolutePath());
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
