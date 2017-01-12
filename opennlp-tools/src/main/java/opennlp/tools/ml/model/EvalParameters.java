/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.ml.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * This class encapsulates the varibales used in producing probabilities from a model
 * and facilitaes passing these variables to the eval method.
 */
public class EvalParameters {

  /** Mapping between outcomes and parameter values for each context.
   * The integer representation of the context can be found using <code>pmap</code>.*/
  private Context[] params;
  /** The number of outcomes being predicted. */
  private final int numOutcomes;
  /** The maximum number of features fired in an event. Usually referred to as C.
   * This is used to normalize the number of features which occur in an event. */
  private double correctionConstant;

  /**  Stores inverse of the correction constant, 1/C. */
  private final double constantInverse;
  /** The correction parameter of the model. */
  private double correctionParam;

  /**
   * Creates a set of parameters which can be evaulated with the eval method.
   * @param params The parameters of the model.
   * @param correctionParam The correction parameter.
   * @param correctionConstant The correction constant.
   * @param numOutcomes The number of outcomes.
   */
  public EvalParameters(Context[] params, double correctionParam,
      double correctionConstant, int numOutcomes) {
    this.params = params;
    this.correctionParam = correctionParam;
    this.numOutcomes = numOutcomes;
    this.correctionConstant = correctionConstant;
    this.constantInverse = 1.0 / correctionConstant;
  }

  public EvalParameters(Context[] params, int numOutcomes) {
    this(params,0,0,numOutcomes);
  }

  /* (non-Javadoc)
   * @see opennlp.tools.ml.model.EvalParameters#getParams()
   */
  public Context[] getParams() {
    return params;
  }

  /* (non-Javadoc)
   * @see opennlp.tools.ml.model.EvalParameters#getNumOutcomes()
   */
  public int getNumOutcomes() {
    return numOutcomes;
  }

  public double getCorrectionConstant() {
    return correctionConstant;
  }

  public double getConstantInverse() {
    return constantInverse;
  }

  public double getCorrectionParam() {
    return correctionParam;
  }

  public void setCorrectionParam(double correctionParam) {
    this.correctionParam = correctionParam;
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(params), numOutcomes, correctionConstant,
        constantInverse, correctionParam);
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
          && correctionConstant == evalParameters.correctionConstant
          && constantInverse == evalParameters.constantInverse
          && correctionParam == evalParameters.correctionParam;
    }

    return false;
  }
}
