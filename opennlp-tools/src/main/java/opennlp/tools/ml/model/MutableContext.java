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

/**
 * Class used to store parameters or expected values associated with this context which
 * can be updated or assigned.
 */
public class MutableContext extends Context {

  /**
   * Creates a new parameters object with the specified parameters associated with the specified
   * outcome pattern.
   *
   * @param outcomePattern Array of outcomes for which parameters exists for this context.
   * @param parameters Parameters for the outcomes specified.
   */
  public MutableContext(int[] outcomePattern, double[] parameters) {
    super(outcomePattern, parameters);
  }

  /**
   * Assigns the parameter or expected value at the specified outcomeIndex the specified value.
   *
   * @param outcomeIndex The index of the parameter or expected value to be updated.
   * @param value The value to be assigned.
   */
  public void setParameter(int outcomeIndex, double value) {
    parameters[outcomeIndex] = value;
  }

  /**
   * Updated the parameter or expected value at the specified outcomeIndex by
   * adding the specified value to its current value.
   *
   * @param outcomeIndex The index of the parameter or expected value to be updated.
   * @param value The value to be added.
   */
  public void updateParameter(int outcomeIndex, double value) {
    parameters[outcomeIndex] += value;
  }

  public boolean contains(int outcome) {
    return Arrays.binarySearch(outcomes,outcome) >= 0;
  }
}
