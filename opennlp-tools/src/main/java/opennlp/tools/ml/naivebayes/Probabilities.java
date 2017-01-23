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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class implementing the probability distribution over labels returned by a classifier.
 *
 * @param <T> the label (category) class
 *
 */
public abstract class Probabilities<T> {
  protected Map<T, Double> map = new HashMap<>();

  protected transient boolean isNormalised = false;
  protected Map<T, Double> normalised;

  protected double confidence = 0.0;

  /**
   * Assigns a probability to a label, discarding any previously assigned probability.
   *
   * @param t           the label to which the probability is being assigned
   * @param probability the probability to assign
   */
  public void set(T t, double probability) {
    isNormalised = false;
    map.put(t, probability);
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability.
   *
   * @param t           the label to which the probability is being assigned
   * @param probability the probability to assign
   */
  public void set(T t, Probability<T> probability) {
    isNormalised = false;
    map.put(t, probability.get());
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability,
   * if the new probability is greater than the old one.
   *
   * @param t           the label to which the probability is being assigned
   * @param probability the probability to assign
   */
  public void setIfLarger(T t, double probability) {
    Double p = map.get(t);
    if (p == null || probability > p) {
      isNormalised = false;
      map.put(t, probability);
    }
  }

  /**
   * Assigns a log probability to a label, discarding any previously assigned probability.
   *
   * @param t           the label to which the log probability is being assigned
   * @param probability the log probability to assign
   */
  public void setLog(T t, double probability) {
    set(t, Math.exp(probability));
  }

  /**
   * Compounds the existing probability mass on the label with the new probability passed in to the method.
   *
   * @param t           the label whose probability mass is being updated
   * @param probability the probability weight to add
   * @param count       the amplifying factor for the probability compounding
   */
  public void addIn(T t, double probability, int count) {
    isNormalised = false;
    Double p = map.get(t);
    if (p == null)
      p = 1.0;
    probability = Math.pow(probability, count);
    map.put(t, p * probability);
  }

  /**
   * Returns the probability associated with a label
   *
   * @param t the label whose probability needs to be returned
   * @return the probability associated with the label
   */
  public Double get(T t) {
    Double d = normalize().get(t);
    if (d == null)
      return 0.0;
    return d;
  }

  /**
   * Returns the log probability associated with a label
   *
   * @param t the label whose log probability needs to be returned
   * @return the log probability associated with the label
   */
  public Double getLog(T t) {
    return Math.log(get(t));
  }

  /**
   * Returns the probabilities associated with all labels
   *
   * @return the HashMap of labels and their probabilities
   */
  public Set<T> getKeys() {
    return map.keySet();
  }

  /**
   * Returns the probabilities associated with all labels
   *
   * @return the HashMap of labels and their probabilities
   */
  public Map<T, Double> getAll() {
    return normalize();
  }

  private Map<T, Double> normalize() {
    if (isNormalised)
      return normalised;
    Map<T, Double> temp = createMapDataStructure();
    double sum = 0;
    for (T t : map.keySet()) {
      Double p = map.get(t);
      if (p != null) {
        sum += p;
      }
    }
    for (T t : temp.keySet()) {
      Double p = temp.get(t);
      if (p != null) {
        temp.put(t, p / sum);
      }
    }
    normalised = temp;
    isNormalised = true;
    return temp;
  }

  protected Map<T, Double> createMapDataStructure() {
    return new HashMap<>();
  }

  /**
   * Returns the most likely label
   *
   * @return the label that has the highest associated probability
   */
  public T getMax() {
    double max = 0;
    T maxT = null;
    for (T t : map.keySet()) {
      Double temp = map.get(t);
      if (temp >= max) {
        max = temp;
        maxT = t;
      }
    }
    return maxT;
  }

  /**
   * Returns the probability of the most likely label
   *
   * @return the highest probability
   */
  public double getMaxValue() {
    return get(getMax());
  }

  public void discardCountsBelow(double i) {
    List<T> labelsToRemove = new ArrayList<>();
    for (T label : map.keySet()) {
      Double sum = map.get(label);
      if (sum == null) sum = 0.0;
      if (sum < i)
        labelsToRemove.add(label);
    }
    for (T label : labelsToRemove) {
      map.remove(label);
    }
  }

  /**
   * Returns the best confidence with which this set of probabilities has been calculated.
   * This is a function of the amount of data that supports the assertion.
   * It is also a measure of the accuracy of the estimator of the probability.
   *
   * @return the best confidence of the probabilities
   */
  public double getConfidence() {
    return confidence;
  }

  /**
   * Sets the best confidence with which this set of probabilities has been calculated.
   * This is a function of the amount of data that supports the assertion.
   * It is also a measure of the accuracy of the estimator of the probability.
   *
   * @param confidence the confidence in the probabilities
   */
  public void setConfidence(double confidence) {
    this.confidence = confidence;
  }

  public String toString() {
    return getAll().toString();
  }

}
