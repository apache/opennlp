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
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Asserts the {@code Serializable} contract inherited through
 * {@code opennlp.tools.util.normalizer.CharSequenceNormalizer}: a tokenizer round-tripped
 * through Java object serialization must encode and normalize exactly like the original.
 * Also asserts the guarded read path of
 * {@link SentencePieceTokenizer#deserialize(InputStream)}: foreign payloads and streams
 * exceeding the resource limits are rejected before materialisation.
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

  /**
   * Serializes the tokenizer of the given fixture model through
   * {@link SentencePieceTokenizer#serialize(OutputStream)}.
   *
   * @param model The fixture model name.
   * @return The serialized bytes.
   */
  private static byte[] serialized(String model) throws IOException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    SentencePieceParityTest.tokenizer(model).serialize(bytes);
    return bytes.toByteArray();
  }

  @ParameterizedTest
  @ValueSource(strings = {"tiny-unigram", "tiny-unigram-bytefb", "tiny-bpe",
      "tiny-unigram-identity", "tiny-unigram-suffix"})
  void testGuardedDeserializePreservesEncoding(String model)
      throws IOException, ClassNotFoundException {
    final SentencePieceTokenizer original = SentencePieceParityTest.tokenizer(model);
    final SentencePieceTokenizer copy =
        SentencePieceTokenizer.deserialize(new ByteArrayInputStream(serialized(model)));

    assertEquals(original.algorithm(), copy.algorithm(), model + " algorithm");
    for (final String input : INPUTS) {
      final String context = model + " input <" + input + ">";
      assertIterableEquals(original.encode(input), copy.encode(input), context + " pieces");
    }
  }

  /**
   * Verifies that a stream whose top-level object is not on the allow-list is rejected
   * before it is materialised, even though its classes are harmless JDK types.
   */
  @Test
  void testForeignPayloadIsRejected() throws IOException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      final ArrayList<String> foreign = new ArrayList<>();
      foreign.add("not a tokenizer");
      out.writeObject(foreign);
    }
    assertThrows(InvalidClassException.class, () ->
        SentencePieceTokenizer.deserialize(new ByteArrayInputStream(bytes.toByteArray())));
  }

  /**
   * Verifies that a legitimate stream is rejected when it exceeds the supplied resource
   * limits, so the limits bound the graph regardless of the class allow-list.
   */
  @Test
  void testStreamExceedingLimitsIsRejected() throws IOException {
    final byte[] legitimate = serialized("tiny-unigram");
    final SentencePieceTokenizer.DeserializationLimits tight =
        new SentencePieceTokenizer.DeserializationLimits(1, 1, 1);
    assertThrows(InvalidClassException.class, () ->
        SentencePieceTokenizer.deserialize(new ByteArrayInputStream(legitimate), tight));
  }

  /**
   * Verifies that null arguments are rejected with {@link IllegalArgumentException} at the
   * API boundary.
   */
  @Test
  void testNullArgumentsAreRejected() throws IOException {
    final SentencePieceTokenizer tokenizer = SentencePieceParityTest.tokenizer("tiny-unigram");
    assertThrows(IllegalArgumentException.class, () -> tokenizer.serialize(null));
    assertThrows(IllegalArgumentException.class, () ->
        SentencePieceTokenizer.deserialize(null));
    assertThrows(IllegalArgumentException.class, () ->
        SentencePieceTokenizer.deserialize(new ByteArrayInputStream(new byte[0]), null));
    assertThrows(IllegalArgumentException.class, () ->
        new SentencePieceTokenizer.DeserializationLimits(0, 1, 1));
    assertThrows(IllegalArgumentException.class, () ->
        new SentencePieceTokenizer.DeserializationLimits(1, 0, 1));
    assertThrows(IllegalArgumentException.class, () ->
        new SentencePieceTokenizer.DeserializationLimits(1, 1, 0));
  }
}
