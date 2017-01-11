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
import java.util.Map;

/**
 * Class implementing the probability distribution over labels returned by
 * a classifier as a log of probabilities.
 * This is necessary because floating point precision in Java does not allow for high-accuracy
 * representation of very low probabilities such as would occur in a text categorizer.
 *
 * @param <T> the label (category) class
 *
 */
public class LogProbabilities<T> extends Probabilities<T> {

  /**
   * Assigns a probability to a label, discarding any previously assigned probability.
   *
   * @param t           the label to which the probability is being assigned
   * @param probability the probability to assign
   */
  public void set(T t, double probability) {
    isNormalised = false;
    map.put(t, log(probability));
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability.
   *
   * @param t           the label to which the probability is being assigned
   * @param probability the probability to assign
   */
  public void set(T t, Probability<T> probability) {
    isNormalised = false;
    map.put(t, probability.getLog());
  }

  /**
   * Assigns a probability to a label, discarding any previously assigned probability,
   * if the new probability is greater than the old one.
   *
   * @param t           the label to which the probability is being assigned
   * @param probability the probability to assign
   */
  public void setIfLarger(T t, double probability) {
    double logProbability = log(probability);
    Double p = map.get(t);
    if (p == null || logProbability > p) {
      isNormalised = false;
      map.put(t, logProbability);
    }
  }

  /**
   * Assigns a log probability to a label, discarding any previously assigned probability.
   *
   * @param t           the label to which the log probability is being assigned
   * @param probability the log probability to assign
   */
  public void setLog(T t, double probability) {
    isNormalised = false;
    map.put(t, probability);
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
      p = 0.0;
    probability = log(probability) * count;
    map.put(t, p + probability);
  }

  private Map<T, Double> normalize() {
    if (isNormalised)
      return normalised;
    Map<T, Double> temp = createMapDataStructure();
    double highestLogProbability = Double.NEGATIVE_INFINITY;
    for (T t : map.keySet()) {
      Double p = map.get(t);
      if (p != null && p > highestLogProbability) {
        highestLogProbability = p;
      }
    }
    double sum = 0;
    for (T t : map.keySet()) {
      Double p = map.get(t);
      if (p != null) {
        double temp_p = Math.exp(p - highestLogProbability);
        if (!Double.isNaN(temp_p)) {
          sum += temp_p;
          temp.put(t, temp_p);
        }
      }
    }
    for (T t : temp.keySet()) {
      Double p = temp.get(t);
      if (p != null && sum > Double.MIN_VALUE) {
        temp.put(t, p / sum);
      }
    }
    normalised = temp;
    isNormalised = true;
    return temp;
  }

  private double log(double prob) {
    return Math.log(prob);
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
    Double d = map.get(t);
    if (d == null)
      return Double.NEGATIVE_INFINITY;
    return d;
  }

  public void discardCountsBelow(double i) {
    i = Math.log(i);
    ArrayList<T> labelsToRemove = new ArrayList<>();
    for (T label : map.keySet()) {
      Double sum = map.get(label);
      if (sum == null) sum = Double.NEGATIVE_INFINITY;
      if (sum < i)
        labelsToRemove.add(label);
    }
    for (T label : labelsToRemove) {
      map.remove(label);
    }
  }

  /**
   * Returns the probabilities associated with all labels
   *
   * @return the HashMap of labels and their probabilities
   */
  public Map<T, Double> getAll() {
    return normalize();
  }

  /**
   * Returns the most likely label
   *
   * @return the label that has the highest associated probability
   */
  public T getMax() {
    double max = Double.NEGATIVE_INFINITY;
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
}
