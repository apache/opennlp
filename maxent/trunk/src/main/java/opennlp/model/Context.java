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

package opennlp.model;

/**
 * Class which associates a real valued parameter or expected value with a particular contextual
 * predicate or feature.  This is used to store maxent model parameters as well as model and empirical
 * expected values.
 * @author Tom Morton
 *
 */
public class Context {

  /** The real valued parameters or expected values for this context. */
  protected double[] parameters;
  /** The outcomes which occur with this context. */
  protected int[] outcomes;
  
  /**
   * Creates a new parameters object with the specified parameters associated with the specified
   * outcome pattern.
   * @param outcomePattern Array of outcomes for which parameters exists for this context.
   * @param parameters Parameters for the outcomes specified.
   */
  public Context(int[] outcomePattern, double[] parameters) {
    this.outcomes = outcomePattern;
    this.parameters = parameters;
  }
  
  /**
   * Returns the outcomes for which parameters exists for this context.
   * @return Array of outcomes for which parameters exists for this context.
   */
  public int[] getOutcomes() {
    return outcomes;
  }
  
  /**
   * Returns the parameters or expected values for the outcomes which occur with this context.
   * @return Array of parameters for the outcomes of this context.
   */
  public double[] getParameters() {
    return parameters;
  }
}
