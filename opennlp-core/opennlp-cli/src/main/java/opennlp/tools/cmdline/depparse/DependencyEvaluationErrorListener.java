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

package opennlp.tools.cmdline.depparse;

import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.EvaluationErrorPrinter;
import opennlp.tools.depparse.DependencyEvaluationMonitor;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.log.LogPrintStream;

/**
 * A {@link DependencyEvaluationMonitor} that prints each misparsed sentence next to its
 * gold tree, in the five-column form of {@link DependencySample#toString()}.
 */
public class DependencyEvaluationErrorListener extends EvaluationErrorPrinter<DependencySample>
    implements DependencyEvaluationMonitor {

  private static final Logger logger =
      LoggerFactory.getLogger(DependencyEvaluationErrorListener.class);

  /**
   * Creates a listener that prints to the logger of this class.
   */
  public DependencyEvaluationErrorListener() {
    super(new LogPrintStream(logger));
  }

  /**
   * Creates a listener that prints to the given {@link OutputStream}.
   *
   * @param outputStream The stream to print to. Must not be {@code null}.
   */
  public DependencyEvaluationErrorListener(OutputStream outputStream) {
    super(outputStream);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Prints the reference sample and the prediction.</p>
   */
  @Override
  public void misclassified(DependencySample reference, DependencySample prediction) {
    printError(reference, prediction);
  }
}
