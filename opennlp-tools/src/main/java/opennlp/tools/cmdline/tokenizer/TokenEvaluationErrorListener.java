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

package opennlp.tools.cmdline.tokenizer;

import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.EvaluationErrorPrinter;
import opennlp.tools.log.LogPrintStream;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerEvaluationMonitor;
import opennlp.tools.util.eval.EvaluationMonitor;

/**
 * A default implementation of {@link EvaluationMonitor} that prints
 * to an output stream.
 */
public class TokenEvaluationErrorListener extends
    EvaluationErrorPrinter<TokenSample> implements TokenizerEvaluationMonitor {

  private static final Logger logger = LoggerFactory.getLogger(TokenEvaluationErrorListener.class);

  /**
   * Creates a listener that will print to the configured {@code logger}.
   */
  public TokenEvaluationErrorListener() {
    super(new LogPrintStream(logger));
  }

  /**
   * Creates a listener that will print to a given {@link OutputStream}.
   */
  public TokenEvaluationErrorListener(OutputStream outputStream) {
    super(outputStream);
  }

  @Override
  public void misclassified(TokenSample reference, TokenSample prediction) {
    printError(reference.getTokenSpans(), prediction.getTokenSpans(),
        reference, prediction, reference.getText());
  }

}
