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
import java.util.PriorityQueue;
import java.util.Queue;

import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.BeamSearchContextGenerator;
import opennlp.tools.util.Cache;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;

/**
 * Performs k-best search over a sequence.
 * <p>
 * This is based on the description in Ratnaparkhi (1998),
 * PhD diss, Univ. of Pennsylvania.
 *
 * @see Sequence
 * @see SequenceValidator
 * @see BeamSearchContextGenerator
 */
public class BeamSearch implements SequenceClassificationModel {

  public static final String BEAM_SIZE_PARAMETER = "BeamSize";

  private static final Object[] EMPTY_ADDITIONAL_CONTEXT = new Object[0];

  protected final int size;
  protected final MaxentModel model;

  private final double[] probs;
  private Cache<String[], double[]> contextsCache;
  private static final int zeroLog = -100000;

  /**
   * Immutable node in a backward-linked chain of outcome candidates, used only inside
   * {@link #bestSequences(int, Object[], Object[], double, BeamSearchContextGenerator, SequenceValidator)}.
   * A node stores its own outcome plus a parent link, so extending a candidate is O(1);
   * the full outcome array is materialized only when a candidate is expanded.
   * Score accumulation ({@code parent.score + StrictMath.log(prob)}) mirrors
   * {@link Sequence#Sequence(Sequence, String, double)}, and descending score order
   * mirrors {@link Sequence#compareTo(Sequence)}, so search results stay bit-identical
   * to a search over {@link Sequence} instances.
   */
  private static final class SearchNode implements Comparable<SearchNode> {
    private final SearchNode parent;
    private final String outcome; // null on the root
    private final double prob;
    private final double score;
    private final int size;

    /**
     * Creates the root node: the empty candidate with score {@code 0}.
     */
    private SearchNode() {
      this.parent = null;
      this.outcome = null;
      this.prob = 0d;
      this.score = 0d;
      this.size = 0;
    }

    /**
     * Creates a candidate extending {@code parent} by one outcome.
     *
     * @param parent The candidate to extend. Must not be {@code null}.
     * @param outcome The outcome to append.
     * @param prob The probability of {@code outcome}.
     */
    private SearchNode(SearchNode parent, String outcome, double prob) {
      this.parent = parent;
      this.outcome = outcome;
      this.prob = prob;
      this.score = parent.score + StrictMath.log(prob);
      this.size = parent.size + 1;
    }

    /**
     * @return The outcomes on the path from the root to this node, in sequence order.
     */
    private String[] outcomes() {
      final String[] outcomes = new String[size];
      SearchNode node = this;
      for (int i = size - 1; i >= 0; i--) {
        outcomes[i] = node.outcome;
        node = node.parent;
      }
      return outcomes;
    }

    /**
     * Orders nodes by descending score, as {@link Sequence#compareTo(Sequence)} does;
     * nodes with equal scores keep the order of the queue that holds them.
     */
    @Override
    public int compareTo(SearchNode other) {
      return Double.compare(other.score, this.score);
    }
  }

  /**
   * Initializes a {@link BeamSearch} instance.
   *
   * @param size The size of the beam (k).
   * @param model The {@link MaxentModel} for assigning probabilities to the sequence outcomes.
   */
  public BeamSearch(int size, MaxentModel model) {
    this(size, model, 0);
  }

  /**
   * Initializes a {@link BeamSearch} instance.
   *
   * @param size The size of the beam (k).
   * @param model The {@link MaxentModel} for assigning probabilities to the sequence outcomes.
   * @param cacheSize The capacity of the {@link Cache} to use.
   */
  public BeamSearch(int size, MaxentModel model, int cacheSize) {

    this.size = size;
    this.model = model;

    if (cacheSize > 0) {
      contextsCache = new Cache<>(cacheSize);
    }

    this.probs = new double[model.getNumOutcomes()];
  }

