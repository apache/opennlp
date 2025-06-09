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
import java.util.Map.Entry;

/**
 * Class implementing the probability distribution over labels returned by
 * a classifier as a log of probabilities.
 * <p>
 * This is necessary because floating point precision in Java does not allow for high-accuracy
 * representation of very low probabilities such as would occur in a text categorizer.
 *
 * @param <T> the label (category) class
 *
 * @see Probabilities
 */
public class LogProbabilities<T> extends Probabilities<T> {

  /**
   * Assigns a {@code probability} to a label {@code t},
   * discarding any previously assigned probability.
   *
   * @param t           The label to which the probability is being assigned.
   * @param probability The probability to assign.
   */
  @Override
  public void set(T t, double probability) {
    isNormalised = false;
    map.put(t, log(probability));
  }

  /**
   * Assigns a {@code probability} to a label {@code t},
   * discarding any previously assigned probability.
   *
   * @param t           The label to which the probability is being assigned.
   * @param probability The {@link Probability<T>} to assign.
   */
  @Override
  public void set(T t, Probability<T> probability) {
    isNormalised = false;
    map.put(t, probability.getLog());
  }

  /**
   * Assigns a {@code probability} to a label {@code t},
   * discarding any previously assigned probability,
   * if the new probability is greater than the old one.
   *
   * @param t           The label to which the probability is being assigned.
   * @param probability The probability to assign.
   */
  @Override
  public void setIfLarger(T t, double probability) {
    double logProbability = log(probability);
    Double p = map.get(t);
    if (p == null || logProbability > p) {
      isNormalised = false;
      map.put(t, logProbability);
    }
  }

  /**
   * Assigns a log {@code probability} to a label {@code t},
   * discarding any previously assigned probability.
   *
   * @param t           The label to which the log probability is being assigned.
   * @param probability The log {@code probability} to assign.
   */
  @Override
  public void setLog(T t, double probability) {
    isNormalised = false;
    map.put(t, probability);
  }

  /**
   * Compounds the existing {@code probability} mass on the label {@code t}
   * with the new probability passed in to the method.
   *
   * @param t           The label whose {@code probability} mass is being updated.
   * @param probability The probability weight to add.
   * @param count       The amplifying factor for the {@code probability} compounding.
   */
  @Override
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
    for (Entry<T, Double> entry : map.entrySet()) {
      final Double p = entry.getValue();
      if (p != null && p > highestLogProbability) {
        highestLogProbability = p;
      }
    }
    double sum = 0;
    for (Entry<T, Double> entry : map.entrySet()) {
      T t = entry.getKey();
      Double p = entry.getValue();
      if (p != null) {
        double temp_p = StrictMath.exp(p - highestLogProbability);
        if (!Double.isNaN(temp_p)) {
          sum += temp_p;
          temp.put(t, temp_p);
        }
      }
    }
    for (Entry<T, Double> entry : temp.entrySet()) {
      final T t = entry.getKey();
      final Double p = entry.getValue();
      if (p != null && sum > Double.MIN_VALUE) {
        temp.put(t, p / sum);
      }
    }
    normalised = temp;
    isNormalised = true;
    return temp;
  }

  private double log(double prob) {
    return StrictMath.log(prob);
  }

  /**
   * @param t The label whose probability shall be returned.
   * @return Retrieves the probability associated with the label {@code t}.
   */
  @Override
  public Double get(T t) {
    Double d = normalize().get(t);
    if (d == null)
      return 0.0;
    return d;
  }

  /**
   * @param t The label whose log probability shall be returned.
   * @return Retrieves the log probability associated with the label {@code t}.
   */
  @Override
  public Double getLog(T t) {
    Double d = map.get(t);
    if (d == null)
      return Double.NEGATIVE_INFINITY;
    return d;
  }

  @Override
  public void discardCountsBelow(double i) {
    i = StrictMath.log(i);
    ArrayList<T> labelsToRemove = new ArrayList<>();
    for (Entry<T, Double> entry : map.entrySet()) {
      final T label = entry.getKey();
      Double sum = entry.getValue();
      if (sum == null) sum = Double.NEGATIVE_INFINITY;
      if (sum < i)
        labelsToRemove.add(label);
    }
    for (T label : labelsToRemove) {
      map.remove(label);
    }
  }

  /**
   * @return Retrieves a {@link Map} of all labels and their probabilities.
   */
  @Override
  public Map<T, Double> getAll() {
    return normalize();
  }

  /**
   * @return Retrieves the label that has the highest associated probability
   */
  @Override
  public T getMax() {
    double max = Double.NEGATIVE_INFINITY;
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
}
