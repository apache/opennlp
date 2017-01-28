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

import java.io.OutputStream;
import java.util.Comparator;
import java.util.Map;

import opennlp.tools.cmdline.FineGrainedReportListener;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderEvaluationMonitor;
import opennlp.tools.util.SequenceCodec;

/**
 * Generates a detailed report for the NameFinder.
 * <p>
 * It is possible to use it from an API and access the statistics using the
 * provided getters
 */
public class TokenNameFinderFineGrainedReportListener
    extends FineGrainedReportListener implements TokenNameFinderEvaluationMonitor {

  private SequenceCodec<String> sequenceCodec;

  /**
   * Creates a listener that will print to {@link System#err}
   */
  public TokenNameFinderFineGrainedReportListener(SequenceCodec<String> seqCodec) {
    this(seqCodec, System.err);
  }

  /**
   * Creates a listener that prints to a given {@link OutputStream}
   */
  public TokenNameFinderFineGrainedReportListener(SequenceCodec<String> seqCodec, OutputStream outputStream) {
    super(outputStream);
    this.sequenceCodec = seqCodec;
  }

  // methods inherited from EvaluationMonitor

  public void missclassified(NameSample reference, NameSample prediction) {
    statsAdd(reference, prediction);
  }

  public void correctlyClassified(NameSample reference,
                                  NameSample prediction) {
    statsAdd(reference, prediction);
  }

  private void statsAdd(NameSample reference, NameSample prediction) {
    String[] refTags = sequenceCodec.encode(reference.getNames(), reference.getSentence().length);
    String[] predTags = sequenceCodec.encode(prediction.getNames(), prediction.getSentence().length);

    // we don' want it to compute token frequency, so we pass an array of empty strings instead
    // of tokens
    getStats().add(new String[reference.getSentence().length], refTags, predTags);
  }

  @Override
  public Comparator<String> getMatrixLabelComparator(Map<String, ConfusionMatrixLine> confusionMatrix) {
    return new GroupedMatrixLabelComparator(confusionMatrix);
  }

  @Override
  public Comparator<String> getLabelComparator(Map<String, Counter> map) {
    return new GroupedLabelComparator(map);
  }

  public void writeReport() {
    printGeneralStatistics();
    printTagsErrorRank();
    printGeneralConfusionTable();
  }
}
