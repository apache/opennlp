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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.BeamSearchContextGenerator;
import opennlp.tools.util.Cache;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;

/**
 * Equivalence tests for the {@code BeamSearch.bestSequences} refactor that replaced
 * per-candidate {@link Sequence} copies with internal chain nodes ({@code SearchNode}).
 * <p>
 * Every test runs the current {@link BeamSearch} side by side with
 * {@link #referenceBestSequences}, a faithful port of the pre-refactor implementation
 * (as of {@code HEAD~1}), and demands identical output: same number of sequences,
 * identical outcome lists (order-sensitive), and bit-identical scores and
 * per-position probabilities.
 */
public class BeamSearchEquivalenceTest {

  /** Mirror of the private {@code BeamSearch.ZERO_LOG} default threshold. */
  private static final double ZERO_LOG = -100000;

  private static final int NUM_OUTCOMES = 4;
  private static final long MODEL_SEED = 0x5eedL;

  private static final int[] BEAM_SIZES = {1, 2, 3, 5, 10}; // 10 > NUM_OUTCOMES on purpose
  private static final int[] INPUT_LENGTHS = {0, 1, 2, 7, 33, 128};
  private static final int[] CACHE_SIZES = {0, 64};

  private static final SequenceValidator<String> ACCEPT_ALL =
      (i, input, outcomes, outcome) -> true;

  // ---------------------------------------------------------------------------
  // Seeded pseudo-random model
  // ---------------------------------------------------------------------------

  /**
   * A {@link MaxentModel} whose probabilities are a deterministic pseudo-random
   * function of the joined context strings and the outcome index. Repeated evals of
   * the same context therefore return identical values. Values lie in (0, 1] and are
   * intentionally not normalized. The {@code eval(context, probs)} buffer contract is
   * honored: values are written into the passed array and that same array is returned.
   */
  static final class SeededModel implements MaxentModel {

    private final String[] outcomes;
    private final long seed;

    SeededModel(int numOutcomes, long seed) {
      this.outcomes = new String[numOutcomes];
      for (int i = 0; i < numOutcomes; i++) {
        this.outcomes[i] = "o" + i;
      }
      this.seed = seed;
    }

    private double prob(String[] context, int outcomeIndex) {
      long h = seed;
      for (String c : context) {
        h = mix(h, c.hashCode());
      }
      h = mix(h, outcomeIndex);
      // splitmix64 finalizer for avalanche
      h ^= h >>> 30;
      h *= 0xBF58476D1CE4E5B9L;
      h ^= h >>> 27;
      h *= 0x94D049BB133111EBL;
      h ^= h >>> 31;
      double u = (h >>> 11) * (1.0 / (1L << 53)); // [0, 1)
      return 0.01 + 0.98 * u; // (0.01, 0.99]
    }

    private static long mix(long h, long v) {
      return (h ^ (v + 0x9E3779B97F4A7C15L)) * 0x100000001B3L;
    }

    @Override
    public double[] eval(String[] context) {
      return eval(context, new double[outcomes.length]);
    }

    @Override
    public double[] eval(String[] context, double[] probs) {
      for (int i = 0; i < outcomes.length; i++) {
        probs[i] = prob(context, i);
      }
      return probs; // buffer contract: write into the passed array AND return it
    }

    @Override
    public double[] eval(String[] context, float[] values) {
      return eval(context);
    }

    @Override
    public String getOutcome(int i) {
      return outcomes[i];
    }

    @Override
    public int getNumOutcomes() {
      return outcomes.length;
    }

    @Override
    public String getAllOutcomes(double[] outcomes) {
      return null;
    }

    @Override
    public String getBestOutcome(double[] outcomes) {
      return null;
    }

    @Override
    public int getIndex(String outcome) {
      for (int i = 0; i < outcomes.length; i++) {
        if (outcomes[i].equals(outcome)) {
          return i;
        }
      }
      return -1;
    }
  }

  // ---------------------------------------------------------------------------
  // Context generator
  // ---------------------------------------------------------------------------

