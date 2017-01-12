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
 * @param <T> the label (category) class
 *
 */
public class Probability<T> {
  protected T label;
  protected double probability = 1.0;

  public Probability(T label) {
    this.label = label;
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability.
   *
   * @param probability the probability to assign
   */
  public void set(double probability) {
    this.probability = probability;
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability.
   *
   * @param probability the probability to assign
   */
  public void set(Probability probability) {
    this.probability = probability.get();
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability,
   * if the new probability is greater than the old one.
   *
   * @param probability the probability to assign
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
   * @param probability the probability to assign
   */
  public void setIfLarger(Probability probability) {
    if (this.probability < probability.get()) {
      this.probability = probability.get();
    }
  }

  /**
   * Checks if a probability is greater than the old one.
   *
   * @param probability the probability to assign
   */
  public boolean isLarger(Probability probability) {
    return this.probability < probability.get();
  }

  /**
   * Assigns a log probability to a label, discarding any previously assigned probability.
   *
   * @param probability the log probability to assign
   */
  public void setLog(double probability) {
    set(Math.exp(probability));
  }

  /**
   * Compounds the existing probability mass on the label with the new probability passed in to the method.
   *
   * @param probability the probability weight to add
   */
  public void addIn(double probability) {
    set(this.probability * probability);
  }

  /**
   * Returns the probability associated with a label
   *
   * @return the probability associated with the label
   */
  public Double get() {
    return probability;
  }

  /**
   * Returns the log probability associated with a label
   *
   * @return the log probability associated with the label
   */
  public Double getLog() {
    return Math.log(get());
  }

  /**
   * Returns the probabilities associated with all labels
   *
   * @return the HashMap of labels and their probabilities
   */
  public T getLabel() {
    return label;
  }

  public String toString() {
    return label == null ? "" + probability : label.toString() + ":" + probability;
  }
}
