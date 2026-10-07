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

package opennlp.tools.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.stemmer.PorterStemmerFactory;
import opennlp.tools.stemmer.SharingStemmer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer.ALGORITHM;
import opennlp.tools.util.normalizer.Dimension;
import opennlp.tools.util.normalizer.NormalizationProfiles;
import opennlp.tools.util.normalizer.Term;
import opennlp.tools.util.normalizer.TermAnalyzer;

/**
 * Stems a multilingual word list from many threads on shared stemmer and analyzer instances and
 * compares every result with a single-threaded reference. This is the long-running stress
 * complement to the concurrency unit tests in {@code StemmerFactoryTest} and
 * {@code NormalizationProfilesTest}; it needs no evaluation data.
 *
 * @see ThreadSafe
 * @see MultiThreadedToolsEval
 */
public class MultiThreadedStemmerEval extends AbstractEvalTest {

  private static final int NUM_THREADS = 8;
  private static final int RUNS_PER_THREAD = 500;
  private static final int ANALYZER_RUNS_PER_THREAD = 50;
  private static final int TIMEOUT_SECONDS = 120;

  /**
   * Words across scripts and languages, including apostrophes, diacritics, Greek, Cyrillic and
   * Arabic. Stemming is deterministic for any input, so every algorithm is run over the whole
   * list; a mismatch signals cross-thread corruption of the stemmer's internal buffers.
   */
  private static final List<String> WORDS = List.of(
      "running", "accompanying", "malediction", "softeners", "declining",
      "importantíssimes", "besar", "accidentalment",
      "abbattimento", "aborrecimentos", "importantísimas",
      "esiintymispaikasta", "aabenbaringen", "skrøbeligheder", "aftonringningen",
      "buchbindergesellen", "mitverursacht", "prévoyant", "examinateurs",
      "ab'yle", "kaçmamaktadır", "sarayı'nı",
      "abbahagynám", "konstrukciójából", "vliegtuigtransport",
      "absurdităţilor", "saracilor", "peledakan", "bhfeidhm",
      "επιστροφή", "στρατιωτών", "красивая", "яблоками",
      "استفتياكما", "استنتاجاتهما");

  /**
   * Stems the word list concurrently with one shared {@link SnowballStemmer} per algorithm.
   *
   * @throws Exception Thrown if a thread fails or times out.
   */
  @Test
  void runSharedSnowballStemmersMultiThreaded() throws Exception {
    for (ALGORITHM algorithm : ALGORITHM.values()) {
      List<String> expected = referenceStems(new SnowballStemmer(algorithm));
      hammer(new SnowballStemmer(algorithm), expected, "Snowball " + algorithm);
    }
  }

  /**
   * Stems the word list concurrently with one shared {@link SharingStemmer} over Porter.
   *
   * @throws Exception Thrown if a thread fails or times out.
   */
  @Test
  void runSharedPorterStemmerMultiThreaded() throws Exception {
    SharingStemmer shared = new SharingStemmer(new PorterStemmerFactory());
    List<String> expected = referenceStems(new PorterStemmerFactory().newStemmer());
    hammer(shared, expected, "SharingStemmer(Porter)");
  }

  /**
   * Analyzes the word list concurrently with the shared matching analyzer of every supported
   * language.
   *
   * @throws Exception Thrown if a thread fails or times out.
   */
  @Test
  void runMatchingAnalyzersMultiThreaded() throws Exception {
    for (String language : NormalizationProfiles.supportedLanguages()) {
      TermAnalyzer analyzer =
          NormalizationProfiles.forLanguage(language).orElseThrow().matchingAnalyzer();

      List<String> expected = new ArrayList<>(WORDS.size());
      for (String word : WORDS) {
        expected.add(stemDimension(analyzer, word));
      }

      runInThreads(threadIndex -> {
        List<Integer> order = shuffledIndexes(threadIndex);
        for (int run = 0; run < ANALYZER_RUNS_PER_THREAD; run++) {
          for (int i : order) {
            Assertions.assertEquals(expected.get(i), stemDimension(analyzer, WORDS.get(i)),
                () -> "matchingAnalyzer(" + language + ") corrupted under concurrency");
          }
        }
      });
    }
  }

  /**
   * Analyzes a word and joins the {@link Dimension#STEM} values of its terms.
   *
   * @param analyzer The analyzer.
   * @param word The input.
   * @return The stems, each followed by a space, or an empty string if there are no terms.
   */
  private static String stemDimension(TermAnalyzer analyzer, String word) {
    List<Term> terms = analyzer.analyze(word);
    if (terms.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Term term : terms) {
      sb.append(term.at(Dimension.STEM)).append(' ');
    }
    return sb.toString();
  }

  /**
   * Stems the word list on the calling thread.
   *
   * @param reference The stemmer.
   * @return The stems in word list order.
   */
  private static List<String> referenceStems(Stemmer reference) {
    List<String> expected = new ArrayList<>(WORDS.size());
    for (String word : WORDS) {
      expected.add(reference.stem(word).toString());
    }
    return expected;
  }

  /**
   * Stems the word list concurrently with one shared stemmer and compares each stem with the
   * reference.
   *
   * @param shared The stemmer shared by all threads.
   * @param expected The reference stems in word list order.
   * @param label The stemmer name for failure messages.
   * @throws Exception Thrown if a thread fails or times out.
   */
  private static void hammer(Stemmer shared, List<String> expected, String label) throws Exception {
    runInThreads(threadIndex -> {
      List<Integer> order = shuffledIndexes(threadIndex);
      for (int run = 0; run < RUNS_PER_THREAD; run++) {
        for (int i : order) {
          Assertions.assertEquals(expected.get(i), shared.stem(WORDS.get(i)).toString(),
              () -> label + " corrupted under concurrency for input '" + WORDS.get(i) + "'");
        }
      }
    });
  }

  /**
   * Returns the word list indexes in a seeded random order, so that concurrent threads work on
   * different words at the same time.
   *
   * @param seed The random seed, one per thread.
   * @return The shuffled indexes.
   */
  private static List<Integer> shuffledIndexes(int seed) {
    List<Integer> order = new ArrayList<>(WORDS.size());
    for (int i = 0; i < WORDS.size(); i++) {
      order.add(i);
    }
    Collections.shuffle(order, new Random(seed));
    return order;
  }

  /** The work of one thread. */
  private interface ThreadBody {
    void run(int threadIndex) throws Exception;
  }

  /**
   * Runs the body on {@link #NUM_THREADS} threads, each with its own thread index.
   *
   * @param body The work of one thread.
   * @throws Exception Thrown if a thread fails or the threads do not finish in time.
   */
  private static void runInThreads(ThreadBody body) throws Exception {
    final List<Callable<Void>> tasks = new ArrayList<>(NUM_THREADS);
    for (int t = 0; t < NUM_THREADS; t++) {
      final int threadIndex = t;
      tasks.add(() -> {
        body.run(threadIndex);
        return null;
      });
    }
    runConcurrently(tasks, NUM_THREADS, TIMEOUT_SECONDS);
  }
}