  /**
   * Computes the best sequence of outcomes based on the {@link MaxentModel}.
   *
   * @param numSequences The number of sequences.
   * @param sequence The input {@link T} sequence.
   * @param additionalContext An {@link Object[]} of additional context.
   *     This is passed to the context generator blindly with the
   *     assumption that the context are appropriate.
   * @param minSequenceScore The minimum sequence score to use.
   * @param cg The {@link BeamSearchContextGenerator context generator} to use.
   * @param validator The {@link SequenceValidator} to validate sequences.
   *
   * @return The top ranked {@link Sequence} of outcomes or {@code null}
   *         if no sequence could be found.
   */
  @Override
  public <T> Sequence[] bestSequences(int numSequences, T[] sequence,
      Object[] additionalContext, double minSequenceScore,
      BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator) {

    Queue<SearchNode> prev = new PriorityQueue<>(size);
    Queue<SearchNode> next = new PriorityQueue<>(size);
    Queue<SearchNode> tmp;
    prev.add(new SearchNode());

    if (additionalContext == null) {
      additionalContext = EMPTY_ADDITIONAL_CONTEXT;
    }

    for (int i = 0; i < sequence.length; i++) {
      int sz = StrictMath.min(size, prev.size());

      for (int sc = 0; prev.size() > 0 && sc < sz; sc++) {
        final SearchNode top = prev.remove();
        final String[] outcomes = top.outcomes();
        final String[] contexts = cg.getContext(i, sequence, outcomes, additionalContext);
        final double[] scores;
        if (contextsCache != null) {
          scores = contextsCache.computeIfAbsent(contexts, c -> {
            // eval() writes into probs; cache values must be immutable copies for reuse.
            double[] res = model.eval(c, probs);
            double[] copy = new double[res.length];
            System.arraycopy(res, 0, copy, 0, res.length);
            return copy;
          });
        } else {
          scores = model.eval(contexts, probs);
        }

        double[] temp_scores = new double[scores.length];
        System.arraycopy(scores, 0, temp_scores, 0, scores.length);

        Arrays.sort(temp_scores);

        double min = temp_scores[StrictMath.max(0,scores.length - size)];

        for (int p = 0; p < scores.length; p++) {
          if (scores[p] >= min) {
            String out = model.getOutcome(p);
            if (validator.validSequence(i, sequence, outcomes, out)) {
              final SearchNode ns = new SearchNode(top, out, scores[p]);
              if (ns.score > minSequenceScore) {
                next.add(ns);
              }
            }
          }
        }

        if (next.size() == 0) { //if no advanced sequences, advance all valid
          for (int p = 0; p < scores.length; p++) {
            String out = model.getOutcome(p);
            if (validator.validSequence(i, sequence, outcomes, out)) {
              final SearchNode ns = new SearchNode(top, out, scores[p]);
              if (ns.score > minSequenceScore) {
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

    int numSeq = StrictMath.min(numSequences, prev.size());
    Sequence[] topSequences = new Sequence[numSeq];

    for (int seqIndex = 0; seqIndex < numSeq; seqIndex++) {
      final SearchNode winner = prev.remove();
      final String[] outs = new String[winner.size];
      final double[] winnerProbs = new double[winner.size];
      SearchNode node = winner;
      for (int j = winner.size - 1; j >= 0; j--) {
        outs[j] = node.outcome;
        winnerProbs[j] = node.prob;
        node = node.parent;
      }
      // Sequence.add accumulates score += StrictMath.log(p) per element, so rebuilding in
      // chain order yields a score bit-identical to the node's accumulated score.
      final Sequence seq = new Sequence();
      for (int j = 0; j < outs.length; j++) {
        seq.add(outs[j], winnerProbs[j]);
      }
      topSequences[seqIndex] = seq;
    }

    return topSequences;
  }

  /**
   * Computes the best sequence of outcomes based on the {@link MaxentModel}.
   *
   * @param numSequences The number of sequences.
   * @param sequence The input {@link T} sequence.
   * @param additionalContext An {@link Object[]} of additional context.
   *     This is passed to the context generator blindly with the
   *     assumption that the context are appropriate.
   * @param cg The {@link BeamSearchContextGenerator context generator} to use.
   * @param validator The {@link SequenceValidator} to validate sequences.
   *
   * @return The top ranked {@link Sequence} of outcomes or {@code null}
   *         if no sequence could be found.
   */
  @Override
  public <T> Sequence[] bestSequences(int numSequences, T[] sequence,
      Object[] additionalContext, BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator) {
    return bestSequences(numSequences, sequence, additionalContext, zeroLog, cg, validator);
  }

  /**
   * Computes the best sequence of outcomes based on the {@link MaxentModel}.
   *
   * @param sequence The input {@link T} sequence.
   * @param additionalContext An {@link Object[]} of additional context.
   *     This is passed to the context generator blindly with the
   *     assumption that the context are appropriate.
   * @param cg The {@link BeamSearchContextGenerator context generator} to use.
   * @param validator The {@link SequenceValidator} to validate sequences.
   *
   * @return The top ranked {@link Sequence} of outcomes or {@code null}
   *         if no sequence could be found.
   */
  @Override
  public <T> Sequence bestSequence(T[] sequence, Object[] additionalContext,
      BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator) {
    Sequence[] sequences = bestSequences(1, sequence, additionalContext, cg, validator);

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
