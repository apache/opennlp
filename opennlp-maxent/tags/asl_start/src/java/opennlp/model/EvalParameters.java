/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.model;

 /**
 * This class encapsulates the varibales used in producing probabilities from a model 
 * and facilitaes passing these variables to the eval method.  Variables are declared
 * non-private so that they may be accessed and updated without a method call for efficiency
 * reasons.
 * @author Tom Morton
 *
 */
public class EvalParameters {
  
 /** Mapping between outcomes and paramater values for each context. 
   * The integer representation of the context can be found using <code>pmap</code>.*/
  public Context[] params;
  /** The number of outcomes being predicted. */
  public final int numOutcomes;
  /** The maximum number of feattures fired in an event. Usually refered to a C.
   * This is used to normalize the number of features which occur in an event. */
  public double correctionConstant;
  
  /**  Stores inverse of the correction constant, 1/C. */
  public final double constantInverse;
  /** The correction parameter of the model. */
  public double correctionParam;
  /** Log of 1/C; initial value of probabilities. */
  private final double iprob;
  
  private String[] outcomeLabels;
    
  /**
   * Creates a set of paramters which can be evaulated with the eval method.
   * @param params The parameters of the model.
   * @param correctionParam The correction paramter.
   * @param correctionConstant The correction constant.
   * @param numOutcomes The number of outcomes.
   */
  public EvalParameters(Context[] params, double correctionParam, double correctionConstant, int numOutcomes) {
    this.params = params;
    this.correctionParam = correctionParam;
    this.numOutcomes = numOutcomes;
    this.correctionConstant = correctionConstant;
    this.constantInverse = 1.0 / correctionConstant;
    this.iprob = Math.log(1.0/numOutcomes);
  }
  
  public EvalParameters(Context[] params, int numOutcomes) {
    this(params,0,0,numOutcomes);
  }
  
  public EvalParameters(Context[] params, int numOutcomes, String[] outcomeLabels) {
    this(params,0,0,numOutcomes);
    this.outcomeLabels = outcomeLabels;
  }
  
  public void setOutcomes(String[] outcomeLabels) {
    this.outcomeLabels = outcomeLabels;
  }
  
  public String getOutcomeLabel(int oi) {
    if (outcomeLabels != null) {
      return outcomeLabels[oi];
    }
    return null;
  }
}