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

package opennlp.spellcheck.eval;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.spellcheck.SuggestItem;
import opennlp.spellcheck.Verbosity;
import opennlp.spellcheck.dictionary.FrequencyDictionaryLoader;
import opennlp.spellcheck.distance.DamerauOSADistance;
import opennlp.spellcheck.distance.EditDistance;
import opennlp.spellcheck.distance.LevenshteinDistance;
import opennlp.spellcheck.symspell.SymSpell;
import opennlp.spellcheck.symspell.SymSpellConfig;
import opennlp.tools.eval.AbstractEvalTest;
import opennlp.tools.util.InputStreamFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benchmark / evaluation of the SymSpell-backed {@link SymSpell spell checker} against the
 * upstream SymSpell English frequency dictionaries and the 1000-query noisy benchmark.
 *
 * <p>Data is read from {@code $OPENNLP_DATA_DIR/spellcheck/en/} (bundled in the
 * {@code opennlp-data} nightly benchmark archive under {@code spellcheck/en/}):</p>
 * <pre>
 *   spellcheck/en/frequency_dictionary_en_82_765.txt
 *   spellcheck/en/frequency_bigramdictionary_en_243_342.txt
 *   spellcheck/en/noisy_query_en_1000.txt
 * </pre>
 *
 * <p>The test <b>skips gracefully</b> (it is not a failure) when {@code OPENNLP_DATA_DIR}
 * is unset or the files are absent, mirroring the other {@code *Eval} classes, so a plain
 * build without the data does not break. It runs under the {@code eval-tests} Maven
 * profile (class name ends in {@code Eval}).</p>
 *
 * <h2>What is measured, and how it relates to the SymSpell project</h2>
 *
 * <p>In the upstream SymSpell repository {@code noisy_query_en_1000.txt} drives a pure
 * <i>speed</i> benchmark (only the misspelled token is used; lookups are timed). SymSpell
 * itself publishes no single-word accuracy figure, because its candidate set within a
 * given edit distance is identical to Norvig's &mdash; the contribution is speed, not a
 * different accuracy. This class therefore measures <b>throughput</b> (the comparable
 * SymSpell metric) as the primary hard gate, and additionally repurposes the file's
 * {@code <typo> <correct> <editdistance>} columns as a correction-quality probe.</p>
 *
 * <p>Correction quality is reported as a full <b>confusion matrix</b> rather than a single
 * accuracy scalar, because top-1 accuracy alone hides the precision/recall trade-off. For
 * each query the engine's top-1 suggestion is the predicted output ({@code null}/empty
 * suggestion means "leave unchanged"):</p>
 * <ul>
 *   <li><b>error present</b> ({@code typo != correct}) &rarr; <b>TP</b> output == correct,
 *       <b>FN</b> output unchanged (error missed), <b>miscorrect</b> changed to a wrong
 *       word;</li>
 *   <li><b>no error</b> ({@code typo == correct}) &rarr; <b>TN</b> left unchanged,
 *       <b>FP</b> over-corrected a clean word.</li>
 * </ul>
 *
 * <p>Because the benchmark is built so a fixed fraction of queries carry an edit distance
 * greater than the configured {@code maxEditDistance}, accuracy is bounded by that budget:
 * raising the edit distance increases recall (more distant typos become reachable) but can
 * lower precision (a larger candidate set yields more wrong corrections). The
 * {@link #accuracyImprovesWithEditDistance()} test exercises that relationship explicitly.</p>
 */
public class SpellCheckerEval extends AbstractEvalTest {

  private static final Logger logger = LoggerFactory.getLogger(SpellCheckerEval.class);

  private static final String BASE = "spellcheck/en";
  private static final String UNIGRAMS = BASE + "/frequency_dictionary_en_82_765.txt";
  private static final String BIGRAMS = BASE + "/frequency_bigramdictionary_en_243_342.txt";
  private static final String QUERIES = BASE + "/noisy_query_en_1000.txt";

  /**
   * Optional expected MD5 checksums. When a property is set the corresponding dataset
   * checksum is asserted; when it is unset the actual checksum is logged instead, so the
   * test still passes on first contact with a freshly downloaded (but unrecorded) dataset.
   */
  private static final String PROP_DICT_MD5 = "opennlp.spellcheck.eval.dict.md5";
  private static final String PROP_BIGRAM_MD5 = "opennlp.spellcheck.eval.bigram.md5";
  private static final String PROP_QUERY_MD5 = "opennlp.spellcheck.eval.query.md5";

  /** SymSpell reference benchmark defaults. */
  private static final int MAX_EDIT_DISTANCE = 2;
  private static final int PREFIX_LENGTH = 7;

  /**
   * Minimum overall accuracy {@code (TP + TN) / total} for the default Damerau-OSA metric
   * at {@link #MAX_EDIT_DISTANCE}. Calibrated against the measured value (~0.57 on the
   * SymSpell English data) with margin, so it is a regression guard, not a tight target.
   */
  private static final double MIN_ACCURACY = 0.50d;

  /**
   * Minimum precision of corrections {@code TP / (changes)}: of all words the checker
   * decided to change, at least this fraction must be changed to the intended word.
   * Measured ~0.47; floored conservatively.
   */
  private static final double MIN_PRECISION = 0.40d;

  /**
   * Maximum tolerated over-correction rate {@code FP / clean}: clean words (already
   * correct) that the checker wrongly changed. Measured ~0.04; capped well above.
   */
  private static final double MAX_OVERCORRECTION_RATE = 0.10d;

  /**
   * Minimum mean token accuracy for bigram-backed {@code lookupCompound} over the canonical
   * SymSpell demonstration sentences. Measured 1.0 (all four corrected exactly); floored with
   * margin so it stays a regression guard.
   */
  private static final double MIN_COMPOUND_TOKEN_ACCURACY = 0.90d;

  /**
   * Minimum throughput floor in lookups/second (single-threaded, warm dictionary). Even
   * very modest CI hardware sustains thousands of SymSpell lookups per second; this floor
   * only catches catastrophic regressions.
   */
  private static final double MIN_LOOKUPS_PER_SEC = 1_000d;

  /** A single noisy-query benchmark row: a misspelling and its expected correction. */
  private record Query(String typo, String expected) {
  }

  /**
   * Confusion matrix for one (engine, maxEditDistance) configuration over the benchmark.
   *
   * @param tp         error present and corrected to the expected word
   * @param fn         error present but left unchanged (missed)
   * @param miscorrect error present and changed, but to the wrong word
   * @param tn         no error and correctly left unchanged
   * @param fp         no error but wrongly changed (over-correction)
   */
  private record Confusion(int tp, int fn, int miscorrect, int tn, int fp) {
    int errors() {
      return tp + fn + miscorrect;
    }

    int clean() {
      return tn + fp;
    }

    int total() {
      return errors() + clean();
    }

    int changes() {
      return tp + miscorrect + fp;
    }

    /** Overall accuracy: fraction of queries whose final output equals the intended word. */
    double accuracy() {
      return total() == 0 ? 0d : (tp + tn) / (double) total();
    }

    /** Recall over error-bearing queries: fraction of real errors that were fixed. */
    double recall() {
      return errors() == 0 ? 0d : tp / (double) errors();
    }

    /** Precision of the correction action: of all changes made, fraction that were right. */
    double precision() {
      return changes() == 0 ? 0d : tp / (double) changes();
    }

    double f1() {
      final double p = precision();
      final double r = recall();
      return (p + r == 0d) ? 0d : 2d * p * r / (p + r);
    }

    /** Over-correction rate: clean words wrongly changed. */
    double overCorrectionRate() {
      return clean() == 0 ? 0d : fp / (double) clean();
    }

    String format(String label) {
      return String.format(Locale.ROOT,
          "%-22s acc=%.4f P=%.4f R=%.4f F1=%.4f | TP=%d FN=%d miscorrect=%d | TN=%d FP=%d "
              + "(overcorrect=%.4f, errors=%d clean=%d)",
          label, accuracy(), precision(), recall(), f1(), tp, fn, miscorrect, tn, fp,
          overCorrectionRate(), errors(), clean());
    }
  }

  private static File dataFile(String relative) throws FileNotFoundException {
    return new File(getOpennlpDataDir(), relative);
  }

  /** True only when OPENNLP_DATA_DIR is set and all three datasets are present. */
  private static boolean dataAvailable() {
    try {
      return dataFile(UNIGRAMS).isFile()
          && dataFile(BIGRAMS).isFile()
          && dataFile(QUERIES).isFile();
    } catch (RuntimeException | FileNotFoundException e) {
      return false;
    }
  }

  @BeforeAll
  static void requireData() {
    final boolean available = dataAvailable();
    if (!available) {
      logger.info("Skipping SpellCheckerEval: OPENNLP_DATA_DIR/{} datasets are not present.", BASE);
    }
    Assumptions.assumeTrue(available,
        "OPENNLP_DATA_DIR/" + BASE + " benchmark datasets are not available");
  }

  // ------------------------------------------------------------------
  // Helpers.
  // ------------------------------------------------------------------

  private static InputStreamFactory factory(File file) {
    return () -> Files.newInputStream(file.toPath());
  }

  private static SymSpell buildEngine(EditDistance metric, int maxEditDistance) throws IOException {
    return buildEngine(metric, maxEditDistance, true);
  }

  private static SymSpell buildEngine(EditDistance metric, int maxEditDistance, boolean withBigrams)
      throws IOException {
    final SymSpell engine = new SymSpell(SymSpellConfig.builder()
        .maxDictionaryEditDistance(maxEditDistance)
        .prefixLength(PREFIX_LENGTH)
        .editDistance(metric)
        .build());
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    loader.loadUnigrams(engine, factory(dataFile(UNIGRAMS)));
    if (withBigrams) {
      loader.loadBigrams(engine, factory(dataFile(BIGRAMS)));
    }
    return engine;
  }

  /**
   * The canonical SymSpell {@code LookupCompound} demonstration sentences (input &rarr;
   * expected), reproduced from the SymSpell README. Each fixes ~9 errors spanning the three
   * compound cases: wrongly split words, wrongly merged words, and per-word misspellings.
   */
  private static final String[][] COMPOUND_CASES = {
    {"whereis th elove hehad dated forimuch of thepast who couqdn'tread in sixthgrade and ins pired him",
     "where is the love he had dated for much of the past who couldn't read in sixth grade and inspired him"},
    {"in te dhird qarter oflast jear he hadlearned ofca sekretplan",
     "in the third quarter of last year he had learned of a secret plan"},
    {"the bigjest playrs in the strogsommer film slatew ith plety of funn",
     "the biggest players in the strong summer film slate with plenty of fun"},
    {"can yu readthis messa ge despite thehorible sppelingmsitakes",
     "can you read this message despite the horrible spelling mistakes"},
  };

  /** Position-wise token accuracy of {@code output} against {@code expected}. */
  private static double tokenAccuracy(String expected, String output) {
    final String[] exp = expected.trim().toLowerCase(Locale.ROOT).split("\\s+");
    final String[] out = output.trim().toLowerCase(Locale.ROOT).split("\\s+");
    final int n = Math.min(exp.length, out.length);
    int match = 0;
    for (int k = 0; k < n; k++) {
      if (exp[k].equals(out[k])) {
        match++;
      }
    }
    return exp.length == 0 ? 0d : match / (double) exp.length;
  }

  /** Mean token accuracy + exact-match count of lookupCompound over the canonical cases. */
  private static double[] compoundScore(SymSpell engine, boolean log) {
    double sum = 0d;
    int exact = 0;
    for (String[] c : COMPOUND_CASES) {
      final List<SuggestItem> r = engine.lookupCompound(c[0], MAX_EDIT_DISTANCE);
      final String out = r.isEmpty() ? "" : r.get(0).term();
      final double ta = tokenAccuracy(c[1], out);
      sum += ta;
      if (out.equals(c[1])) {
        exact++;
      }
      if (log) {
        report(String.format(Locale.ROOT, "compound tokenAcc=%.3f exact=%b%n    out: %s%n    exp: %s",
            ta, out.equals(c[1]), out, c[1]));
      }
    }
    return new double[] {sum / COMPOUND_CASES.length, exact};
  }

  /**
   * Loads the noisy benchmark. The SymSpell {@code noisy_query_en_1000.txt} file holds one
   * whitespace-separated {@code <typo> <expected> <editdistance>} row per (non-comment)
   * line; only the first two columns are used here. Lines that do not yield at least two
   * tokens are ignored.
   */
  private static List<Query> loadQueries() throws IOException {
    final List<Query> queries = new ArrayList<>();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(
        Files.newInputStream(dataFile(QUERIES).toPath()), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        final String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
          continue;
        }
        final String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
          continue;
        }
        queries.add(new Query(parts[0].toLowerCase(Locale.ROOT),
            parts[1].toLowerCase(Locale.ROOT)));
      }
    }
    return queries;
  }

  /** Scores the engine over the benchmark into a confusion matrix. */
  private static Confusion evaluate(SymSpell engine, List<Query> queries, int maxEditDistance) {
    int tp = 0;
    int fn = 0;
    int miscorrect = 0;
    int tn = 0;
    int fp = 0;
    for (Query q : queries) {
      final List<SuggestItem> suggestions = engine.lookup(q.typo(), Verbosity.TOP, maxEditDistance);
      final String output = suggestions.isEmpty() ? q.typo() : suggestions.get(0).term();
      final boolean errorPresent = !q.typo().equals(q.expected());
      final boolean changed = !output.equals(q.typo());
      if (errorPresent) {
        if (output.equals(q.expected())) {
          tp++;
        } else if (!changed) {
          fn++;
        } else {
          miscorrect++;
        }
      } else if (changed) {
        fp++;
      } else {
        tn++;
      }
    }
    return new Confusion(tp, fn, miscorrect, tn, fp);
  }

  private static void verifyOrLogChecksum(File file, String propertyName) throws Exception {
    final String expected = System.getProperty(propertyName);
    if (expected != null && !expected.isBlank()) {
      verifyFileChecksum(file.toPath(), new BigInteger(expected, 16));
      logger.info("Checksum OK for {}", file.getName());
    } else {
      logger.info("No expected checksum configured ({}); actual MD5 of {} = {}",
          propertyName, file.getName(), md5Hex(file));
    }
  }

  private static String md5Hex(File file) throws Exception {
    final java.security.MessageDigest digest = java.security.MessageDigest.getInstance(HASH_ALGORITHM);
    try (var in = new java.io.BufferedInputStream(Files.newInputStream(file.toPath()))) {
      final byte[] buf = new byte[65536];
      int len;
      while ((len = in.read(buf)) > 0) {
        digest.update(buf, 0, len);
      }
    }
    return String.format("%032x", new BigInteger(1, digest.digest()));
  }

  /** Benchmark output goes to stdout so it is always captured in the failsafe report. */
  private static void report(String line) {
    System.out.println("[SpellCheckerEval] " + line);
  }

  // ------------------------------------------------------------------
  // Tests.
  // ------------------------------------------------------------------

  @Test
  void validatesDatasetChecksums() throws Exception {
    verifyOrLogChecksum(dataFile(UNIGRAMS), PROP_DICT_MD5);
    verifyOrLogChecksum(dataFile(BIGRAMS), PROP_BIGRAM_MD5);
    verifyOrLogChecksum(dataFile(QUERIES), PROP_QUERY_MD5);
  }

  @Test
  void meetsCorrectionQualityFloors() throws IOException {
    final List<Query> queries = loadQueries();
    assertTrue(queries.size() > 100, "expected a non-trivial benchmark, got " + queries.size());

    final SymSpell engine = buildEngine(DamerauOSADistance.INSTANCE, MAX_EDIT_DISTANCE);
    final Confusion m = evaluate(engine, queries, MAX_EDIT_DISTANCE);
    report(m.format("DamerauOSA maxED=" + MAX_EDIT_DISTANCE));

    assertTrue(m.accuracy() >= MIN_ACCURACY,
        "accuracy " + m.accuracy() + " fell below the floor " + MIN_ACCURACY);
    assertTrue(m.precision() >= MIN_PRECISION,
        "precision " + m.precision() + " fell below the floor " + MIN_PRECISION);
    assertTrue(m.overCorrectionRate() <= MAX_OVERCORRECTION_RATE,
        "over-correction rate " + m.overCorrectionRate()
            + " exceeded the cap " + MAX_OVERCORRECTION_RATE);
  }

  @Test
  void accuracyImprovesWithEditDistance() throws IOException {
    final List<Query> queries = loadQueries();

    Confusion previous = null;
    for (int maxEd = 1; maxEd <= 3; maxEd++) {
      final Confusion m = evaluate(buildEngine(DamerauOSADistance.INSTANCE, maxEd), queries, maxEd);
      report(m.format("DamerauOSA maxED=" + maxEd));
      if (previous != null) {
        // A larger edit budget only adds farther candidates; closer candidates always win
        // for Verbosity.TOP, so recall (real errors fixed) is monotonically non-decreasing.
        assertTrue(m.recall() >= previous.recall() - ACCURACY_DELTA,
            "recall should not decrease as maxEditDistance grows: "
                + previous.recall() + " -> " + m.recall());
      }
      previous = m;
    }
  }

  @Test
  void comparesDamerauOsaAgainstLevenshtein() throws IOException {
    final List<Query> queries = loadQueries();

    final Confusion damerau =
        evaluate(buildEngine(DamerauOSADistance.INSTANCE, MAX_EDIT_DISTANCE), queries, MAX_EDIT_DISTANCE);
    final Confusion levenshtein =
        evaluate(buildEngine(LevenshteinDistance.INSTANCE, MAX_EDIT_DISTANCE), queries, MAX_EDIT_DISTANCE);
    report(damerau.format("DamerauOSA  maxED=" + MAX_EDIT_DISTANCE));
    report(levenshtein.format("Levenshtein maxED=" + MAX_EDIT_DISTANCE));
    report(String.format(Locale.ROOT, "accuracy delta (Damerau - Levenshtein) = %.4f",
        damerau.accuracy() - levenshtein.accuracy()));

    // On this benchmark the two metrics are near-identical (transpositions are rare); assert
    // only that they stay within a small band rather than a strict ordering.
    assertTrue(Math.abs(damerau.accuracy() - levenshtein.accuracy()) <= 0.02d,
        "Damerau-OSA and Levenshtein accuracy should be within 0.02; got "
            + damerau.accuracy() + " vs " + levenshtein.accuracy());
    assertTrue(damerau.accuracy() >= MIN_ACCURACY && levenshtein.accuracy() >= MIN_ACCURACY,
        "both metrics should clear the accuracy floor; D=" + damerau.accuracy()
            + " L=" + levenshtein.accuracy());
  }

  @Test
  void compoundCorrectionWithBigrams() throws IOException {
    final SymSpell engine = buildEngine(DamerauOSADistance.INSTANCE, MAX_EDIT_DISTANCE, true);
    final double[] score = compoundScore(engine, true);
    report(String.format(Locale.ROOT, "compound (with bigrams): mean tokenAcc=%.3f exact=%d/%d",
        score[0], (int) score[1], COMPOUND_CASES.length));

    assertTrue(score[0] >= MIN_COMPOUND_TOKEN_ACCURACY,
        "compound token accuracy " + score[0] + " fell below the floor "
            + MIN_COMPOUND_TOKEN_ACCURACY);
  }

  @Test
  void bigramDictionaryImprovesCompound() throws IOException {
    final double[] withBigrams =
        compoundScore(buildEngine(DamerauOSADistance.INSTANCE, MAX_EDIT_DISTANCE, true), false);
    final double[] unigramOnly =
        compoundScore(buildEngine(DamerauOSADistance.INSTANCE, MAX_EDIT_DISTANCE, false), false);
    report(String.format(Locale.ROOT,
        "compound mean tokenAcc: with-bigram=%.3f unigram-only=%.3f (delta=%.3f)",
        withBigrams[0], unigramOnly[0], withBigrams[0] - unigramOnly[0]));

    // The bigram (sentence-context) dictionary must not hurt and is expected to help on the
    // first sentence, where word-choice between equal-distance candidates needs context.
    assertTrue(withBigrams[0] >= unigramOnly[0] - ACCURACY_DELTA,
        "bigram-backed compound (" + withBigrams[0] + ") should be >= unigram-only ("
            + unigramOnly[0] + ")");
  }

  @Test
  void meetsThroughputFloor() throws IOException {
    final List<Query> queries = loadQueries();
    final SymSpell engine = buildEngine(DamerauOSADistance.INSTANCE, MAX_EDIT_DISTANCE);

    // Warm-up to amortise JIT / first-touch costs.
    for (Query q : queries) {
      engine.lookup(q.typo(), Verbosity.TOP, MAX_EDIT_DISTANCE);
    }

    final int repeats = 5;
    final long start = System.nanoTime();
    long lookups = 0;
    for (int i = 0; i < repeats; i++) {
      for (Query q : queries) {
        engine.lookup(q.typo(), Verbosity.TOP, MAX_EDIT_DISTANCE);
        lookups++;
      }
    }
    final double seconds = (System.nanoTime() - start) / 1_000_000_000.0d;
    final double perSec = lookups / seconds;

    report(String.format(Locale.ROOT, "throughput: %d lookups in %.3f s = %.0f lookups/s",
        lookups, seconds, perSec));
    assertTrue(perSec >= MIN_LOOKUPS_PER_SEC,
        "throughput " + perSec + " lookups/s fell below the floor " + MIN_LOOKUPS_PER_SEC);
  }
}
