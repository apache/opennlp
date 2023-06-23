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

package opennlp.tools.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a weighted sequence of outcomes.
 */
public class Sequence implements Comparable<Sequence> {
  private double score;
  private final List<String> outcomes;
  private final List<Double> probs;
  private static final Double ONE = 1.0d;

  /**
   * Initializes a new {@link Sequence} of outcomes.
   */
  public Sequence() {
    outcomes = new ArrayList<>(1);
    probs = new ArrayList<>(1);
    score = 0d;
  }

  /**
   * Initializes a new {@link Sequence} of outcomes from an existing {@link Sequence}.
   *
   * @param s An existing {@link Sequence} used as input.
   */
  public Sequence(Sequence s) {
    outcomes = new ArrayList<>(s.outcomes.size() + 1);
    outcomes.addAll(s.outcomes);
    probs = new ArrayList<>(s.probs.size() + 1);
    probs.addAll(s.probs);
    score = s.score;
  }

  /**
   * Initializes a new {@link Sequence} of outcomes from an existing {@link Sequence}.
   *
   * @param s An existing {@link Sequence} used as input.
   * @param outcome An extra outcome to add to {@code s}.
   * @param p A extra probability of the {@code outcome}.
   */
  public Sequence(Sequence s, String outcome, double p) {
    outcomes = new ArrayList<>(s.outcomes.size() + 1);
    outcomes.addAll(s.outcomes);
    outcomes.add(outcome);
    probs = new ArrayList<>(s.probs.size() + 1);
    probs.addAll(s.probs);
    probs.add(p);
    score = s.score + StrictMath.log(p);
  }

  /**
   * Initializes a new {@link Sequence} of outcomes from a list of
   * {@code outcomes}. The probabilities for each outcome will be
   * equally initialized to {@link #ONE}.
   *
   * @param outcomes Several existing outcomes used as input.
   */
  public Sequence(List<String> outcomes) {
    this.outcomes = outcomes;
    this.probs = Collections.nCopies(outcomes.size(),ONE);
  }

  @Override
  public int compareTo(Sequence s) {
    return Double.compare(s.score, score);
  }

  @Override
  public int hashCode() {
    return Objects.hash(outcomes, probs, score);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;

    if (obj instanceof Sequence other) {
      double epsilon = 0.0000001;
      return Objects.equals(outcomes, other.outcomes) &&
          Objects.equals(probs, other.probs) &&
          StrictMath.abs(score - other.score) < epsilon;
    }

    return false;
  }

  /** Adds an outcome and probability to this sequence.
   * @param outcome the outcome to be added.
   * @param p the probability associated with this outcome.
   */
  public void add(String outcome, double p) {
    outcomes.add(outcome);
    probs.add(p);
    score += StrictMath.log(p);
  }

  /**
   * @return Retrieves a list of outcomes for this {@link Sequence}.
   */
  public List<String> getOutcomes() {
    return List.copyOf(outcomes);
  }

  /**
   * @return Retrieves the size of the outcomes for this {@link Sequence}.
   */
  public int getSize() {
    return outcomes.size();
  }

  /**
   * @param index must be greater than or equal to zero and must be less than {@link Sequence#getSize()}.
   * @return the outcome at the specified index.
   * @throws IndexOutOfBoundsException thrown if the given index is out of range.
   */
  public String getOutcome(int index) {
    return outcomes.get(index);
  }

  /**
   * @param index must be greater than or equal to zero and must be less than {@link Sequence#getSize()}.
   * @return the probability at the specified index.
   * @throws IndexOutOfBoundsException thrown if the given index is out of range.
   */
  public double getProb(int index) {
    return probs.get(index);
  }

  /**
   * @return Retrieves an array of probabilities associated with the {@link Sequence} outcomes.
   */
  public double[] getProbs() {
    double[] ps = new double[probs.size()];
    getProbs(ps);
    return ps;
  }

  /**
   * @return Retrieves the score of this {@link Sequence}.
   */
  public double getScore() {
    return score;
  }

  /**
   * Populates an array with the probabilities associated with the {@link Sequence} outcomes.
   * 
   * @param ps A pre-allocated array to hold the values of the
   *           probabilities of the outcomes for this {@link Sequence}.
   */
  public void getProbs(double[] ps) {
    for (int pi = 0, pl = probs.size(); pi < pl; pi++) {
      ps[pi] = probs.get(pi);
    }
  }

  @Override
  public String toString() {
    return score + " " + outcomes;
  }
}
