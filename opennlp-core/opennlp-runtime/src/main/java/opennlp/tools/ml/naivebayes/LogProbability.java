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

package opennlp.tools.ml.naivebayes;

/**
 * A class implementing the logarithmic {@link Probability} for a label.
 *
 * @param <T> The label (category) class.
 *
 */
public class LogProbability<T> extends Probability<T> {

  public LogProbability(T label) {
    super(label);
    set(1.0);
  }

  /**
   * Assigns a {@code probability} to a label, discarding any previously assigned probability.
   *
   * @param probability The probability to assign.
   */
  @Override
  public void set(double probability) {
    this.probability = StrictMath.log(probability);
  }

  /**
   * Assigns a {@code probability} to a label, discarding any previously assigned probability.
   *
   * @param probability The {@link Probability} to assign.
   */
  @Override
  public void set(Probability<T> probability) {
    this.probability = probability.getLog();
  }

  /**
   * Assigns a {@code probability} to a label, discarding any previously assigned probability,
   * if the new probability is greater than the old one.
   *
   * @param probability The probability to assign.
   */
  @Override
  public void setIfLarger(double probability) {
    double logP = StrictMath.log(probability);
    if (this.probability < logP) {
      this.probability = logP;
    }
  }

  /**
   * Assigns a {@code probability} to a label, discarding any previously assigned probability,
   * if the new probability is greater than the old one.
   *
   * @param probability The {@link Probability} to assign.
   */
  @Override
  public void setIfLarger(Probability<T> probability) {
    if (this.probability < probability.getLog()) {
      this.probability = probability.getLog();
    }
  }

  /**
   * @param probability the probability to check
   * @return {@code true} if a probability is greater than the old one, {@code false} otherwise.
   */
  @Override
  public boolean isLarger(Probability<T> probability) {
    return this.probability < probability.getLog();
  }

  /**
   * Assigns a log {@code probability} to a label, discarding any previously assigned probability.
   *
   * @param probability The log probability to assign.
   */
  @Override
  public void setLog(double probability) {
    this.probability = probability;
  }

  /**
   * Compounds the existing {@code probability} mass on the label with the new
   * probability passed in to the method.
   *
   * @param probability The probability weight to add.
   */
  @Override
  public void addIn(double probability) {
    setLog(this.probability + StrictMath.log(probability));
  }

  /**
   * @return Retrieves the probability associated with a label.
   */
  @Override
  public Double get() {
    return StrictMath.exp(probability);
  }

  /**
   * @return Retrieves the log probability associated with a label.
   */
  @Override
  public Double getLog() {
    return probability;
  }

  /**
   * @return Retrieves the probabilities associated with all labels,
   */
  @Override
  public T getLabel() {
    return label;
  }

  @Override
  public String toString() {
    return label + ":" + probability;
  }
}
