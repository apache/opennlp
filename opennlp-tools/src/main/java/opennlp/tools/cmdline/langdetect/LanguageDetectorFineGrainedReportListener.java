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

import java.io.OutputStream;

import opennlp.tools.cmdline.FineGrainedReportListener;
import opennlp.tools.langdetect.LanguageDetectorEvaluationMonitor;
import opennlp.tools.langdetect.LanguageSample;

/**
 * Generates a detailed report for the POS Tagger.
 * <p>
 * It is possible to use it from an API and access the statistics using the
 * provided getters
 */
public class LanguageDetectorFineGrainedReportListener
    extends FineGrainedReportListener implements LanguageDetectorEvaluationMonitor {

  /**
   * Creates a listener that will print to {@link System#err}
   */
  public LanguageDetectorFineGrainedReportListener() {
    this(System.err);
  }

  /**
   * Creates a listener that prints to a given {@link OutputStream}
   */
  public LanguageDetectorFineGrainedReportListener(OutputStream outputStream) {
    super(outputStream);
  }

  // methods inherited from EvaluationMonitor

  public void missclassified(LanguageSample reference, LanguageSample prediction) {
    statsAdd(reference, prediction);
  }

  public void correctlyClassified(LanguageSample reference, LanguageSample prediction) {
    statsAdd(reference, prediction);
  }

  private void statsAdd(LanguageSample reference, LanguageSample prediction) {
    getStats().add(reference.getContext(),
        reference.getLanguage().getLang(), prediction.getLanguage().getLang());
  }

  public void writeReport() {
    printGeneralStatistics();
    printTagsErrorRank();
    printGeneralConfusionTable();
  }

}
