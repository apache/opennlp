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
import java.io.FileInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.ml.libsvm.doccat.SvmDoccatModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * CLI tool for classifying documents using an SVM-based document categorization model.
 * <p>
 * Usage: {@code opennlp DoccatSVM model < documents}
 */
public class DoccatSVMTool extends BasicCmdLineTool {

  private static final Logger logger = LoggerFactory.getLogger(DoccatSVMTool.class);

  @Override
  public String getShortDescription() {
    return "SVM-based document categorizer";
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < documents";
  }

  @Override
  public void run(String[] args) {

    if (0 == args.length) {
      logger.info(getHelp());
    } else {

      File modelFile = new File(args[0]);
      CmdLineUtil.checkInputFile("SVM Doccat model", modelFile);

      SvmDoccatModel model;
      try (FileInputStream in = new FileInputStream(modelFile)) {
        model = SvmDoccatModel.deserialize(in);
      } catch (IOException | ClassNotFoundException e) {
        throw new RuntimeException("Failed to load SVM Doccat model: " + e.getMessage(), e);
      }

      opennlp.tools.ml.libsvm.doccat.DocumentCategorizerSVM categorizer =
          new opennlp.tools.ml.libsvm.doccat.DocumentCategorizerSVM(
              model, new BagOfWordsFeatureGenerator());

      ObjectStream<String> documentStream;

      PerformanceMonitor perfMon = new PerformanceMonitor("doc");
      perfMon.start();

      try {
        documentStream = new ParagraphStream(new PlainTextByLineStream(
            new SystemInputStreamFactory(), SystemInputStreamFactory.encoding()));
        String document;
        while ((document = documentStream.read()) != null) {
          String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(document);

          double[] prob = categorizer.categorize(tokens);
          String category = categorizer.getBestCategory(prob);

          DocumentSample sample = new DocumentSample(category, tokens);
          logger.info(sample.toString());

          perfMon.incrementCounter();
        }
      } catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();
    }
  }
}
