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
import java.util.Map.Entry;
import java.util.Set;

/**
 * Class implementing the probability distribution over labels returned by a classifier.
 *
 * @param <T> The label (category) class.
 *
 */
public abstract class Probabilities<T> {
  protected Map<T, Double> map = new HashMap<>();

  protected transient boolean isNormalised = false;
  protected Map<T, Double> normalised;

  protected double confidence = 0.0;

  /**
   * Assigns a probability to a label {@code t},
   * discarding any previously assigned probability.
   *
   * @param t           The label to which the probability is being assigned.
   * @param probability The probability to assign.
   */
  public void set(T t, double probability) {
    isNormalised = false;
    map.put(t, probability);
  }

  /**
   * Assigns a probability to a label {@code t},
   * discarding any previously assigned probability.
   *
   * @param t           The label to which the probability is being assigned.
   * @param probability The probability to assign.
   */
  public void set(T t, Probability<T> probability) {
    isNormalised = false;
    map.put(t, probability.get());
  }

  /**
   * Assigns a probability to a label {@code t},
   * discarding any previously assigned probability,
   * if the new probability is greater than the old one.
   *
   * @param t           The label to which the probability is being assigned.
   * @param probability The probability to assign.
   */
  public void setIfLarger(T t, double probability) {
    Double p = map.get(t);
    if (p == null || probability > p) {
      isNormalised = false;
      map.put(t, probability);
    }
  }

  /**
   * Assigns a log probability to a label {@code t},
   * discarding any previously assigned probability.
   *
   * @param t           The label to which the log probability is being assigned.
   * @param probability The log probability to assign.
   */
  public void setLog(T t, double probability) {
    set(t, StrictMath.exp(probability));
  }

  /**
   * Compounds the existing probability mass on the label {@code t}
   * with the new probability passed in to the method.
   *
   * @param t           The label whose probability mass is being updated.
   * @param probability The probability weight to add.
   * @param count       The amplifying factor for the probability compounding.
   */
  public void addIn(T t, double probability, int count) {
    isNormalised = false;
    Double p = map.get(t);
    if (p == null)
      p = 1.0;
    probability = StrictMath.pow(probability, count);
    map.put(t, p * probability);
  }

  /**
   * @param t The label whose probability needs to be returned.
   * @return Retrieves the probability associated with the label.
   */
  public Double get(T t) {
    Double d = normalize().get(t);
    if (d == null)
      return 0.0;
    return d;
  }

  /**
   * @param t The label whose log probability should be returned.
   * @return Retrieves the log probability associated with the label
   */
  public Double getLog(T t) {
    return StrictMath.log(get(t));
  }

  /**
   * @return Retrieves the probabilities associated with all labels
   */
  public Set<T> getKeys() {
    return map.keySet();
  }

  /**
   * @return Retrieves a {@link Map} of labels and their probabilities
   */
  public Map<T, Double> getAll() {
    return normalize();
  }

  private Map<T, Double> normalize() {
    if (isNormalised)
      return normalised;
    Map<T, Double> temp = createMapDataStructure();
    double sum = 0;
    for (Entry<T, Double> entry : map.entrySet()) {
      Double p = entry.getValue();
      if (p != null) {
        sum += p;
      }
    }
    for (Entry<T, Double> entry : temp.entrySet()) {
      T t = entry.getKey();
      Double p = entry.getValue();
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
   * @return Retrieves the label that has the highest associated probability.
   */
  public T getMax() {
    double max = 0;
    T maxT = null;
    for (Entry<T, Double> entry : map.entrySet()) {
      final T t = entry.getKey();
      final Double temp = entry.getValue();
      if (temp >= max) {
        max = temp;
        maxT = t;
      }
    }
    return maxT;
  }

  /**
   * @return Retrieves the probability of the most likely label
   */
  public double getMaxValue() {
    return get(getMax());
  }

  public void discardCountsBelow(double i) {
    List<T> labelsToRemove = new ArrayList<>();
    for (Entry<T, Double> entry : map.entrySet()) {
      T label = entry.getKey();
      Double sum = entry.getValue();
      if (sum == null) sum = 0.0;
      if (sum < i)
        labelsToRemove.add(label);
    }
    for (T label : labelsToRemove) {
      map.remove(label);
    }
  }

  /**
   * @return Retrieves the best confidence with which this set of probabilities has been calculated.
   *         This is a function of the amount of data that supports the assertion.
   *         It is also a measure of the accuracy of the estimator of the probability.
   */
  public double getConfidence() {
    return confidence;
  }

  /**
   * Sets the best confidence with which this set of probabilities has been calculated.
   * This is a function of the amount of data that supports the assertion.
   * It is also a measure of the accuracy of the estimator of the probability.
   *
   * @param confidence The confidence in the probabilities.
   */
  public void setConfidence(double confidence) {
    this.confidence = confidence;
  }

  @Override
  public String toString() {
    return getAll().toString();
  }

}
