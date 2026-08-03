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

import opennlp.tools.commons.ThreadSafe;
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
 * <p>
 * This implementation is thread-safe. The contexts cache and probability buffer
 * are maintained per-thread via {@link ThreadLocal}.
 * <p>
 * <b>Note:</b> In container environments with classloader isolation (e.g. Jakarta EE),
 * {@link ThreadLocal} state may pin the classloader. Ensure instances do not outlive
 * the application's lifecycle, or call {@link ThreadLocal#remove()} on pooled threads.
 *
 * @see Sequence
 * @see SequenceValidator
 * @see BeamSearchContextGenerator
 */
@ThreadSafe
public class BeamSearch implements SequenceClassificationModel, AutoCloseable {

  public static final String BEAM_SIZE_PARAMETER = "BeamSize";

  private static final Object[] EMPTY_ADDITIONAL_CONTEXT = new Object[0];

  private final int size;
  private final MaxentModel model;

  private static final int ZERO_LOG = -100000;

  private final int cacheSize;

  private final ThreadLocal<CacheState> threadState;

  private static final class CacheState {
    private final double[] probs;
    private final double[] tempScores;
    private final Cache<String[], double[]> cache;

    CacheState(int numOutcomes, int cacheSize) {
      this.probs = new double[numOutcomes];
      this.tempScores = new double[numOutcomes];
      this.cache = cacheSize > 0 ? new Cache<>(cacheSize) : null;
    }
  }

