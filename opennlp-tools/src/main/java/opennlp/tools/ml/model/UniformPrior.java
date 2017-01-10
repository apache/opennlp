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

import java.util.Objects;

/**
 * Provide a maximum entropy model with a uniform prior.
 */
public class UniformPrior implements Prior {

  private int numOutcomes;
  private double r;

  public void logPrior(double[] dist, int[] context, float[] values) {
    for (int oi = 0; oi < numOutcomes; oi++) {
      dist[oi] = r;
    }
  }

  public void logPrior(double[] dist, int[] context) {
    logPrior(dist,context,null);
  }

  public void setLabels(String[] outcomeLabels, String[] contextLabels) {
    this.numOutcomes = outcomeLabels.length;
    r = Math.log(1.0 / numOutcomes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(numOutcomes, r);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof UniformPrior) {
      UniformPrior prior = (UniformPrior) obj;

      return numOutcomes == prior.numOutcomes && r == prior.r;
    }

    return false;
  }
}
