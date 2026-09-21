/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerEvaluator;
import opennlp.tools.tokenize.TokenizerFactory;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.lang.en.TokenSampleStream;
import opennlp.tools.util.CollectionObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

/**
 * Evaluates {@link TokenSampleStream}, the English detokenizing reader of whitespace-separated
 * token lines, on the CoNLL-2000 chunking corpus under {@code conll00} of
 * {@code OPENNLP_DATA_DIR}: the token column of each sentence is joined into one line, read
 * through the stream, and the resulting samples are pinned by count, span consistency, and
 * digest. A tokenizer trained on the training samples is then evaluated on the test samples.
 */
public class EnglishTokenSampleStreamEval extends AbstractEvalTest {

  private static final BigInteger TRAIN_LINES_DIGEST =
      new BigInteger("301122588956914125372652203489993621272");
  private static final BigInteger TEST_LINES_DIGEST =
      new BigInteger("137949334592096501881782226355762620597");

  private static final int TRAIN_SENTENCES = 8936;
  private static final int TRAIN_TOKENS = 211727;
  private static final int TEST_SENTENCES = 2012;
  private static final int TEST_TOKENS = 47377;

  /** MD5 over the detokenized text and spans of every training sample, in order. */
  private static final BigInteger TRAIN_SAMPLES_DIGEST =
      new BigInteger("191748249203924286712667399185967843116");
  /** MD5 over the detokenized text and spans of every test sample, in order. */
  private static final BigInteger TEST_SAMPLES_DIGEST =
      new BigInteger("34587640684711740229972437775975202269");

  private static final String FIRST_TRAIN_TEXT = "Confidence in the pound is widely expected to "
      + "take another sharp dive if trade figures for September, due for release tomorrow, fail "
      + "to show a substantial improvement from July and August's near-record deficits.";

  private static final double EXPECTED_F_MEASURE = 0.9965d;

  private static List<String> trainLines;
  private static List<String> testLines;

  @BeforeAll
  static void verifyTrainingData() throws Exception {
    trainLines = tokenLines(new File(getOpennlpDataDir(), "conll00/train.txt"));
    testLines = tokenLines(new File(getOpennlpDataDir(), "conll00/test.txt"));
    verifyTrainingData(new CollectionObjectStream<>(trainLines), TRAIN_LINES_DIGEST);
    verifyTrainingData(new CollectionObjectStream<>(testLines), TEST_LINES_DIGEST);
  }

  @Test
  void testDetokenizedSamples(TestReporter reporter) throws Exception {
    final List<TokenSample> train = samples(trainLines);
    final List<TokenSample> test = samples(testLines);
    Assertions.assertEquals(TRAIN_SENTENCES, train.size());
    Assertions.assertEquals(TEST_SENTENCES, test.size());
    Assertions.assertEquals(TRAIN_TOKENS, countSpans(train));
    Assertions.assertEquals(TEST_TOKENS, countSpans(test));
    Assertions.assertEquals(FIRST_TRAIN_TEXT, train.get(0).getText());
    verifySpansCoverTokens(train, trainLines);
    verifySpansCoverTokens(test, testLines);
    final BigInteger trainDigest = digest(train);
    final BigInteger testDigest = digest(test);
    reporter.publishEntry("trainSamplesDigest", trainDigest.toString());
    reporter.publishEntry("testSamplesDigest", testDigest.toString());
    Assertions.assertEquals(TRAIN_SAMPLES_DIGEST, trainDigest);
    Assertions.assertEquals(TEST_SAMPLES_DIGEST, testDigest);
  }

  @Test
  void testTokenizerTrainedFromSamples(TestReporter reporter) throws Exception {
    final TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    final TokenizerModel model = TokenizerME.train(
        new CollectionObjectStream<>(samples(trainLines)),
        TokenizerFactory.create(null, "eng", null, true, null), params);
    final TokenizerEvaluator evaluator = new TokenizerEvaluator(new TokenizerME(model));
    evaluator.evaluate(new CollectionObjectStream<>(samples(testLines)));
    final double fMeasure = evaluator.getFMeasure().getFMeasure();
    reporter.publishEntry("fMeasure", String.valueOf(fMeasure));
    Assertions.assertEquals(EXPECTED_F_MEASURE, fMeasure, ACCURACY_DELTA);
  }

