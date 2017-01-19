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

/** Represents a weighted sequence of outcomes. */
public class Sequence implements Comparable<Sequence> {
  private double score;
  private List<String> outcomes;
  private List<Double> probs;
  private static final Double ONE = 1.0d;

  /** Creates a new sequence of outcomes. */
  public Sequence() {
    outcomes = new ArrayList<>(1);
    probs = new ArrayList<>(1);
    score = 0d;
  }

  public Sequence(Sequence s) {
    outcomes = new ArrayList<>(s.outcomes.size() + 1);
    outcomes.addAll(s.outcomes);
    probs = new ArrayList<>(s.probs.size() + 1);
    probs.addAll(s.probs);
    score = s.score;
  }

  public Sequence(Sequence s,String outcome, double p) {
    outcomes = new ArrayList<>(s.outcomes.size() + 1);
    outcomes.addAll(s.outcomes);
    outcomes.add(outcome);
    probs = new ArrayList<>(s.probs.size() + 1);
    probs.addAll(s.probs);
    probs.add(p);
    score = s.score + Math.log(p);
  }

  public Sequence(List<String> outcomes) {
    this.outcomes = outcomes;
    this.probs = Collections.nCopies(outcomes.size(),ONE);
  }

  public int compareTo(Sequence s) {
    if (score < s.score)
      return 1;
    if (score > s.score)
      return -1;
    return 0;
  }

  /** Adds an outcome and probability to this sequence.
   * @param outcome the outcome to be added.
   * @param p the probability associated with this outcome.
   */
  public void add(String outcome, double p) {
    outcomes.add(outcome);
    probs.add(p);
    score += Math.log(p);
  }

  /** Returns a list of outcomes for this sequence.
   * @return a list of outcomes.
   */
  public List<String> getOutcomes() {
    return outcomes;
  }

  /** Returns an array of probabilities associated with the outcomes of this sequence.
   * @return an array of probabilities.
   */
  public double[] getProbs() {
    double[] ps = new double[probs.size()];
    getProbs(ps);
    return ps;
  }

  /**
   * Returns the score of this sequence.
   * @return The score of this sequence.
   */
  public double getScore() {
    return score;
  }

  /** Populates  an array with the probabilities associated with the outcomes of this sequence.
   * @param ps a pre-allocated array to use to hold the values of the
   *           probabilities of the outcomes for this sequence.
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
