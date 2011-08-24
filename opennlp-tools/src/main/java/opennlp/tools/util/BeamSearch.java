/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import java.util.Arrays;
import java.util.List;

import opennlp.model.MaxentModel;

/**
 * Performs k-best search over sequence.  This is based on the description in
 * Ratnaparkhi (1998), PhD diss, Univ. of Pennsylvania.
 *
 * @see Sequence
 * @see SequenceValidator
 * @see BeamSearchContextGenerator
 */
public class BeamSearch<T> {

  private static final Object[] EMPTY_ADDITIONAL_CONTEXT = new Object[0];

  protected int size;
  protected BeamSearchContextGenerator<T> cg;
  protected MaxentModel model;
  private SequenceValidator<T> validator;

  private double[] probs;
  private Cache contextsCache;
  private static final int zeroLog = -100000;

  /**
   * Creates new search object.
   *
   * @param size The size of the beam (k).
   * @param cg the context generator for the model.
   * @param model the model for assigning probabilities to the sequence outcomes.
   */
  public BeamSearch(int size, BeamSearchContextGenerator<T> cg, MaxentModel model) {
    this(size, cg, model, null, 0);
  }

  public BeamSearch(int size, BeamSearchContextGenerator<T> cg, MaxentModel model,
      int cacheSize) {
    this (size, cg, model, null, cacheSize);
  }

  public BeamSearch(int size, BeamSearchContextGenerator<T> cg, MaxentModel model,
      SequenceValidator<T> validator, int cacheSize) {

    this.size = size;
    this.cg = cg;
    this.model = model;
    this.validator = validator;

    if (cacheSize > 0) {
      contextsCache = new Cache(cacheSize);
    }

    this.probs = new double[model.getNumOutcomes()];
  }

  /**
   * Note:
   * This method will be private in the future because clients can now
   * pass a validator to validate the sequence.
   *
   * @see SequenceValidator
   */
  private boolean validSequence(int i, T[] inputSequence, String[] outcomesSequence, String outcome) {

    if (validator != null) {
      return validator.validSequence(i, inputSequence, outcomesSequence, outcome);
    }
    else {
      return true;
    }
  }

  public Sequence[] bestSequences(int numSequences, T[] sequence, Object[] additionalContext) {
    return bestSequences(numSequences, sequence, additionalContext, zeroLog);
  }

  /**
   * Returns the best sequence of outcomes based on model for this object.
   *
   * @param numSequences The maximum number of sequences to be returned.
   * @param sequence The input sequence.
   * @param additionalContext An Object[] of additional context.  This is passed to the context generator blindly with the assumption that the context are appropiate.
   * @param minSequenceScore A lower bound on the score of a returned sequence.
   * @return An array of the top ranked sequences of outcomes.
   */
  public Sequence[] bestSequences(int numSequences, T[] sequence, Object[] additionalContext, double minSequenceScore) {

    Heap<Sequence> prev = new ListHeap<Sequence>(size);
    Heap<Sequence> next = new ListHeap<Sequence>(size);
    Heap<Sequence> tmp;
    prev.add(new Sequence());

    if (additionalContext == null) {
      additionalContext = EMPTY_ADDITIONAL_CONTEXT;
    }

    for (int i = 0; i < sequence.length; i++) {
      int sz = Math.min(size, prev.size());

      for (int sc = 0; prev.size() > 0 && sc < sz; sc++) {
        Sequence top = prev.extract();
        List<String> tmpOutcomes = top.getOutcomes();
        String[] outcomes = tmpOutcomes.toArray(new String[tmpOutcomes.size()]);
        String[] contexts = cg.getContext(i, sequence, outcomes, additionalContext);
        double[] scores;
        if (contextsCache != null) {
          scores = (double[]) contextsCache.get(contexts);
          if (scores == null) {
            scores = model.eval(contexts, probs);
            contextsCache.put(contexts,scores);
          }
        }
        else {
          scores = model.eval(contexts, probs);
        }

        double[] temp_scores = new double[scores.length];
        for (int c = 0; c < scores.length; c++) {
          temp_scores[c] = scores[c];
        }

        Arrays.sort(temp_scores);

        double min = temp_scores[Math.max(0,scores.length-size)];

        for (int p = 0; p < scores.length; p++) {
          if (scores[p] < min)
            continue; //only advance first "size" outcomes
          String out = model.getOutcome(p);
          if (validSequence(i, sequence, outcomes, out)) {
            Sequence ns = new Sequence(top, out, scores[p]);
            if (ns.getScore() > minSequenceScore) {
              next.add(ns);
            }
          }
        }

        if (next.size() == 0) {//if no advanced sequences, advance all valid
          for (int p = 0; p < scores.length; p++) {
            String out = model.getOutcome(p);
            if (validSequence(i, sequence, outcomes, out)) {
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
      topSequences[seqIndex] = prev.extract();
    }

    return topSequences;
  }

  /**
   * Returns the best sequence of outcomes based on model for this object.
   *
   * @param sequence The input sequence.
   * @param additionalContext An Object[] of additional context.  This is passed to the context generator blindly with the assumption that the context are appropiate.
   *
   * @return The top ranked sequence of outcomes or null if no sequence could be found
   */
  public Sequence bestSequence(T[] sequence, Object[] additionalContext) {
    Sequence sequences[] =  bestSequences(1, sequence, additionalContext,zeroLog);
    
    if (sequences.length > 0)
      return sequences[0];
    else 
      return null;
  }
}
