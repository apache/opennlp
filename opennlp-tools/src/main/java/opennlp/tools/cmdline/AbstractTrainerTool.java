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

package opennlp.tools.cmdline;

import java.io.IOException;

import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.TrainingParameters;

/**
 * Base class for trainer tools.
 */
public class AbstractTrainerTool<T, P> extends AbstractEvaluatorTool<T, P> {

  protected TrainingParameters mlParams;

  /**
   * Constructor with type parameters.
   *
   * @param sampleType class of the template parameter
   * @param params     interface with parameters
   */
  protected AbstractTrainerTool(Class<T> sampleType, Class<P> params) {
    super(sampleType, params);
  }

  protected TerminateToolException createTerminationIOException(IOException e) {

    if (e instanceof InsufficientTrainingDataException) {
      return new TerminateToolException(-1, "\n\nERROR: Not enough training data\n" +
          "The provided training data is not sufficient to create enough events to train a model.\n" +
          "To resolve this error use more training data, if this doesn't help there might\n" +
          "be some fundamental problem with the training data itself.");
    }

    return new TerminateToolException(-1, "IO error while reading training data or indexing data: " +
        e.getMessage(), e);
  }
}
