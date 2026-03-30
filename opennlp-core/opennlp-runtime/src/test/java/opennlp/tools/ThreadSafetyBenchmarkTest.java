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

package opennlp.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkSampleStream;
import opennlp.tools.chunker.ChunkerFactory;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.langdetect.LanguageDetectorFactory;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.langdetect.LanguageDetectorSampleStream;
import opennlp.tools.lemmatizer.LemmaSample;
import opennlp.tools.lemmatizer.LemmaSampleStream;
import opennlp.tools.lemmatizer.LemmatizerFactory;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.namefind.BioCodec;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.sentdetect.SentenceDetectorFactory;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.sentdetect.SentenceSampleStream;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenSampleStream;
import opennlp.tools.tokenize.TokenizerFactory;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

/**
 * Thread-safety correctness tests for ME classes.
 * <p>
 * Each test shares a single ME instance across multiple threads
 * that are barrier-synchronized to maximize contention. Every
 * concurrent result is compared against a single-threaded baseline.
 * <p>
 * Uses {@link CyclicBarrier} to ensure all threads hit the critical
 * section simultaneously, increasing the probability of surfacing
 * races.
 */
public class ThreadSafetyBenchmarkTest {

  private static final int THREADS =
      Math.max(8, Runtime.getRuntime().availableProcessors());
  private static final int REPS = 200;

  private static TokenizerModel tokModel;
  private static SentenceModel sentModel;
  private static POSModel posModel;
  private static LemmatizerModel lemModel;
  private static ChunkerModel chunkModel;
  private static TokenNameFinderModel nfModel;
  private static LanguageDetectorModel ldModel;

  private static final String[] SENTENCES = {
      "The driver got badly injured.",
      "She told me that he lived in Edinburgh.",
      "I wrote him a letter right away.",
      "The quick brown fox jumps over the lazy dog.",
      "OpenNLP provides tools for NLP."
  };

  private static final String[] POS_TOKENS = {
      "The driver got badly injured .",
      "She told me that he lived in Edinburgh .",
      "The quick brown fox jumps over the lazy dog ."
  };

  private static final String[] LEM_TOKENS =
      {"Rockwell", "said", "the", "agreement",
       "calls", "for", "it", "to", "supply"};
  private static final String[] LEM_TAGS =
      {"NNP", "VBD", "DT", "NN",
       "VBZ", "IN", "PRP", "TO", "VB"};

  private static final String[] CHUNK_TOKS =
      {"Rockwell", "International", "Corp.", "'s",
       "Tulsa", "unit", "said", "it", "signed",
       "a", "tentative", "agreement", "."};
  private static final String[] CHUNK_TAGS =
      {"NNP", "NNP", "NNP", "POS",
       "NNP", "NN", "VBD", "PRP", "VBD",
       "DT", "JJ", "NN", "."};

  private static final String[] NF_SENTENCE =
      {"Alisa", "appreciated", "the", "hint",
       "and", "enjoyed", "a", "delicious",
       "traditional", "meal", "."};

  private static final String LD_ENG =
      "The quick brown fox jumps over the lazy dog";
  private static final String LD_FRA =
      "Le renard brun rapide saute par-dessus";

  private static final String LONG_TEXT =
      String.join(" ", SENTENCES).repeat(5);

  @BeforeAll
  static void trainModels() throws Exception {
    tokModel = trainTokenizer();
    sentModel = trainSentenceDetector();
    posModel = trainPOSTagger();
    lemModel = trainLemmatizer();
    chunkModel = trainChunker();
    nfModel = trainNameFinder();
    ldModel = trainLangDetect();
  }