  /**
   * Joins the token column of each CoNLL-2000 sentence into one whitespace-separated line.
   *
   * @param file The corpus file: one token per line, a blank line between sentences.
   * @return One line per sentence.
   * @throws IOException If the file cannot be read.
   */
  private static List<String> tokenLines(File file) throws IOException {
    final List<String> lines = new ArrayList<>();
    final StringBuilder sentence = new StringBuilder();
    for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        if (!sentence.isEmpty()) {
          lines.add(sentence.toString());
          sentence.setLength(0);
        }
        continue;
      }
      final int space = line.indexOf(' ');
      final String token = space < 0 ? line : line.substring(0, space);
      if (!sentence.isEmpty()) {
        sentence.append(' ');
      }
      sentence.append(token);
    }
    if (!sentence.isEmpty()) {
      lines.add(sentence.toString());
    }
    return lines;
  }

  /**
   * Reads token lines through the English stream.
   *
   * @param lines The token lines.
   * @return The samples, one per line.
   * @throws IOException If reading fails.
   */
  private static List<TokenSample> samples(List<String> lines) throws IOException {
    final byte[] content = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
    final TokenSampleStream stream = new TokenSampleStream(new ByteArrayInputStream(content));
    final List<TokenSample> samples = new ArrayList<>(lines.size());
    while (stream.hasNext()) {
      samples.add(stream.next());
    }
    return samples;
  }

  private static int countSpans(List<TokenSample> samples) {
    int count = 0;
    for (TokenSample sample : samples) {
      count += sample.getTokenSpans().length;
    }
    return count;
  }

  /**
   * Checks that each span covers its token, with the bracket codes mapped to brackets, and
   * that consecutive spans are separated by at most one space.
   *
   * @param samples The samples.
   * @param lines The token lines the samples were read from.
   */
  private static void verifySpansCoverTokens(List<TokenSample> samples, List<String> lines) {
    for (int i = 0; i < samples.size(); i++) {
      final TokenSample sample = samples.get(i);
      final String[] tokens = lines.get(i).split(" ");
      final Span[] spans = sample.getTokenSpans();
      Assertions.assertEquals(tokens.length, spans.length, "sample " + i);
      int previousEnd = 0;
      for (int t = 0; t < tokens.length; t++) {
        final String expected = switch (tokens[t]) {
          case "-LRB-" -> "(";
          case "-LCB-" -> "{";
          case "-RRB-" -> ")";
          case "-RCB-" -> "}";
          default -> tokens[t];
        };
        Assertions.assertEquals(expected, spans[t].getCoveredText(sample.getText()).toString(),
            "sample " + i + " token " + t);
        Assertions.assertTrue(spans[t].getStart() - previousEnd <= 1, "sample " + i + " token " + t);
        previousEnd = spans[t].getEnd();
      }
      Assertions.assertEquals(previousEnd, sample.getText().length(), "sample " + i);
    }
  }

  /**
   * Digests the detokenized text and spans of each sample.
   *
   * @param samples The samples.
   * @return The MD5 digest as a positive integer.
   * @throws Exception If the algorithm is unavailable.
   */
  private static BigInteger digest(List<TokenSample> samples) throws Exception {
    final MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
    for (TokenSample sample : samples) {
      digest.update(sample.getText().getBytes(StandardCharsets.UTF_8));
      for (Span span : sample.getTokenSpans()) {
        digest.update((" " + span.getStart() + ":" + span.getEnd()).getBytes(StandardCharsets.UTF_8));
      }
      digest.update((byte) '\n');
    }
    return new BigInteger(1, digest.digest());
  }
}
