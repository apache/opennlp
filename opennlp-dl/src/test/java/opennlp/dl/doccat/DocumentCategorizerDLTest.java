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

package opennlp.dl.doccat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.WordpieceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentCategorizerDLTest {

  private static Map<String, Integer> vocab() {
    final Map<String, Integer> vocab = new HashMap<>();
    vocab.put(WordpieceTokenizer.BERT_CLS_TOKEN, 0);
    vocab.put(WordpieceTokenizer.BERT_SEP_TOKEN, 1);
    vocab.put(WordpieceTokenizer.BERT_UNK_TOKEN, 2);
    vocab.put("hello", 3);
    vocab.put("world", 4);
    return vocab;
  }

  @Test
  void testTokenIdsMapsTokensToVocabularyIds() {
    final long[] ids = DocumentCategorizerDL.tokenIds(
        new String[] {WordpieceTokenizer.BERT_CLS_TOKEN, "hello", "world",
            WordpieceTokenizer.BERT_SEP_TOKEN}, vocab());

    assertArrayEquals(new long[] {0, 3, 4, 1}, ids);
  }

  @Test
  void testTokenIdsRejectsTokensMissingFromVocabulary() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        DocumentCategorizerDL.tokenIds(new String[] {"hello", "missing"}, vocab()));

    assertTrue(e.getMessage().contains("missing"),
        "the error message should name the missing token: " + e.getMessage());
  }

  @Test
  void testSoftmaxIsFiniteForLargeLogits() {
    assertArrayEquals(new double[] {(float) (1.0 / 3), (float) (1.0 / 3), (float) (1.0 / 3)},
        DocumentCategorizerDL.softmax(new float[] {1000f, 1000f, 1000f}));
    assertArrayEquals(new double[] {1.0, 0.0},
        DocumentCategorizerDL.softmax(new float[] {1000f, -1000f}));
    assertArrayEquals(new double[] {0.5, 0.5},
        DocumentCategorizerDL.softmax(new float[] {Float.MAX_VALUE, Float.MAX_VALUE}));
  }

  @Test
  void testSoftmaxMatchesReferenceDistribution() {
    // softmax([1, 2, 3]) = [0.09003057, 0.24472847, 0.66524096]
    final double[] out = DocumentCategorizerDL.softmax(new float[] {1f, 2f, 3f});

    assertEquals(0.09003057, out[0], 1e-7);
    assertEquals(0.24472847, out[1], 1e-7);
    assertEquals(0.66524096, out[2], 1e-7);
  }

  @Test
  void testSoftmaxMatchesUnshiftedFormForModerateLogits() {
    final float[] logits = {-4.25f, 0.5f, 3.75f, 1.125f, -0.5f};

    double sum = 0.0;
    for (final float logit : logits) {
      sum += Math.exp(logit);
    }
    final double[] out = DocumentCategorizerDL.softmax(logits);
    for (int x = 0; x < logits.length; x++) {
      assertEquals((float) (Math.exp(logits[x]) / sum), out[x], Math.ulp((float) out[x]));
    }
  }
}
