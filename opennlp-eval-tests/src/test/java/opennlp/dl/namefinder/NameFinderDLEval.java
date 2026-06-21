/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl.namefinder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.dl.InferenceOptions;
import opennlp.tools.eval.AbstractEvalTest;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.util.Span;

public class NameFinderDLEval extends AbstractEvalTest {

  private static final Logger logger = LoggerFactory.getLogger(NameFinderDLEval.class);
  private final SentenceDetector sentenceDetector;

  public NameFinderDLEval() throws IOException {
    this.sentenceDetector = new SentenceDetectorME("en");
  }

  @Test
  public void tokenNameFinder1Test() throws Exception {

    // This test was written using the dslim/bert-base-NER model.
    // You will need to update the ids2Labels and assertions if you use a different model.

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final String[] tokens = new String[]
        {"George", "Washington", "was", "president", "of", "the", "United", "States", "."};

    try (final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, getIds2Labels(),
        sentenceDetector)) {
      final Span[] spans = nameFinderDL.find(tokens);

      for (Span span : spans) {
        logger.debug(span.toString());
      }

      final String text = String.join(" ", tokens);

      // The model emits a PER and a LOC entity; the person-only decoder previously dropped
      // the location. Span types are the entity labels (PER/LOC), not the matched text.
      Assertions.assertEquals(2, spans.length);

      Assertions.assertEquals("PER", spans[0].getType());
      Assertions.assertEquals(0, spans[0].getStart());
      Assertions.assertEquals(17, spans[0].getEnd());
      Assertions.assertEquals("George Washington", spans[0].getCoveredText(text));
      Assertions.assertTrue(spans[0].getProb() > 0 && spans[0].getProb() <= 1);

      Assertions.assertEquals("LOC", spans[1].getType());
      Assertions.assertEquals(39, spans[1].getStart());
      Assertions.assertEquals(52, spans[1].getEnd());
      Assertions.assertEquals("United States", spans[1].getCoveredText(text));
      Assertions.assertTrue(spans[1].getProb() > 0 && spans[1].getProb() <= 1);
    }

  }

  /**
   * Verifies that a single {@link NameFinderDL} instance is safe to share across threads:
   * many threads call {@link NameFinderDL#find(String[])} concurrently on one instance and
   * every call must return the same correct result as the single-threaded case. A data
   * race on the shared inference state would surface here as a wrong span, an exception or
   * a non-deterministic result.
   */
  @Test
  public void tokenNameFinderConcurrentTest() throws Exception {

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final String[] tokens = new String[]
        {"George", "Washington", "was", "president", "of", "the", "United", "States", "."};
    final String text = String.join(" ", tokens);

    final int threads = 8;
    final int iterationsPerThread = 25;

    try (final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, getIds2Labels(),
        sentenceDetector)) {

      final ExecutorService executor = Executors.newFixedThreadPool(threads);
      try {
        final CountDownLatch startGate = new CountDownLatch(1);
        final List<Future<Boolean>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
          futures.add(executor.submit(() -> {
            // Release all threads at once to maximize contention on the shared instance.
            startGate.await();
            for (int i = 0; i < iterationsPerThread; i++) {
              final Span[] spans = nameFinderDL.find(tokens);
              // The all-entity decoder yields both the PER and the LOC span for this input.
              if (spans.length != 2
                  || spans[0].getStart() != 0
                  || spans[0].getEnd() != 17
                  || !"PER".equals(spans[0].getType())
                  || !"George Washington".equals(spans[0].getCoveredText(text))
                  || spans[1].getStart() != 39
                  || spans[1].getEnd() != 52
                  || !"LOC".equals(spans[1].getType())
                  || !"United States".equals(spans[1].getCoveredText(text))) {
                return false;
              }
            }
            return true;
          }));
        }

        startGate.countDown();
        for (Future<Boolean> future : futures) {
          Assertions.assertTrue(future.get(),
              "a concurrent find() returned a result inconsistent with the single-threaded case");
        }
      } finally {
        // Shut down on every path so a failed assertion can never leave the pool running.
        executor.shutdownNow();
      }
    }

  }

  /**
   * Concurrent test that explicitly pairs {@link NameFinderDL} with {@link SentenceDetectorME}
   * to validate the documented {@code @ThreadSafe} precondition: {@code NameFinderDL} may be
   * shared across threads only when the injected {@link SentenceDetector} is itself thread-safe.
   * {@code SentenceDetectorME} is annotated {@code @ThreadSafe}, satisfying the contract.
   */
  @Test
  public void nameFinderDlConcurrentWithSentenceDetectorMe() throws Exception {

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final String[] tokens = new String[]
        {"George", "Washington", "was", "president", "of", "the", "United", "States", "."};
    final String text = String.join(" ", tokens);

    // Explicitly construct the detector inside the test to make the precondition visible.
    final SentenceDetectorME detector = new SentenceDetectorME("en");

    final int threads = 8;
    final int iterationsPerThread = 25;

    try (final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, getIds2Labels(),
        detector)) {

      final ExecutorService executor = Executors.newFixedThreadPool(threads);
      try {
        final CountDownLatch startGate = new CountDownLatch(1);
        final List<Future<Boolean>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
          futures.add(executor.submit(() -> {
            startGate.await();
            for (int i = 0; i < iterationsPerThread; i++) {
              final Span[] spans = nameFinderDL.find(tokens);
              // The all-entity decoder yields both the PER and the LOC span for this input.
              if (spans.length != 2
                  || spans[0].getStart() != 0
                  || spans[0].getEnd() != 17
                  || !"PER".equals(spans[0].getType())
                  || !"George Washington".equals(spans[0].getCoveredText(text))
                  || spans[1].getStart() != 39
                  || spans[1].getEnd() != 52
                  || !"LOC".equals(spans[1].getType())
                  || !"United States".equals(spans[1].getCoveredText(text))) {
                return false;
              }
            }
            return true;
          }));
        }

        startGate.countDown();
        for (Future<Boolean> future : futures) {
          Assertions.assertTrue(future.get(),
              "concurrent find() with SentenceDetectorME returned inconsistent results");
        }
      } finally {
        executor.shutdownNow();
      }
    }
  }

  /**
   * Verifies that {@link InferenceOptions} are snapshotted at construction: mutating the
   * options object after the {@link NameFinderDL} is built must not change its inference,
   * which is what makes a shared instance safe against callers that hold the same options.
   */
  @Test
  public void tokenNameFinderSnapshotsInferenceOptionsTest() throws Exception {

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final String[] tokens = new String[]
        {"George", "Washington", "was", "president", "of", "the", "United", "States", "."};
    final String text = String.join(" ", tokens);

    final InferenceOptions options = new InferenceOptions();

    try (final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, getIds2Labels(),
        options, sentenceDetector)) {

      final Span[] baseline = nameFinderDL.find(tokens);
      Assertions.assertEquals(2, baseline.length);
      Assertions.assertEquals("George Washington", baseline[0].getCoveredText(text));
      Assertions.assertEquals("United States", baseline[1].getCoveredText(text));

      // Mutate the options in ways that would change inference if they were read live:
      // a split size of 1 would chunk the input one word at a time.
      options.setIncludeAttentionMask(!options.isIncludeAttentionMask());
      options.setIncludeTokenTypeIds(!options.isIncludeTokenTypeIds());
      options.setDocumentSplitSize(1);
      options.setSplitOverlapSize(0);

      final Span[] afterMutation = nameFinderDL.find(tokens);
      Assertions.assertEquals(2, afterMutation.length,
          "mutating InferenceOptions after construction must not affect a built instance");
      Assertions.assertEquals(0, afterMutation[0].getStart());
      Assertions.assertEquals(17, afterMutation[0].getEnd());
      Assertions.assertEquals("George Washington", afterMutation[0].getCoveredText(text));
      Assertions.assertEquals(39, afterMutation[1].getStart());
      Assertions.assertEquals(52, afterMutation[1].getEnd());
      Assertions.assertEquals("United States", afterMutation[1].getCoveredText(text));
    }

  }

  @Test
  public void tokenNameFinder2Test() throws Exception {

    // This test was written using the dslim/bert-base-NER model.
    // You will need to update the ids2Labels and assertions if you use a different model.

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final String[] tokens = new String[] {"His", "name", "was", "George", "Washington"};

    try (final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, getIds2Labels(),
        sentenceDetector)) {
      final Span[] spans = nameFinderDL.find(tokens);

      for (Span span : spans) {
        logger.debug(span.toString());
      }

      Assertions.assertEquals(1, spans.length);
      Assertions.assertEquals("PER", spans[0].getType());
      Assertions.assertEquals(13, spans[0].getStart());
      Assertions.assertEquals(30, spans[0].getEnd());
      Assertions.assertEquals("George Washington",
          spans[0].getCoveredText(String.join(" ", tokens)));
    }
  }

  @Test
  public void tokenNameFinder3Test() throws Exception {

    // This test was written using the dslim/bert-base-NER model.
    // You will need to update the ids2Labels and assertions if you use a different model.

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final String[] tokens = new String[] {"His", "name", "was", "George"};

    try (final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, getIds2Labels(),
        sentenceDetector)) {
      final Span[] spans = nameFinderDL.find(tokens);

      for (Span span : spans) {
        logger.debug(span.toString());
      }

      Assertions.assertEquals(1, spans.length);
      Assertions.assertEquals("PER", spans[0].getType());
      Assertions.assertEquals(13, spans[0].getStart());
      Assertions.assertEquals(19, spans[0].getEnd());
      Assertions.assertEquals("George", spans[0].getCoveredText(String.join(" ", tokens)));
    }
  }

  @Test
  public void tokenNameFinderNoInputTest() throws Exception {

    // This test was written using the dslim/bert-base-NER model.
    // You will need to update the ids2Labels and assertions if you use a different model.

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final String[] tokens = new String[] {};

    try (final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, getIds2Labels(),
        sentenceDetector)) {
      final Span[] spans = nameFinderDL.find(tokens);

      Assertions.assertEquals(0, spans.length);
    }
  }

  @Test
  public void tokenNameFinderNoEntitiesTest() throws Exception {

    // This test was written using the dslim/bert-base-NER model.
    // You will need to update the ids2Labels and assertions if you use a different model.

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final String[] tokens = new String[] {"I", "went", "to", "the", "park"};

    try (final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, getIds2Labels(),
        sentenceDetector)) {
      final Span[] spans = nameFinderDL.find(tokens);

      Assertions.assertEquals(0, spans.length);
    }

  }

  @Test
  public void tokenNameFinderMultipleEntitiesTest() throws Exception {

    // This test was written using the dslim/bert-base-NER model.
    // You will need to update the ids2Labels and assertions if you use a different model.

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final String[] tokens = new String[] {"George", "Washington", "and", "Abraham", "Lincoln",
        "were", "presidents"};

    try (final NameFinderDL nameFinderDL = new NameFinderDL(model, vocab, getIds2Labels(),
        sentenceDetector)) {
      final Span[] spans = nameFinderDL.find(tokens);

      for (Span span : spans) {
        logger.debug(span.toString());
      }

      final String text = String.join(" ", tokens);

      Assertions.assertEquals(2, spans.length);
      Assertions.assertEquals("PER", spans[0].getType());
      Assertions.assertEquals(0, spans[0].getStart());
      Assertions.assertEquals(17, spans[0].getEnd());
      Assertions.assertEquals("George Washington", spans[0].getCoveredText(text));
      Assertions.assertEquals("PER", spans[1].getType());
      Assertions.assertEquals(22, spans[1].getStart());
      Assertions.assertEquals(37, spans[1].getEnd());
      Assertions.assertEquals("Abraham Lincoln", spans[1].getCoveredText(text));

    }

  }

  @Test
  public void invalidModel() {

    Assertions.assertThrows(OrtException.class, () -> {
      // This test was written using the dslim/bert-base-NER model.
      // You will need to update the ids2Labels and assertions if you use a different model.

      final File model = new File("invalid.onnx");
      final File vocab = new File("vocab.txt");
      //noinspection resource
      new NameFinderDL(model, vocab, getIds2Labels(), sentenceDetector);
    });

  }

  /**
   * End-to-end offset safety: with dash normalization enabled and a non-BMP dash before an entity,
   * the fold shrinks the text by one UTF-16 unit, so the entity sits at a smaller offset in the
   * normalized text than in the original. {@link NameFinderDL#findInOriginal(String[])} must report
   * the entity at its true offset in the original input, not the one-unit-shorter normalized offset.
   */
  @Test
  public void findInOriginalMapsSpansAcrossNonBmpDash() throws Exception {

    final File model = new File(getOpennlpDataDir(), "onnx/namefinder/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/namefinder/vocab.txt");

    final InferenceOptions options = new InferenceOptions();
    options.setNormalizeDashes(true);

    // Yezidi hyphen (U+10EAD): a Unicode Dash code point in the supplementary plane (two UTF-16
    // units) that folds to a single ASCII hyphen, shifting every following character left by one.
    final String yezidi = new String(Character.toChars(0x10EAD));
    final String[] tokens = {yezidi, "George", "Washington", "was", "president",
        "of", "the", "United", "States", "."};
    final String original = String.join(" ", tokens);

    try (final NameFinderDL nameFinderDL =
             new NameFinderDL(model, vocab, getIds2Labels(), options, sentenceDetector)) {

      final Span[] spans = nameFinderDL.findInOriginal(tokens);

      Span person = null;
      for (final Span span : spans) {
        if ("PER".equals(span.getType())) {
          person = span;
        }
      }
      Assertions.assertNotNull(person, "the PER entity should still be detected after the dash");
      // Mapped back through the alignment, the span covers the entity in the ORIGINAL input (which
      // still contains the two-unit dash); without the mapping it would be shifted left by one.
      Assertions.assertEquals("George Washington", person.getCoveredText(original));
      Assertions.assertEquals(original.indexOf("George Washington"), person.getStart());
    }

  }

  private Map<Integer, String> getIds2Labels() {

    final Map<Integer, String> ids2Labels = new HashMap<>();
    ids2Labels.put(0, "O");
    ids2Labels.put(1, "B-MISC");
    ids2Labels.put(2, "I-MISC");
    ids2Labels.put(3, "B-PER");
    ids2Labels.put(4, "I-PER");
    ids2Labels.put(5, "B-ORG");
    ids2Labels.put(6, "I-ORG");
    ids2Labels.put(7, "B-LOC");
    ids2Labels.put(8, "I-LOC");

    return ids2Labels;

  }

}
