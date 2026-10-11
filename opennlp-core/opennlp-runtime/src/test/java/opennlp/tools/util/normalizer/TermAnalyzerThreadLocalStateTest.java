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
package opennlp.tools.util.normalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.stemmer.CachingStemmer;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;
import opennlp.tools.stemmer.snowball.SnowballStemmer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TermAnalyzerThreadLocalStateTest {

  /** A factory that counts the delegates it mints and the words they stem. */
  private static final class CountingFactory implements StemmerFactory {

    private final AtomicInteger minted = new AtomicInteger();
    private final AtomicInteger stemmed = new AtomicInteger();

    @Override
    public Stemmer newStemmer() {
      minted.incrementAndGet();
      return word -> {
        stemmed.incrementAndGet();
        return word.toString() + "-s";
      };
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"dog", "cat", "running"})
  void testClearReleasesAWorkerThreadsDelegate(String word) throws Exception {
    final CountingFactory factory = new CountingFactory();
    final TermAnalyzer analyzer = TermAnalyzer.builder().stem(factory).build();
    // The first stemming thread owns a retained state; a pool thread holds per-thread state.
    analyzer.analyze(word);
    final ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      pool.submit(() -> analyzer.analyze(word)).get();
      final int beforeClear = factory.minted.get();
      pool.submit(() -> analyzer.analyze(word)).get();
      assertEquals(beforeClear, factory.minted.get(), "the worker reuses its delegate");

      pool.submit(analyzer::clearThreadLocalState).get();
      final List<Term> terms = pool.submit(() -> analyzer.analyze(word)).get();
      assertEquals(word + "-s", terms.get(0).normalized());
      assertEquals(beforeClear + 1, factory.minted.get(),
          "a fresh delegate is minted after the clear");
    } finally {
      pool.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"dog", "cat", "running"})
  void testClearEmptiesTheOwnerThreadsCache(String word) {
    final CountingFactory factory = new CountingFactory();
    final TermAnalyzer analyzer = TermAnalyzer.builder().stem(factory, 16).build();
    analyzer.analyze(word);
    analyzer.analyze(word);
    assertEquals(1, factory.stemmed.get(), "the second lookup is served from the cache");

    analyzer.clearThreadLocalState();
    assertEquals(word + "-s", analyzer.analyze(word).get(0).normalized());
    assertEquals(2, factory.stemmed.get(), "the clear emptied the cache");
  }

  @Test
  void testClearReleasesAPassedCachingStemmer() throws Exception {
    final CountingFactory factory = new CountingFactory();
    final TermAnalyzer analyzer =
        TermAnalyzer.builder().stem(new CachingStemmer(factory)).build();
    analyzer.analyze("dog");
    final ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      pool.submit(() -> analyzer.analyze("dog")).get();
      final int beforeClear = factory.minted.get();
      pool.submit(analyzer::clearThreadLocalState).get();
      pool.submit(() -> analyzer.analyze("dog")).get();
      assertEquals(beforeClear + 1, factory.minted.get());
    } finally {
      pool.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"Running", "Cats", "jumped"})
  void testClearIsANoOpWithoutACachingStemmer(String word) {
    final List<TermAnalyzer> analyzers = new ArrayList<>();
    analyzers.add(TermAnalyzer.builder().caseFold().build());
    analyzers.add(TermAnalyzer.builder().caseFold().stem(new PorterStemmer()).build());
    analyzers.add(TermAnalyzer.builder().caseFold()
        .stem(new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH)).build());
    for (final TermAnalyzer analyzer : analyzers) {
      final String before = analyzer.analyze(word).get(0).normalized();
      assertDoesNotThrow(analyzer::clearThreadLocalState);
      assertEquals(before, analyzer.analyze(word).get(0).normalized());
    }
  }
}
