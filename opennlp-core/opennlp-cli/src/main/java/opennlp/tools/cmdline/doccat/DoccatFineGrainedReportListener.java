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

import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.FineGrainedReportListener;
import opennlp.tools.doccat.DoccatEvaluationMonitor;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.log.LogPrintStream;

/**
 * Generates a detailed report for the POS Tagger.
 * <p>
 * It is possible to use it from an API and access the statistics using the
 * provided getters
 */
public class DoccatFineGrainedReportListener
    extends FineGrainedReportListener implements DoccatEvaluationMonitor {

  private static final Logger logger = LoggerFactory.getLogger(DoccatFineGrainedReportListener.class);

  /**
   * Creates a listener that will print to the configured {@code logger}.
   */
  public DoccatFineGrainedReportListener() {
    this(new LogPrintStream(logger));
  }

  /**
   * Creates a listener that prints to a given {@link OutputStream}
   */
  public DoccatFineGrainedReportListener(OutputStream outputStream) {
    super(outputStream);
  }

  // methods inherited from EvaluationMonitor
  @Override
  public void misclassified(DocumentSample reference, DocumentSample prediction) {
    statsAdd(reference, prediction);
  }

  @Override
  public void correctlyClassified(DocumentSample reference, DocumentSample prediction) {
    statsAdd(reference, prediction);
  }

  private void statsAdd(DocumentSample reference, DocumentSample prediction) {
    getStats().add(reference.getText(), reference.getCategory(), prediction.getCategory());
  }

  @Override
  public void writeReport() {
    printGeneralStatistics();
    printTagsErrorRank();
    printGeneralConfusionTable();
  }

}
