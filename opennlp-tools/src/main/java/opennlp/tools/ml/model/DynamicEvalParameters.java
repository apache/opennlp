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

import java.util.List;

public class DynamicEvalParameters {

  /** Mapping between outcomes and paramater values for each context.
   * The integer representation of the context can be found using <code>pmap</code>.*/
  private List<? extends Context> params;

  /** The number of outcomes being predicted. */
  private final int numOutcomes;


  /**
   * Creates a set of paramters which can be evaulated with the eval method.
   * @param params The parameters of the model.
   * @param numOutcomes The number of outcomes.
   */
  public DynamicEvalParameters(List<? extends Context> params, int numOutcomes) {
    this.params = params;
    this.numOutcomes = numOutcomes;
  }

  public Context[] getParams() {
    return params.toArray(new Context[params.size()]);
  }

  public int getNumOutcomes() {
    return numOutcomes;
  }

}