  /**
   * Immutable node in a backward-linked chain of outcome candidates, used only inside
   * {@link #bestSequences(int, Object[], Object[], double, BeamSearchContextGenerator, SequenceValidator)}.
   * A node stores its own outcome plus a parent link, so extending a candidate is O(1);
   * the full outcome array is materialized only when a candidate is expanded.
   * Score accumulation ({@code parent.score + StrictMath.log(prob)}) and
   * {@link #compareTo(SearchNode)} must stay in lockstep with
   * {@link Sequence#Sequence(Sequence, String, double)} and {@link Sequence#compareTo(Sequence)}
   * to keep search results bit-identical to a search over {@link Sequence} instances.
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
     * Orders nodes by descending score, mirroring {@link Sequence#compareTo(Sequence)}.
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
   * Initializes a {@link BeamSearch} instance with an optional per-thread contexts cache.
   *
   * @param size The size of the beam (k).
   * @param model The {@link MaxentModel} for assigning probabilities to the sequence outcomes.
   * @param cacheSize The capacity of the per-thread contexts cache. Use {@code 0} to disable
   *     only that cache; per-thread score buffers are still allocated so evaluation stays
   *     thread-safe (see {@link CacheState}).
   */
  public BeamSearch(int size, MaxentModel model, int cacheSize) {

    this.size = size;
    this.model = model;
    this.cacheSize = cacheSize;
    this.threadState = ThreadLocal.withInitial(
        () -> new CacheState(model.getNumOutcomes(), cacheSize));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Sequence[] bestSequences(final int numSequences, final T[] sequence,
      final Object[] additionalContext, final double minSequenceScore,
      final BeamSearchContextGenerator<T> cg, final SequenceValidator<T> validator) {

    final CacheState state = threadState.get();

    Queue<SearchNode> prev = new PriorityQueue<>(size);
    Queue<SearchNode> next = new PriorityQueue<>(size);
    Queue<SearchNode> tmp;
    prev.add(new SearchNode());

    Object[] context = additionalContext;
    if (context == null) {
      context = EMPTY_ADDITIONAL_CONTEXT;
    }

    for (int i = 0; i < sequence.length; i++) {
      final int sz = StrictMath.min(size, prev.size());

      for (int sc = 0; prev.size() > 0 && sc < sz; sc++) {
        final SearchNode top = prev.remove();
        final String[] outcomes = top.outcomes();
        final String[] contexts = cg.getContext(i, sequence, outcomes, context);
        final double[] scores;
        if (state.cache != null) {
          scores = state.cache.computeIfAbsent(contexts, c -> {
            // eval() writes into state.probs; cache values must be immutable copies for reuse.
            double[] res = model.eval(c, state.probs);
            double[] copy = new double[res.length];
            System.arraycopy(res, 0, copy, 0, res.length);
            return copy;
          });
        } else {
          scores = model.eval(contexts, state.probs);
        }

        // tempScores is a per-thread scratch buffer of length numOutcomes; we sort a copy here so
        // we never mutate `scores` (which may be a cached entry or alias state.probs).
        final double[] tempScores = state.tempScores;
        System.arraycopy(scores, 0, tempScores, 0, scores.length);

        Arrays.sort(tempScores);

        final double min = tempScores[StrictMath.max(0, scores.length - size)];

        for (int p = 0; p < scores.length; p++) {
          if (scores[p] >= min) {
            final String out = model.getOutcome(p);
            if (validator.validSequence(i, sequence, outcomes, out)) {
              final SearchNode ns = new SearchNode(top, out, scores[p]);
              if (ns.score > minSequenceScore) {
                next.add(ns);
              }
            }
          }
        }

        if (next.isEmpty()) { // if no advanced sequences, advance all valid
          for (int p = 0; p < scores.length; p++) {
            final String out = model.getOutcome(p);
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

    final int numSeq = StrictMath.min(numSequences, prev.size());
    final Sequence[] topSequences = new Sequence[numSeq];

    for (int seqIndex = 0; seqIndex < numSeq; seqIndex++) {
      final SearchNode winner = prev.remove();
      final String[] outs = new String[winner.size];
      final double[] probs = new double[winner.size];
      SearchNode node = winner;
      for (int j = winner.size - 1; j >= 0; j--) {
        outs[j] = node.outcome;
        probs[j] = node.prob;
        node = node.parent;
      }
      // Sequence.add accumulates score += StrictMath.log(p) per element, so rebuilding in
      // chain order yields a score bit-identical to the node's accumulated score.
      final Sequence seq = new Sequence();
      for (int j = 0; j < outs.length; j++) {
        seq.add(outs[j], probs[j]);
      }
      topSequences[seqIndex] = seq;
    }

    return topSequences;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Sequence[] bestSequences(final int numSequences, final T[] sequence,
      final Object[] additionalContext, final BeamSearchContextGenerator<T> cg,
      final SequenceValidator<T> validator) {
    return bestSequences(numSequences, sequence, additionalContext, ZERO_LOG, cg, validator);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Sequence bestSequence(final T[] sequence, final Object[] additionalContext,
      final BeamSearchContextGenerator<T> cg, final SequenceValidator<T> validator) {
    final Sequence[] sequences = bestSequences(1, sequence, additionalContext, cg, validator);

    if (sequences.length > 0) {
      return sequences[0];
    } else {
      return null;
    }
  }

  @Override
  public String[] getOutcomes() {
    String[] outcomes = new String[model.getNumOutcomes()];

    for (int i = 0; i < model.getNumOutcomes(); i++) {
      outcomes[i] = model.getOutcome(i);
    }
    return outcomes;
  }

  /**
   * Clears {@link ThreadLocal} state for the <b>current</b> thread only. This is intentionally not a
   * "shut down the {@code BeamSearch} instance" operation: a single {@code BeamSearch} is typically
   * shared across many pool threads, and each one owns an independent {@link CacheState} entry.
   *
   * <p>Typical usage patterns:</p>
   * <ul>
   *   <li><b>Worker thread returning to a pool:</b> call {@code close()} (or wrap a single decode call in
   *       try-with-resources) on each pool thread that has touched the instance.</li>
   *   <li><b>Application shutdown / classloader unload:</b> {@code close()} on a single thread is
   *       <i>not</i> sufficient to release every per-thread slot — those die with their owning threads, or
   *       must be cleared on each thread before the application classloader is released.</li>
   * </ul>
   *
   * <p>Same lifecycle contract as {@code clearThreadLocalState()} on the seven ME classes.</p>
   */
  @Override
  public void close() {
    threadState.remove();
  }
}
