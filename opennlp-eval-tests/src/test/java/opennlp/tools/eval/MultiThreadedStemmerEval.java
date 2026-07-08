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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
 * Tests that the thread-safe stemmer implementations really are thread-safe by hammering shared
 * instances from many threads and comparing every result against a single-threaded reference.
 *
 * @see ThreadSafe
 * @see MultiThreadedToolsEval
 */
public class MultiThreadedStemmerEval extends AbstractEvalTest {

  private static final int NUM_THREADS = 8;
  private static final int RUNS_PER_THREAD = 500;

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

  @Test
  public void runSharedSnowballStemmersMultiThreaded() throws Exception {
    for (ALGORITHM algorithm : ALGORITHM.values()) {
      List<String> expected = referenceStems(new SnowballStemmer(algorithm));
      hammer(new SnowballStemmer(algorithm), expected, "Snowball " + algorithm);
    }
  }

  @Test
  public void runSharedPorterStemmerMultiThreaded() throws Exception {
    SharingStemmer shared = new SharingStemmer(new PorterStemmerFactory());
    List<String> expected = referenceStems(new PorterStemmerFactory().newStemmer());
    hammer(shared, expected, "SharingStemmer(Porter)");
  }

  @Test
  public void runMatchingAnalyzersMultiThreaded() throws Exception {
    for (String language : NormalizationProfiles.supportedLanguages()) {
      TermAnalyzer analyzer =
          NormalizationProfiles.forLanguage(language).orElseThrow().matchingAnalyzer();

      List<String> expected = new ArrayList<>(WORDS.size());
      for (String word : WORDS) {
        expected.add(stemDimension(analyzer, word));
      }

      runInThreads(threadIndex -> {
        List<Integer> order = shuffledIndexes(threadIndex);
        for (int run = 0; run < RUNS_PER_THREAD / 10; run++) {
          for (int i : order) {
            Assertions.assertEquals(expected.get(i), stemDimension(analyzer, WORDS.get(i)),
                () -> "matchingAnalyzer(" + language + ") corrupted under concurrency");
          }
        }
      });
    }
  }

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

  private static List<String> referenceStems(Stemmer reference) {
    List<String> expected = new ArrayList<>(WORDS.size());
    for (String word : WORDS) {
      expected.add(reference.stem(word).toString());
    }
    return expected;
  }

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
   * Each thread visits the word list in its own order so that different threads are (almost)
   * always inside different words at the same time, maximizing the chance of exposing shared
   * mutable state.
   */
  private static List<Integer> shuffledIndexes(int seed) {
    List<Integer> order = new ArrayList<>(WORDS.size());
    for (int i = 0; i < WORDS.size(); i++) {
      order.add(i);
    }
    Collections.shuffle(order, new Random(seed));
    return order;
  }

  private interface ThreadBody {
    void run(int threadIndex) throws Exception;
  }

  private static void runInThreads(ThreadBody body) throws Exception {
    try (ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS)) {
      List<Future<?>> tasks = new ArrayList<>(NUM_THREADS);
      for (int t = 0; t < NUM_THREADS; t++) {
        final int threadIndex = t;
        tasks.add(pool.submit(() -> {
          body.run(threadIndex);
          return null;
        }));
      }
      for (Future<?> task : tasks) {
        task.get(120, TimeUnit.SECONDS);
      }
    }
  }
}
