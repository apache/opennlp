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

package opennlp.tools.cmdline.namefind;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.cmdline.AbstractCrossValidatorTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.namefind.TokenNameFinderCrossValidatorTool.CVToolParams;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.cmdline.params.DetailedFMeasureEvaluatorParams;
import opennlp.tools.cmdline.params.FineGrainedEvaluatorParams;
import opennlp.tools.namefind.BilouCodec;
import opennlp.tools.namefind.BioCodec;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleTypeFilter;
import opennlp.tools.namefind.TokenNameFinderCrossValidator;
import opennlp.tools.namefind.TokenNameFinderEvaluationMonitor;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.EvaluationMonitor;

public final class TokenNameFinderCrossValidatorTool
    extends AbstractCrossValidatorTool<NameSample, CVToolParams> {

  interface CVToolParams extends TrainingParams, CVParams,
      DetailedFMeasureEvaluatorParams, FineGrainedEvaluatorParams {
  }

  public TokenNameFinderCrossValidatorTool() {
    super(NameSample.class, CVToolParams.class);
  }

  public String getShortDescription() {
    return "K-fold cross validator for the learnable Name Finder";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), true);
    if (mlParams == null) {
      mlParams = new TrainingParameters();
    }

    byte[] featureGeneratorBytes =
        TokenNameFinderTrainerTool.openFeatureGeneratorBytes(params.getFeaturegen());

    Map<String, Object> resources;

    try {
      resources = TokenNameFinderTrainerTool.loadResources(params.getResources(), params.getFeaturegen());
    }
    catch (IOException e) {
      throw new TerminateToolException(-1,"IO error while loading resources", e);
    }

    if (params.getNameTypes() != null) {
      String[] nameTypes = params.getNameTypes().split(",");
      sampleStream = new NameSampleTypeFilter(nameTypes, sampleStream);
    }

    List<EvaluationMonitor<NameSample>> listeners = new LinkedList<>();
    if (params.getMisclassified()) {
      listeners.add(new NameEvaluationErrorListener());
    }
    TokenNameFinderDetailedFMeasureListener detailedFListener = null;
    if (params.getDetailedF()) {
      detailedFListener = new TokenNameFinderDetailedFMeasureListener();
      listeners.add(detailedFListener);
    }

    String sequenceCodecImplName = params.getSequenceCodec();

    if ("BIO".equals(sequenceCodecImplName)) {
      sequenceCodecImplName = BioCodec.class.getName();
    }
    else if ("BILOU".equals(sequenceCodecImplName)) {
      sequenceCodecImplName = BilouCodec.class.getName();
    }

    SequenceCodec<String> sequenceCodec =
        TokenNameFinderFactory.instantiateSequenceCodec(sequenceCodecImplName);


    TokenNameFinderFineGrainedReportListener reportListener = null;
    File reportFile = params.getReportOutputFile();
    OutputStream reportOutputStream = null;

    if (reportFile != null) {
      CmdLineUtil.checkOutputFile("Report Output File", reportFile);
      try {
        reportOutputStream = new FileOutputStream(reportFile);
        reportListener = new TokenNameFinderFineGrainedReportListener(sequenceCodec,
            reportOutputStream);
        listeners.add(reportListener);
      } catch (FileNotFoundException e) {
        throw new TerminateToolException(-1,
            "IO error while creating Name Finder fine-grained report file: "
                + e.getMessage());
      }
    }

    TokenNameFinderFactory nameFinderFactory;
    try {
      nameFinderFactory = TokenNameFinderFactory.create(params.getFactory(),
          featureGeneratorBytes, resources, sequenceCodec);
    } catch (InvalidFormatException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    }

    TokenNameFinderCrossValidator validator;
    try {
      validator = new TokenNameFinderCrossValidator(params.getLang(),
          params.getType(), mlParams, nameFinderFactory,
          listeners.toArray(new TokenNameFinderEvaluationMonitor[listeners.size()]));
      validator.evaluate(sampleStream, params.getFolds());
    } catch (IOException e) {
      throw createTerminationIOException(e);
    } finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    System.out.println("done");

    System.out.println();

    if (reportFile != null) {
      reportListener.writeReport();
    }

    if (detailedFListener == null) {
      System.out.println(validator.getFMeasure());
    } else {
      System.out.println(detailedFListener.toString());
    }
  }
}