  @Test
  void sharedTokenizerProducesCorrectResults()
      throws Exception {
    TokenizerME baseline = new TokenizerME(tokModel);
    String[][] expected = new String[SENTENCES.length][];
    for (int i = 0; i < SENTENCES.length; i++) {
      expected[i] = baseline.tokenize(SENTENCES[i]);
    }

    TokenizerME shared = new TokenizerME(tokModel);
    assertConcurrentCorrectness(shared, expected,
        (me, exp, mis) -> {
          for (int i = 0; i < SENTENCES.length; i++) {
            String[] res =
                ((TokenizerME) me).tokenize(SENTENCES[i]);
            if (!Arrays.equals(res, exp[i])) {
              mis.incrementAndGet();
            }
          }
        });
  }

  @Test
  void sharedSentenceDetectorProducesCorrectResults()
      throws Exception {
    SentenceDetectorME baseline =
        new SentenceDetectorME(sentModel);
    Span[][] expected = new Span[][] {
        baseline.sentPosDetect(LONG_TEXT)
    };

    SentenceDetectorME shared =
        new SentenceDetectorME(sentModel);
    assertConcurrentCorrectness(shared, expected,
        (me, exp, mis) -> {
          Span[] res =
              ((SentenceDetectorME) me)
                  .sentPosDetect(LONG_TEXT);
          if (!Arrays.equals(res, exp[0])) {
            mis.incrementAndGet();
          }
        });
  }

  @Test
  void sharedPOSTaggerProducesCorrectResults()
      throws Exception {
    POSTaggerME baseline = new POSTaggerME(posModel);
    String[][] expected =
        new String[POS_TOKENS.length][];
    for (int i = 0; i < POS_TOKENS.length; i++) {
      expected[i] =
          baseline.tag(POS_TOKENS[i].split(" "));
    }

    POSTaggerME shared = new POSTaggerME(posModel);
    assertConcurrentCorrectness(shared, expected,
        (me, exp, mis) -> {
          for (int i = 0; i < POS_TOKENS.length; i++) {
            String[] tags = ((POSTaggerME) me)
                .tag(POS_TOKENS[i].split(" "));
            if (!Arrays.equals(tags, exp[i])) {
              mis.incrementAndGet();
            }
          }
        });
  }

  @Test
  void sharedLemmatizerProducesCorrectResults()
      throws Exception {
    LemmatizerME baseline = new LemmatizerME(lemModel);
    String[][] expected = new String[][] {
        baseline.lemmatize(LEM_TOKENS, LEM_TAGS)
    };

    LemmatizerME shared = new LemmatizerME(lemModel);
    assertConcurrentCorrectness(shared, expected,
        (me, exp, mis) -> {
          String[] res = ((LemmatizerME) me)
              .lemmatize(LEM_TOKENS, LEM_TAGS);
          if (!Arrays.equals(res, exp[0])) {
            mis.incrementAndGet();
          }
        });
  }

  @Test
  void probsDoesNotThrowUnderConcurrency()
      throws Exception {
    POSTaggerME shared = new POSTaggerME(posModel);
    AtomicInteger errors = new AtomicInteger();
    CyclicBarrier barrier = new CyclicBarrier(THREADS);

    try (ExecutorService ex =
             Executors.newFixedThreadPool(THREADS)) {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < THREADS; t++) {
        futures.add(ex.submit(() -> {
          try {
            barrier.await();
          } catch (Exception e) {
            Thread.currentThread().interrupt();
            return;
          }
          for (int r = 0; r < REPS; r++) {
            try {
              shared.tag(
                  POS_TOKENS[0].split(" "));
              double[] p = shared.probs();
              if (p == null || p.length == 0) {
                errors.incrementAndGet();
              }
            } catch (Exception e) {
              errors.incrementAndGet();
            }
          }
        }));
      }
      for (Future<?> f : futures) {
        f.get();
      }
    }

