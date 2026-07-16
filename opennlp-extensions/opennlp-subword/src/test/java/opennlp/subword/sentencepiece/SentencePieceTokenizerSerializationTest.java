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
package opennlp.subword.sentencepiece;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/**
 * Asserts the {@code Serializable} contract inherited through
 * {@code opennlp.tools.util.normalizer.CharSequenceNormalizer}: a tokenizer round-tripped
 * through Java object serialization must encode and normalize exactly like the original.
 */
class SentencePieceTokenizerSerializationTest {

  private static final String[] INPUTS = {
      "",
      "The quick brown fox jumps over the lazy dog.",
      " Hello   world  ",
      "tokenization and segmentation",
      "caf\u00e9 na\u00efve \u4e2d\u6587"
  };

  @ParameterizedTest
  @ValueSource(strings = {"tiny-unigram", "tiny-unigram-bytefb", "tiny-bpe",
      "tiny-unigram-identity", "tiny-unigram-suffix"})
  void testRoundTripPreservesEncoding(String model) throws IOException, ClassNotFoundException {
    final SentencePieceTokenizer original = SentencePieceParityTest.tokenizer(model);

    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(original);
    }
    final SentencePieceTokenizer copy;
    try (ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (SentencePieceTokenizer) in.readObject();
    }

    assertEquals(original.algorithm(), copy.algorithm(), model + " algorithm");
    assertEquals(original.vocabularySize(), copy.vocabularySize(), model + " vocabulary size");
    for (final String input : INPUTS) {
      final String context = model + " input <" + input + ">";
      assertIterableEquals(original.encode(input), copy.encode(input), context + " pieces");
      assertEquals(original.normalize(input).toString(), copy.normalize(input).toString(),
          context + " normalized form");
    }
  }
}