  /**
   * Builds contexts from the index, the token, the previous outcome and the first
   * additional-context element. Identical context content is interned to the same
   * {@code String[]} instance so the identity-keyed contexts {@link Cache} in
   * {@link BeamSearch} actually produces hits. Thread-safe.
   */
  static final class SeededContextGenerator implements BeamSearchContextGenerator<String> {

    private final ConcurrentHashMap<String, String[]> intern = new ConcurrentHashMap<>();
    private final AtomicInteger callCount = new AtomicInteger();

    @Override
    public String[] getContext(int index, String[] sequence,
                               String[] priorDecisions, Object[] additionalContext) {
      callCount.incrementAndGet();
      String prev = index > 0 ? priorDecisions[index - 1] : "<s>";
      String ac = additionalContext != null && additionalContext.length > 0
          ? String.valueOf(additionalContext[0]) : "-";
      String[] ctx = {"ix=" + index, "tok=" + sequence[index], "prev=" + prev, "ac=" + ac};
      String key = String.join("", ctx);
      String[] existing = intern.putIfAbsent(key, ctx);
      return existing != null ? existing : ctx;
    }

    int callCount() {
      return callCount.get();
    }
  }

  // ---------------------------------------------------------------------------
  // Reference implementation: faithful port of the pre-refactor bestSequences
  // (git show HEAD~1:.../opennlp/tools/ml/BeamSearch.java)
  // ---------------------------------------------------------------------------

  /**
   * Port of the OLD {@code BeamSearch.bestSequences} control flow: PriorityQueue over
   * {@link Sequence}, per-candidate {@code new Sequence(top, out, scores[p])} copies,
   * tempScores sort/min, the {@code next.isEmpty()} advance-all-valid fallback, the
   * queue swap, and the winner removal order. The cache path (a
   * {@code Cache<String[], double[]>} exactly like the old per-thread one) is used
   * when {@code cacheSize > 0}; otherwise the uncached eval path is taken.
   */
  static <T> Sequence[] referenceBestSequences(
      final int numSequences, final T[] sequence, final Object[] additionalContext,
      final double minSequenceScore, final BeamSearchContextGenerator<T> cg,
      final SequenceValidator<T> validator, final MaxentModel model,
      final int beamSize, final int cacheSize) {

    // Local equivalents of the old per-thread CacheState.
    final double[] probs = new double[model.getNumOutcomes()];
    final double[] tempScores = new double[model.getNumOutcomes()];
    final Cache<String[], double[]> cache = cacheSize > 0 ? new Cache<>(cacheSize) : null;

    Queue<Sequence> prev = new PriorityQueue<>(beamSize);
    Queue<Sequence> next = new PriorityQueue<>(beamSize);
    Queue<Sequence> tmp;
    prev.add(new Sequence());

    Object[] context = additionalContext;
    if (context == null) {
      context = new Object[0];
    }

    for (int i = 0; i < sequence.length; i++) {
      final int sz = StrictMath.min(beamSize, prev.size());

      for (int sc = 0; prev.size() > 0 && sc < sz; sc++) {
        final Sequence top = prev.remove();
        final List<String> tmpOutcomes = top.getOutcomes();
        final String[] outcomes = tmpOutcomes.toArray(new String[0]);
        final String[] contexts = cg.getContext(i, sequence, outcomes, context);
        final double[] scores;
        if (cache != null) {
          scores = cache.computeIfAbsent(contexts, c -> {
            double[] res = model.eval(c, probs);
            double[] copy = new double[res.length];
            System.arraycopy(res, 0, copy, 0, res.length);
            return copy;
          });
        } else {
          scores = model.eval(contexts, probs);
        }

        System.arraycopy(scores, 0, tempScores, 0, scores.length);
        Arrays.sort(tempScores);

        final double min = tempScores[StrictMath.max(0, scores.length - beamSize)];

        for (int p = 0; p < scores.length; p++) {
          if (scores[p] >= min) {
            final String out = model.getOutcome(p);
            if (validator.validSequence(i, sequence, outcomes, out)) {
              final Sequence ns = new Sequence(top, out, scores[p]);
              if (ns.getScore() > minSequenceScore) {
                next.add(ns);
              }
            }
          }
        }

        if (next.isEmpty()) { // if no advanced sequences, advance all valid
          for (int p = 0; p < scores.length; p++) {
            final String out = model.getOutcome(p);
            if (validator.validSequence(i, sequence, outcomes, out)) {
              final Sequence ns = new Sequence(top, out, scores[p]);
              if (ns.getScore() > minSequenceScore) {
                next.add(ns);
              }
            }
          }
        }
      }

      // make prev = next; and re-init next (reuse existing prev set once cleared)
      prev.clear();
      tmp = prev;
      prev = next;
      next = tmp;
    }

    final int numSeq = StrictMath.min(numSequences, prev.size());
    final Sequence[] topSequences = new Sequence[numSeq];

    for (int seqIndex = 0; seqIndex < numSeq; seqIndex++) {
      topSequences[seqIndex] = prev.remove();
    }

    return topSequences;
  }

