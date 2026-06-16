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

package opennlp.dl.vectors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.eval.AbstractEvalTest;

public class SentenceVectorsDLEval extends AbstractEvalTest {

  @Test
  public void generateVectorsTest() throws Exception {

    final File MODEL_FILE_NAME = new File(getOpennlpDataDir(), "onnx/sentence-transformers/model.onnx");
    final File VOCAB_FILE_NAME = new File(getOpennlpDataDir(), "onnx/sentence-transformers/vocab.txt");

    final String sentence = "george washington was president";

    try (final SentenceVectorsDL sv = new SentenceVectorsDL(MODEL_FILE_NAME, VOCAB_FILE_NAME)) {

      final float[] vectors = sv.getVectors(sentence);

      /*
       * Expected values are the first three components of last_hidden_state[0][0]
       * (the [CLS] position) produced by the ONNX export of
       * https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2 for the
       * input "[CLS] george washington was president [SEP]" with the corrected
       * encoding (attention_mask=1, token_type_ids=0; see OPENNLP-1836).
       *
       * Reproducible independently of this class with the HuggingFace reference
       * implementation (Python packages 'tokenizers' and 'onnxruntime'):
       *
       *   tok = BertWordPieceTokenizer("vocab.txt", lowercase=True)
       *   enc = tok.encode(sentence, add_special_tokens=True)
       *   ids = np.array([enc.ids], dtype=np.int64)
       *   out = ort.InferenceSession("model.onnx").run(None, {
       *       "input_ids": ids,
       *       "attention_mask": np.ones_like(ids),
       *       "token_type_ids": np.zeros_like(ids)})[0]
       *   out[0][0][:3]  # -> [0.04474502, 0.20219636, 0.41306049]
       */
      Assertions.assertEquals(0.044745024, vectors[0], 0.00001);
      Assertions.assertEquals(0.20219636, vectors[1], 0.00001);
      Assertions.assertEquals(0.41306049, vectors[2], 0.00001);
      Assertions.assertEquals(384, vectors.length);

      // The uncased model lower cases during tokenization, so a capitalized
      // variant must produce the same vectors. Prior to BERT basic
      // tokenization, every capitalized word was mapped to [UNK].
      final float[] capitalized = sv.getVectors("George Washington was President");

      Assertions.assertArrayEquals(vectors, capitalized, 0.00001f);
    }

  }

  /**
   * Verifies that a single {@link SentenceVectorsDL} instance is safe to share across
   * threads: concurrent {@link SentenceVectorsDL#getVectors(String)} calls on one instance
   * must all return the same vector as the single-threaded baseline.
   */
  @Test
  public void generateVectorsConcurrentTest() throws Exception {

    final File model = new File(getOpennlpDataDir(), "onnx/sentence-transformers/model.onnx");
    final File vocab = new File(getOpennlpDataDir(), "onnx/sentence-transformers/vocab.txt");

    final String sentence = "george washington was president";

    final int threads = 8;
    final int iterationsPerThread = 10;

    try (final SentenceVectorsDL sv = new SentenceVectorsDL(model, vocab)) {

      final float[] baseline = sv.getVectors(sentence);

      final ExecutorService executor = Executors.newFixedThreadPool(threads);
      try {
        final CountDownLatch startGate = new CountDownLatch(1);
        final List<Future<Boolean>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
          futures.add(executor.submit(() -> {
            startGate.await();
            for (int i = 0; i < iterationsPerThread; i++) {
              final float[] vectors = sv.getVectors(sentence);
              if (vectors.length != baseline.length) {
                return false;
              }
              for (int c = 0; c < baseline.length; c++) {
                if (Math.abs(vectors[c] - baseline[c]) > 0.00001f) {
                  return false;
                }
              }
            }
            return true;
          }));
        }

        startGate.countDown();
        for (Future<Boolean> future : futures) {
          Assertions.assertTrue(future.get(),
              "a concurrent getVectors() returned a vector inconsistent with the single-threaded case");
        }
      } finally {
        executor.shutdownNow();
      }
    }

  }

}
