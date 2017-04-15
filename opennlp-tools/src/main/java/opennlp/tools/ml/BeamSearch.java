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

package opennlp.tools.ml;

import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.BeamSearchContextGenerator;
import opennlp.tools.util.Cache;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;

/**
 * Performs k-best search over sequence.  This is based on the description in
 * Ratnaparkhi (1998), PhD diss, Univ. of Pennsylvania.
 *
 * @see Sequence
 * @see SequenceValidator
 * @see BeamSearchContextGenerator
 */
public class BeamSearch<T> implements SequenceClassificationModel<T> {

  public static final String BEAM_SIZE_PARAMETER = "BeamSize";

  private static final Object[] EMPTY_ADDITIONAL_CONTEXT = new Object[0];

  protected int size;
  protected MaxentModel model;

  private double[] probs;
  private Cache<String[], double[]> contextsCache;
  private static final int zeroLog = -100000;

  /**
   * Creates new search object.
   *
   * @param size The size of the beam (k).
   * @param model the model for assigning probabilities to the sequence outcomes.
   */
  public BeamSearch(int size, MaxentModel model) {
    this(size, model, 0);
  }

  public BeamSearch(int size, MaxentModel model, int cacheSize) {

    this.size = size;
    this.model = model;

    if (cacheSize > 0) {
      contextsCache = new Cache<>(cacheSize);
    }

    this.probs = new double[model.getNumOutcomes()];
  }

  /**
   * Returns the best sequence of outcomes based on model for this object.
   *
   * @param sequence The input sequence.
   * @param additionalContext An Object[] of additional context.
   *     This is passed to the context generator blindly with the
   *     assumption that the context are appropiate.
   *
   * @return The top ranked sequence of outcomes or null if no sequence could be found
   */
  public Sequence[] bestSequences(int numSequences, T[] sequence,
      Object[] additionalContext, double minSequenceScore,
      BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator) {

    Queue<Sequence> prev = new PriorityQueue<>(size);
    Queue<Sequence> next = new PriorityQueue<>(size);
    Queue<Sequence> tmp;
    prev.add(new Sequence());

    if (additionalContext == null) {
      additionalContext = EMPTY_ADDITIONAL_CONTEXT;
    }

    for (int i = 0; i < sequence.length; i++) {
      int sz = Math.min(size, prev.size());

      for (int sc = 0; prev.size() > 0 && sc < sz; sc++) {
        Sequence top = prev.remove();
        List<String> tmpOutcomes = top.getOutcomes();
        String[] outcomes = tmpOutcomes.toArray(new String[tmpOutcomes.size()]);
        String[] contexts = cg.getContext(i, sequence, outcomes, additionalContext);
        double[] scores;
        if (contextsCache != null) {
          scores = contextsCache.computeIfAbsent(contexts, c -> model.eval(c, probs));
        } else {
          scores = model.eval(contexts, probs);
        }

        double[] temp_scores = new double[scores.length];
        System.arraycopy(scores, 0, temp_scores, 0, scores.length);

        Arrays.sort(temp_scores);

        double min = temp_scores[Math.max(0,scores.length - size)];

        for (int p = 0; p < scores.length; p++) {
          if (scores[p] >= min) {
            String out = model.getOutcome(p);
            if (validator.validSequence(i, sequence, outcomes, out)) {
              Sequence ns = new Sequence(top, out, scores[p]);
              if (ns.getScore() > minSequenceScore) {
                next.add(ns);
              }
            }
          }
        }

        if (next.size() == 0) { //if no advanced sequences, advance all valid
          for (int p = 0; p < scores.length; p++) {
            String out = model.getOutcome(p);
            if (validator.validSequence(i, sequence, outcomes, out)) {
              Sequence ns = new Sequence(top, out, scores[p]);
              if (ns.getScore() > minSequenceScore) {
                next.add(ns);
              }
            }
          }
        }
      }

      //    make prev = next; and re-init next (we reuse existing prev set once we clear it)
      prev.clear();
      tmp = prev;
      prev = next;
      next = tmp;
    }

    int numSeq = Math.min(numSequences, prev.size());
    Sequence[] topSequences = new Sequence[numSeq];

    for (int seqIndex = 0; seqIndex < numSeq; seqIndex++) {
      topSequences[seqIndex] = prev.remove();
    }

    return topSequences;
  }

  public Sequence[] bestSequences(int numSequences, T[] sequence,
      Object[] additionalContext, BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator) {
    return bestSequences(numSequences, sequence, additionalContext, zeroLog, cg, validator);
  }

  public Sequence bestSequence(T[] sequence, Object[] additionalContext,
      BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator) {
    Sequence[] sequences =  bestSequences(1, sequence, additionalContext, cg, validator);

    if (sequences.length > 0)
      return sequences[0];
    else
      return null;
  }

  @Override
  public String[] getOutcomes() {
    String[] outcomes = new String[model.getNumOutcomes()];

    for (int i = 0; i < model.getNumOutcomes(); i++) {
      outcomes[i] = model.getOutcome(i);
    }

    return outcomes;
  }
}