  /** Reference twin of the two-arg overload (default {@code ZERO_LOG} threshold). */
  static <T> Sequence[] referenceBestSequences(
      int numSequences, T[] sequence, Object[] additionalContext,
      BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator,
      MaxentModel model, int beamSize, int cacheSize) {
    return referenceBestSequences(numSequences, sequence, additionalContext, ZERO_LOG,
        cg, validator, model, beamSize, cacheSize);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static String[] randomInput(int length, long seed) {
    Random rnd = new Random(seed);
    String[] input = new String[length];
    for (int i = 0; i < length; i++) {
      input[i] = "t" + rnd.nextInt(17);
    }
    return input;
  }

  private static void assertBitIdentical(double expected, double actual, String what) {
    Assertions.assertEquals(Double.doubleToRawLongBits(expected),
        Double.doubleToRawLongBits(actual),
        what + " (expected=" + expected + ", actual=" + actual + ")");
  }

  private static void assertSequencesEqual(Sequence[] expected, Sequence[] actual,
                                           String caseDesc) {
    Assertions.assertNotNull(actual, caseDesc + ": result array must not be null");
    Assertions.assertEquals(expected.length, actual.length,
        caseDesc + ": number of returned sequences");
    for (int s = 0; s < expected.length; s++) {
      String seqDesc = caseDesc + ", sequence[" + s + "]";
      Assertions.assertEquals(expected[s].getOutcomes(), actual[s].getOutcomes(),
          seqDesc + ": outcomes");
      assertBitIdentical(expected[s].getScore(), actual[s].getScore(),
          seqDesc + ": score");
      Assertions.assertEquals(expected[s].getSize(), actual[s].getSize(),
          seqDesc + ": size");
      double[] expectedProbs = expected[s].getProbs();
      double[] actualProbs = actual[s].getProbs();
      Assertions.assertEquals(expectedProbs.length, actualProbs.length,
          seqDesc + ": probs length");
      for (int p = 0; p < expectedProbs.length; p++) {
        assertBitIdentical(expectedProbs[p], actualProbs[p],
            seqDesc + ": prob[" + p + "]");
        assertBitIdentical(expected[s].getProb(p), actual[s].getProb(p),
            seqDesc + ": getProb(" + p + ")");
      }
    }
  }

  private static String caseDesc(String test, int beam, int length, int cache) {
    return test + "[beam=" + beam + ", len=" + length + ", cache=" + cache + "]";
  }

  // ---------------------------------------------------------------------------
  // 1. Equivalence matrix: beam sizes x input lengths x cache sizes,
  //    default (ZERO_LOG) threshold via the two-arg overload, accept-all validator
  // ---------------------------------------------------------------------------

  @Test
  void equivalenceAcrossBeamSizesLengthsAndCaches() {
    MaxentModel model = new SeededModel(NUM_OUTCOMES, MODEL_SEED);
    for (int beam : BEAM_SIZES) {
      for (int length : INPUT_LENGTHS) {
        String[] input = randomInput(length, 1000L + length);
        for (int cache : CACHE_SIZES) {
          String desc = caseDesc("matrix", beam, length, cache);
          SeededContextGenerator cg = new SeededContextGenerator();

          Sequence[] expected = referenceBestSequences(1, input, null, cg, ACCEPT_ALL,
              model, beam, cache);
          Sequence[] actual = new BeamSearch(beam, model, cache)
              .bestSequences(1, input, null, cg, ACCEPT_ALL);

          assertSequencesEqual(expected, actual, desc);
          if (length == 0) {
            Assertions.assertEquals(0, cg.callCount(),
                desc + ": context generator must not be called for empty input");
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // 2. Equivalence with a tight minSequenceScore that actually filters candidates
  // ---------------------------------------------------------------------------

  @Test
  void equivalenceWithTightMinSequenceScore() {
    MaxentModel model = new SeededModel(NUM_OUTCOMES, MODEL_SEED);
    for (int beam : BEAM_SIZES) {
      for (int length : INPUT_LENGTHS) {
        String[] input = randomInput(length, 2000L + length);
        for (int cache : CACHE_SIZES) {
          String desc = caseDesc("threshold", beam, length, cache);

          // Derive a threshold that bites: run uncapped, then cut between the best
          // and worst candidate scores (or just above the best when only one exists).
          Sequence[] uncapped = referenceBestSequences(beam, input, null,
              new SeededContextGenerator(), ACCEPT_ALL, model, beam, cache);
          final double threshold;
          if (uncapped.length == 0) {
            threshold = 0;
          } else {
            double best = uncapped[0].getScore();
            double worst = uncapped[uncapped.length - 1].getScore();
            threshold = (uncapped.length > 1 && worst < best)
                ? (best + worst) / 2.0 : best + 0.5;
          }

          Sequence[] expected = referenceBestSequences(1, input, null, threshold,
              new SeededContextGenerator(), ACCEPT_ALL, model, beam, cache);
          Sequence[] actual = new BeamSearch(beam, model, cache)
              .bestSequences(1, input, null, threshold, new SeededContextGenerator(),
                  ACCEPT_ALL);

          assertSequencesEqual(expected, actual, desc + ", threshold=" + threshold);
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // 3. Equivalence under restrictive validators, including the next.isEmpty()
  //    advance-all-valid fallback and the reject-everything empty-result path
  // ---------------------------------------------------------------------------

  @Test
  void equivalenceWithRestrictiveValidators() {
    MaxentModel model = new SeededModel(NUM_OUTCOMES, MODEL_SEED);

    SequenceValidator<String> rejectOneOutcome =
        (i, input, outcomes, outcome) -> !"o2".equals(outcome);
    // Rejects every outcome at position 2: at that position the threshold loop adds
    // nothing, so the next.isEmpty() fallback runs (and also rejects everything,
    // killing the search for inputs longer than 2).
    SequenceValidator<String> rejectAllAtPosition2 =
        (i, input, outcomes, outcome) -> i != 2;
    // Rejects everything except "o3" at position 2: the fallback actually populates
    // next with the sub-threshold "o3" candidate whenever "o3" fell below the min.
    SequenceValidator<String> onlyO3AtPosition2 =
        (i, input, outcomes, outcome) -> i != 2 || "o3".equals(outcome);
    SequenceValidator<String> rejectEverything =
        (i, input, outcomes, outcome) -> false;

    record NamedValidator(String name, SequenceValidator<String> validator) {}
    List<NamedValidator> validators = List.of(
        new NamedValidator("rejectOneOutcome", rejectOneOutcome),
        new NamedValidator("rejectAllAtPosition2", rejectAllAtPosition2),
        new NamedValidator("onlyO3AtPosition2", onlyO3AtPosition2),
        new NamedValidator("rejectEverything", rejectEverything));

    for (NamedValidator nv : validators) {
      for (int beam : BEAM_SIZES) {
        for (int length : INPUT_LENGTHS) {
          String[] input = randomInput(length, 3000L + length);
          for (int cache : CACHE_SIZES) {
            String desc = caseDesc("validator-" + nv.name(), beam, length, cache);

            Sequence[] expected = referenceBestSequences(1, input, null,
                new SeededContextGenerator(), nv.validator(), model, beam, cache);
            Sequence[] actual = new BeamSearch(beam, model, cache)
                .bestSequences(1, input, null, new SeededContextGenerator(),
                    nv.validator());

            assertSequencesEqual(expected, actual, desc);
            if ("rejectEverything".equals(nv.name()) && length > 0) {
              Assertions.assertEquals(0, actual.length,
                  desc + ": reject-everything must yield an empty (non-null) array");
              Assertions.assertEquals(0, expected.length,
                  desc + ": reference reject-everything must also be empty");
            }
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // 4. numSequences > 1: winner order and scores match the reference exactly
  // ---------------------------------------------------------------------------

  @Test
  void multiWinnerOrderingMatchesReference() {
    MaxentModel model = new SeededModel(NUM_OUTCOMES, MODEL_SEED);
    int beam = 5;
    int numSequences = 3;
    Object[] additionalContext = {"ac-ctx"};
    for (int length : INPUT_LENGTHS) {
      String[] input = randomInput(length, 4000L + length);
      for (int cache : CACHE_SIZES) {
        String desc = caseDesc("multiWinner[k=3]", beam, length, cache);

        Sequence[] expected = referenceBestSequences(numSequences, input,
            additionalContext, new SeededContextGenerator(), ACCEPT_ALL,
            model, beam, cache);
        Sequence[] actual = new BeamSearch(beam, model, cache)
            .bestSequences(numSequences, input, additionalContext,
                new SeededContextGenerator(), ACCEPT_ALL);

        assertSequencesEqual(expected, actual, desc);
        if (length > 0) {
          Assertions.assertEquals(numSequences, actual.length,
              desc + ": expected a full k-best list");
          // Winners must come out in non-increasing score order.
          for (int s = 1; s < actual.length; s++) {
            Assertions.assertTrue(actual[s - 1].getScore() >= actual[s].getScore(),
                desc + ": winner order not non-increasing at index " + s);
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // 5. Concurrency determinism: one shared BeamSearch, 8 worker threads
  // ---------------------------------------------------------------------------

  @Test
  void concurrentResultsMatchSerialReference() throws Exception {
    final int numInputs = 64;
    final int rounds = 4; // 64 x 4 = 256 decode evaluations across the pool
    final int numThreads = 8;
    final int beam = 3;
    final int cache = 64;

    MaxentModel model = new SeededModel(NUM_OUTCOMES, MODEL_SEED);
    BeamSearch shared = new BeamSearch(beam, model, cache);

    String[][] inputs = new String[numInputs][];
    for (int n = 0; n < numInputs; n++) {
      inputs[n] = randomInput(n, 5000L + n); // lengths 0..63
    }
    SeededContextGenerator cg = new SeededContextGenerator();

    // Serial reference results, one per input.
    Sequence[][] reference = new Sequence[numInputs][];
    for (int n = 0; n < numInputs; n++) {
      reference[n] = referenceBestSequences(1, inputs[n], null, cg, ACCEPT_ALL,
          model, beam, cache);
    }

    // Run 1: 8 workers, each with a disjoint subset of inputs, `rounds` passes each.
    Sequence[][][] runResults = new Sequence[rounds][numInputs][];
    ExecutorService pool = Executors.newFixedThreadPool(numThreads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int w = 0; w < numThreads; w++) {
        final int worker = w;
        futures.add(pool.submit(() -> {
          for (int round = 0; round < rounds; round++) {
            for (int n = worker; n < numInputs; n += numThreads) {
              runResults[round][n] = shared.bestSequences(1, inputs[n], null, cg,
                  ACCEPT_ALL);
            }
          }
        }));
      }
      for (Future<?> f : futures) {
        f.get();
      }
    } finally {
      pool.shutdown();
    }

    for (int round = 0; round < rounds; round++) {
      for (int n = 0; n < numInputs; n++) {
        assertSequencesEqual(reference[n], runResults[round][n],
            "concurrent[round=" + round + ", input=" + n + ", len=" + inputs[n].length
                + "]");
      }
    }

    // Run 2: the SAME input set twice concurrently; the two passes must agree with
    // each other (and with the serial reference).
    Sequence[][] passA = new Sequence[numInputs][];
    Sequence[][] passB = new Sequence[numInputs][];
    ExecutorService pool2 = Executors.newFixedThreadPool(numThreads);
    try {
      Future<?> fa = pool2.submit(() -> {
        for (int n = 0; n < numInputs; n++) {
          passA[n] = shared.bestSequences(1, inputs[n], null, cg, ACCEPT_ALL);
        }
      });
      Future<?> fb = pool2.submit(() -> {
        for (int n = numInputs - 1; n >= 0; n--) { // reverse order, still racing passA
          passB[n] = shared.bestSequences(1, inputs[n], null, cg, ACCEPT_ALL);
        }
      });
      fa.get();
      fb.get();
    } finally {
      pool2.shutdown();
    }

    for (int n = 0; n < numInputs; n++) {
      assertSequencesEqual(passA[n], passB[n],
          "concurrent-agreement[input=" + n + "]");
      assertSequencesEqual(reference[n], passA[n],
          "concurrent-vs-reference[input=" + n + "]");
    }
  }

  // ---------------------------------------------------------------------------
  // 6. Winner materialization: outcomes, per-position probs and score of the
  //    winning Sequence are consistent with the model's eval outputs
  // ---------------------------------------------------------------------------

  @Test
  void winnerMaterializationMatchesModelOutputs() {
    MaxentModel model = new SeededModel(NUM_OUTCOMES, MODEL_SEED);
    String[] input = randomInput(7, 6000L);
    int beam = 3;

    for (int cache : CACHE_SIZES) {
      String desc = "materialization[cache=" + cache + "]";
      BeamSearch bs = new BeamSearch(beam, model, cache);
      Sequence winner = bs.bestSequence(input, null, new SeededContextGenerator(),
          ACCEPT_ALL);
      Assertions.assertNotNull(winner, desc);
      Assertions.assertEquals(input.length, winner.getSize(), desc + ": size");

      // The winner must equal the reference winner.
      Sequence refWinner = referenceBestSequences(1, input, null,
          new SeededContextGenerator(), ACCEPT_ALL, model, beam, cache)[0];
      Assertions.assertEquals(refWinner.getOutcomes(), winner.getOutcomes(),
          desc + ": outcomes vs reference");

      // Walk the winning path and recompute the expected probs/score independently.
      List<String> outcomes = winner.getOutcomes();
      double[] probs = winner.getProbs();
      double expectedScore = 0d;
      SeededContextGenerator cg = new SeededContextGenerator();
      for (int i = 0; i < outcomes.size(); i++) {
        String[] prefix = outcomes.subList(0, i).toArray(new String[0]);
        String[] contexts = cg.getContext(i, input, prefix, new Object[0]);
        double[] eval = model.eval(contexts);
        int outcomeIndex = model.getIndex(outcomes.get(i));
        Assertions.assertTrue(outcomeIndex >= 0, desc + ": outcome known to model");
        double expectedProb = eval[outcomeIndex];

        assertBitIdentical(expectedProb, probs[i], desc + ": getProbs()[" + i + "]");
        assertBitIdentical(expectedProb, winner.getProb(i),
            desc + ": getProb(" + i + ")");
        expectedScore += StrictMath.log(expectedProb);
      }
      assertBitIdentical(expectedScore, winner.getScore(), desc + ": score");
    }
  }
}
