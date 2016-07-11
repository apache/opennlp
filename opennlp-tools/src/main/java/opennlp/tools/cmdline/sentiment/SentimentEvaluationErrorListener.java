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

package opennlp.tools.cmdline.sentiment;

import java.io.OutputStream;

import opennlp.tools.cmdline.EvaluationErrorPrinter;
import opennlp.tools.sentiment.SentimentSample;
import opennlp.tools.util.eval.EvaluationMonitor;

/**
 * Class for creating an evaluation error listener.
 */
public class SentimentEvaluationErrorListener
    extends EvaluationErrorPrinter<SentimentSample>
    implements EvaluationMonitor<SentimentSample> {

  /**
   * Constructor
   */
  public SentimentEvaluationErrorListener() {
    super(System.err);
  }

  /**
   * Constructor
   */
  protected SentimentEvaluationErrorListener(OutputStream outputStream) {
    super(outputStream);
  }

  /**
   * Prints the error in case of a missclassification in the evaluator
   *
   * @param reference
   *          the sentiment sample reference to be used
   * @param prediction
   *          the sentiment sampple prediction
   */
  @Override
  public void missclassified(SentimentSample reference,
      SentimentSample prediction) {
    printError(new String[] { reference.getSentiment() },
        new String[] { prediction.getSentiment() }, reference, prediction,
        reference.getSentence());
  }

}
