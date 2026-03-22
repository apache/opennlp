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

package opennlp.tools.tokenize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.BPETokenizer.SymbolPair;

/**
 * Tests for the {@link BPETokenizerFactory} class.
 * <p>
 * Verifies that the factory correctly manages BPE merge rules artifacts,
 * serializers, and that properties survive model serialization roundtrips.
 *
 * @see BPETokenizerFactory
 * @see BPEModel
 */
public class BPETokenizerFactoryTest {

  private static final List<String> CORPUS = List.of(
      "low low low low low",
      "lower lower lower",
      "newest newest newest newest"
  );

  /**
   * Tests that the factory provides merge rules after training.
   */
  @Test
  void testFactoryProvidesMerges() {
    final BPEModel model = new BPETokenizerTrainer().train(CORPUS, 10, "en");
    final BPETokenizerFactory factory = model.getFactory();

    Assertions.assertNotNull(factory);
    Assertions.assertNotNull(factory.getMerges());
    Assertions.assertFalse(factory.getMerges().isEmpty());
  }

  /**
   * Tests that the factory language code is set correctly.
   */
  @Test
  void testFactoryLanguageCode() {
    final List<SymbolPair> merges = List.of(new SymbolPair("a", "b"));
    final BPETokenizerFactory factory = new BPETokenizerFactory("de", merges);

    Assertions.assertEquals("de", factory.getLanguageCode());
  }

  /**
   * Tests that merge rules are accessible from the factory after
   * model serialization and deserialization.
   */
  @Test
  void testFactorySurvivesSerialization() throws IOException {
    final BPEModel original = new BPETokenizerTrainer().train(CORPUS, 10, "en");

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    original.serialize(out);

    final BPEModel restored = new BPEModel(new ByteArrayInputStream(out.toByteArray()));
    final BPETokenizerFactory factory = restored.getFactory();

    Assertions.assertNotNull(factory);
    Assertions.assertNotNull(factory.getMerges());
    Assertions.assertEquals(original.getMerges().size(), factory.getMerges().size());
  }

  /**
   * Tests that the factory merges are consistent between direct construction
   * and deserialized access.
   */
  @Test
  void testMergesConsistentAfterRoundtrip() throws IOException {
    final BPEModel original = new BPETokenizerTrainer().train(CORPUS, 5, "en");
    final List<SymbolPair> originalMerges = original.getFactory().getMerges();

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    original.serialize(out);

    final BPEModel restored = new BPEModel(new ByteArrayInputStream(out.toByteArray()));
    final List<SymbolPair> restoredMerges = restored.getFactory().getMerges();

    Assertions.assertEquals(originalMerges, restoredMerges);
  }

  /**
   * Tests that the factory creates the correct artifact serializer map.
   */
  @Test
  void testArtifactSerializersMapContainsMergesSerializer() {
    final BPETokenizerFactory factory = new BPETokenizerFactory("en", List.of());

    Assertions.assertTrue(factory.createArtifactSerializersMap().containsKey("merges"));
  }

  /**
   * Tests that the factory creates an artifact map containing the merges entry.
   */
  @Test
  void testArtifactMapContainsMergesEntry() {
    final List<SymbolPair> merges = List.of(
        new SymbolPair("a", "b"),
        new SymbolPair("ab", "c" + BPETokenizer.END_OF_WORD)
    );
    final BPETokenizerFactory factory = new BPETokenizerFactory("en", merges);

    Assertions.assertTrue(factory.createArtifactMap().containsKey(BPETokenizerFactory.MERGES_ENTRY_NAME));
  }

  /**
   * Tests that the empty constructor creates a valid factory (for model loading).
   */
  @Test
  void testEmptyConstructor() {
    final BPETokenizerFactory factory = new BPETokenizerFactory();

    // Empty factory should not throw
    Assertions.assertNotNull(factory);
    Assertions.assertNotNull(factory.createArtifactSerializersMap());
  }

  /**
   * Tests null parameter validation.
   */
  @Test
  void testNullLanguageCodeThrows() {
    Assertions.assertThrows(NullPointerException.class,
        () -> new BPETokenizerFactory(null, List.of()));
  }

  @Test
  void testNullMergesThrows() {
    Assertions.assertThrows(NullPointerException.class,
        () -> new BPETokenizerFactory("en", null));
  }
}
