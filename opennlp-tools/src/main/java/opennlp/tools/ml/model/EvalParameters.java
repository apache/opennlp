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

package opennlp.tools.ml.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * This class encapsulates the varibales used in producing probabilities from a model
 * and facilitaes passing these variables to the eval method.
 */
public class EvalParameters {

  /**
   * Mapping between outcomes and parameter values for each context.
   * The integer representation of the context can be found using <code>pmap</code>.
   */
  private Context[] params;
  /**
   * The number of outcomes being predicted.
   */
  private final int numOutcomes;
  /**
   * The maximum number of features fired in an event. Usually referred to as C.
   * This is used to normalize the number of features which occur in an event. */
  private double correctionConstant;

  public EvalParameters(Context[] params, int numOutcomes) {
    this.params = params;
    this.numOutcomes = numOutcomes;
  }

  public Context[] getParams() {
    return params;
  }

  public int getNumOutcomes() {
    return numOutcomes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(params), numOutcomes, correctionConstant);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof EvalParameters) {
      EvalParameters evalParameters = (EvalParameters) obj;

      return Arrays.equals(params, evalParameters.params)
          && numOutcomes == evalParameters.numOutcomes
          && correctionConstant == evalParameters.correctionConstant;
    }

    return false;
  }
}