    Assertions.assertEquals(0, errors.get(),
        "probs() threw or returned invalid data "
            + "under concurrent access");
  }

  @Test
  void sharedChunkerProducesCorrectResults()
      throws Exception {
    ChunkerME baseline = new ChunkerME(chunkModel);
    String[][] expected = new String[][] {
        baseline.chunk(CHUNK_TOKS, CHUNK_TAGS)
    };

    ChunkerME shared = new ChunkerME(chunkModel);
    assertConcurrentCorrectness(shared, expected,
        (me, exp, mis) -> {
          String[] res = ((ChunkerME) me)
              .chunk(CHUNK_TOKS, CHUNK_TAGS);
          if (!Arrays.equals(res, exp[0])) {
            mis.incrementAndGet();
          }
        });
  }

  @Test
  void sharedNameFinderProducesCorrectResults()
      throws Exception {
    NameFinderME baseline = new NameFinderME(nfModel);
    Span[][] expected = new Span[][] {
        baseline.find(NF_SENTENCE)
    };

    NameFinderME shared = new NameFinderME(nfModel);
    assertConcurrentCorrectness(shared, expected,
        (me, exp, mis) -> {
          Span[] res = ((NameFinderME) me)
              .find(NF_SENTENCE);
          if (!Arrays.equals(res, exp[0])) {
            mis.incrementAndGet();
          }
        });
  }

  @Test
  void sharedLangDetectorProducesCorrectResults()
      throws Exception {
    LanguageDetectorME baseline =
        new LanguageDetectorME(ldModel);
    String[][] expected = new String[][] {
        {baseline.predictLanguage(LD_ENG).getLang(),
         baseline.predictLanguage(LD_FRA).getLang()}
    };

    LanguageDetectorME shared =
        new LanguageDetectorME(ldModel);
    assertConcurrentCorrectness(shared, expected,
        (me, exp, mis) -> {
          LanguageDetectorME ld =
              (LanguageDetectorME) me;
          String eng = ld.predictLanguage(LD_ENG)
              .getLang();
          String fra = ld.predictLanguage(LD_FRA)
              .getLang();
          if (!eng.equals(exp[0][0])
              || !fra.equals(exp[0][1])) {
            mis.incrementAndGet();
          }
        });
  }

  // ========== BARRIER-SYNCHRONIZED HARNESS ==========

  @FunctionalInterface
  private interface ConcurrentTask {
    void run(Object me, Object[][] expected,
             AtomicInteger mismatches);
  }

  private <T> void assertConcurrentCorrectness(
      T sharedInstance, Object[][] expected,
      ConcurrentTask task) throws Exception {

    CyclicBarrier barrier = new CyclicBarrier(THREADS);
    AtomicInteger mismatches = new AtomicInteger();

    try (ExecutorService ex =
             Executors.newFixedThreadPool(THREADS)) {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < THREADS; t++) {
        futures.add(ex.submit(() -> {
          try {
            barrier.await();
          } catch (Exception e) {
            Thread.currentThread().interrupt();
            return;
          }
          for (int r = 0; r < REPS; r++) {
            task.run(sharedInstance, expected,
                mismatches);
          }
        }));
      }
      for (Future<?> f : futures) {
        f.get();
      }
    }

    Assertions.assertEquals(0, mismatches.get(),
        "Shared instance produced " + mismatches.get()
            + " mismatches vs single-threaded baseline"
            + " (" + THREADS + " threads, "
            + REPS + " reps each)");
  }

  // ========== MODEL TRAINING ==========

  private static TokenizerModel trainTokenizer()
      throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        TokenizerModel.class,
        "/opennlp/tools/tokenize/token.train");
    ObjectStream<TokenSample> samples =
        new TokenSampleStream(new PlainTextByLineStream(
            in, StandardCharsets.UTF_8));
    TrainingParameters p = new TrainingParameters();
    p.put(Parameters.ITERATIONS_PARAM, 100);
    p.put(Parameters.CUTOFF_PARAM, 0);
    return TokenizerME.train(samples,
        TokenizerFactory.create(
            null, "eng", null, true, null), p);
  }

  private static SentenceModel trainSentenceDetector()
      throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        ThreadSafetyBenchmarkTest.class,
        "/opennlp/tools/sentdetect/Sentences.txt");
    ObjectStream<SentenceSample> samples =
        new SentenceSampleStream(new PlainTextByLineStream(
            in, StandardCharsets.UTF_8));
    return SentenceDetectorME.train("eng", samples,
        new SentenceDetectorFactory(
            "eng", true, null, null),
        TrainingParameters.defaultParams());
  }

  private static POSModel trainPOSTagger()
      throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        POSTaggerME.class,
        "/opennlp/tools/postag/AnnotatedSentences.txt");
    ObjectStream<POSSample> samples =
        new WordTagSampleStream(new PlainTextByLineStream(
            in, StandardCharsets.UTF_8));
    TrainingParameters p = new TrainingParameters();
    p.put(Parameters.ALGORITHM_PARAM,
        ModelType.MAXENT.toString());
    p.put(Parameters.ITERATIONS_PARAM, 100);
    p.put(Parameters.CUTOFF_PARAM, 5);
    return POSTaggerME.train(
        "eng", samples, p, new POSTaggerFactory());
  }

  private static LemmatizerModel trainLemmatizer()
      throws IOException {
    ObjectStream<LemmaSample> samples =
        new LemmaSampleStream(new PlainTextByLineStream(
            new MockInputStreamFactory(new File(
                "opennlp/tools/lemmatizer/trial.old.tsv")),
            StandardCharsets.UTF_8));
    TrainingParameters p = new TrainingParameters();
    p.put(Parameters.ITERATIONS_PARAM, 100);
    p.put(Parameters.CUTOFF_PARAM, 5);
    return LemmatizerME.train(
        "eng", samples, p, new LemmatizerFactory());
  }

  private static ChunkerModel trainChunker()
      throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        ThreadSafetyBenchmarkTest.class,
        "/opennlp/tools/chunker/test.txt");
    ObjectStream<ChunkSample> samples =
        new ChunkSampleStream(new PlainTextByLineStream(
            in, StandardCharsets.UTF_8));
    TrainingParameters p = new TrainingParameters();
    p.put(Parameters.ITERATIONS_PARAM, 70);
    p.put(Parameters.CUTOFF_PARAM, 1);
    return ChunkerME.train(
        "eng", samples, p, new ChunkerFactory());
  }

  private static TokenNameFinderModel trainNameFinder()
      throws IOException {
    ObjectStream<NameSample> samples =
        new NameSampleDataStream(
            new PlainTextByLineStream(
                new MockInputStreamFactory(new File(
                    "opennlp/tools/namefind/"
                        + "AnnotatedSentences.txt")),
                "ISO-8859-1"));
    TrainingParameters p = new TrainingParameters();
    p.put(Parameters.ITERATIONS_PARAM, 70);
    p.put(Parameters.CUTOFF_PARAM, 1);
    return NameFinderME.train("eng", null, samples, p,
        TokenNameFinderFactory.create(
            null, null, Collections.emptyMap(),
            new BioCodec()));
  }

  private static LanguageDetectorModel trainLangDetect()
      throws Exception {
    InputStreamFactory in = new ResourceAsStreamFactory(
        ThreadSafetyBenchmarkTest.class,
        "/opennlp/tools/doccat/DoccatSample.txt");
    LanguageDetectorSampleStream samples =
        new LanguageDetectorSampleStream(
            new PlainTextByLineStream(
                in, StandardCharsets.UTF_8));
    TrainingParameters p = new TrainingParameters();
    p.put(Parameters.ITERATIONS_PARAM, 100);
    p.put(Parameters.CUTOFF_PARAM, 5);
    p.put("DataIndexer", "TwoPass");
    p.put(Parameters.ALGORITHM_PARAM, "NAIVEBAYES");
    return LanguageDetectorME.train(
        samples, p, new LanguageDetectorFactory());
  }
}
