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

import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.dl.Tokens;
import opennlp.tools.tokenize.WordpieceEncoder;
import opennlp.tools.tokenize.WordpieceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SentenceVectorsDLTest {

  private static Map<String, Integer> vocabulary() {
    return Map.of(
        WordpieceTokenizer.BERT_CLS_TOKEN, 101,
        WordpieceTokenizer.BERT_SEP_TOKEN, 205,
        WordpieceTokenizer.BERT_UNK_TOKEN, 999,
        "hello", 42);
  }

  @Test
  void testEncodeUsesSingleSegmentBertInputsAndVocabularyIds() {
    final Map<String, Integer> vocabulary = vocabulary();
    final WordpieceEncoder encoder = new WordpieceEncoder(vocabulary, true,
        WordpieceTokenizer.BERT_CLS_TOKEN,
        WordpieceTokenizer.BERT_SEP_TOKEN,
        WordpieceTokenizer.BERT_UNK_TOKEN);

    final Tokens tokens = SentenceVectorsDL.encode("Hello missing", encoder);

    assertArrayEquals(new String[] {"[CLS]", "hello", "[UNK]", "[SEP]"}, tokens.tokens());
    assertArrayEquals(new long[] {101, 42, 999, 205}, tokens.ids());
    assertArrayEquals(new long[] {1, 1, 1, 1}, tokens.mask());
    assertArrayEquals(new long[] {0, 0, 0, 0}, tokens.types());
  }
}
