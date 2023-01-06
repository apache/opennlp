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
 * Class implementing the probability for a label.
 *
 * @param <T> The label (category) class.
 *
 */
public class Probability<T> {
  protected T label;
  protected double probability = 1.0;

  /**
   * Instantiates a {@link Probability} with a given {@code label}.
   * 
   * @param label The {@link T label} to assign probabilities to.
   */
  public Probability(T label) {
    this.label = label;
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability.
   *
   * @param probability The probability to assign.
   */
  public void set(double probability) {
    this.probability = probability;
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability.
   *
   * @param probability The probability to assign.
   */
  public void set(Probability<T> probability) {
    this.probability = probability.get();
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability,
   * if the new probability is greater than the old one.
   *
   * @param probability The probability to assign.
   */
  public void setIfLarger(double probability) {
    if (this.probability < probability) {
      this.probability = probability;
    }
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability,
   * if the new probability is greater than the old one.
   *
   * @param probability The probability to assign.
   */
  public void setIfLarger(Probability<T> probability) {
    if (this.probability < probability.get()) {
      this.probability = probability.get();
    }
  }

  /**
   * Checks if a probability is greater than the old one.
   *
   * @param probability The probability to assign.
   * @return {@code true} if a probability is greater than the old one, {@code false} otherwise.
   */
  public boolean isLarger(Probability<T> probability) {
    return this.probability < probability.get();
  }

  /**
   * Assigns a log probability to a label, discarding any previously assigned probability.
   *
   * @param probability The log probability to assign.
   */
  public void setLog(double probability) {
    set(StrictMath.exp(probability));
  }

  /**
   * Compounds the existing probability mass on the label with the new probability passed in to the method.
   *
   * @param probability The probability weight to add.
   */
  public void addIn(double probability) {
    set(this.probability * probability);
  }

  /**
   * @return Retrieves the probability associated with a label.
   */
  public Double get() {
    return probability;
  }

  /**
   * @return Retrieves the log probability associated with a label.
   */
  public Double getLog() {
    return StrictMath.log(get());
  }

  /**
   * @return Retrieves the probabilities associated with all labels.
   */
  public T getLabel() {
    return label;
  }

  public String toString() {
    return label == null ? "" + probability : label + ":" + probability;
  }
}
