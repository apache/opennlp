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
 * Abstract base class for {@link BPEModel} tests.
 * <p>
 * Subclasses provide a language-specific corpus and language code.
 *
 * @see BPEModel
 */
public abstract class AbstractBPEModelTest {

  /**
   * @return a corpus of sentences for training.
   */
  protected abstract List<String> getCorpus();

  /**
   * @return the ISO language code to use during training.
   */
  protected abstract String getLanguageCode();

  protected BPEModel trainModel(int numMerges) {
    return new BPETokenizerTrainer().train(getCorpus(), numMerges, getLanguageCode());
  }

  /**
   * Tests that a model can be serialized and deserialized without data loss.
   */
  @Test
  void testBPEModelSerialization() throws IOException {
    final BPEModel model = trainModel(10);
    Assertions.assertFalse(model.isLoadedFromSerialized());

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      model.serialize(out);

      final BPEModel restored = new BPEModel(new ByteArrayInputStream(out.toByteArray()));
      Assertions.assertNotNull(restored);
      Assertions.assertTrue(restored.isLoadedFromSerialized());
    }
  }

  /**
   * Tests that merge rules are preserved after serialization roundtrip.
   */
  @Test
  void testMergesPreservedAfterSerialization() throws IOException {
    final BPEModel original = trainModel(10);

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      original.serialize(out);

      final BPEModel restored = new BPEModel(new ByteArrayInputStream(out.toByteArray()));

      final List<SymbolPair> originalMerges = original.getMerges();
      final List<SymbolPair> restoredMerges = restored.getMerges();

      Assertions.assertEquals(originalMerges.size(), restoredMerges.size());
      for (int i = 0; i < originalMerges.size(); i++) {
        Assertions.assertEquals(originalMerges.get(i), restoredMerges.get(i));
      }
    }
  }

  /**
   * Tests that merge order is preserved — order determines priority.
   */
  @Test
  void testMergeOrderPreserved() throws IOException {
    final BPEModel model = trainModel(5);

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      model.serialize(out);

      final BPEModel restored = new BPEModel(new ByteArrayInputStream(out.toByteArray()));

      // Verify exact order matches
      Assertions.assertEquals(model.getMerges(), restored.getMerges());
    }
  }

  /**
   * Tests that a deserialized model can be used to tokenize text.
   */
  @Test
  void testDeserializedModelCanTokenize() throws IOException {
    final BPEModel original = trainModel(10);

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      original.serialize(out);

      final BPEModel loaded = new BPEModel(new ByteArrayInputStream(out.toByteArray()));
      final BPETokenizer tokenizer = new BPETokenizer(loaded);

      final String[] tokens = tokenizer.tokenize("low");
      Assertions.assertTrue(tokens.length >= 1);
      Assertions.assertEquals("low", String.join("", tokens));
    }
  }

  /**
   * Tests that the language code is preserved in the model.
   */
  @Test
  void testLanguagePreserved() throws IOException {
    final BPEModel model = trainModel(5);

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      model.serialize(out);

      final BPEModel restored = new BPEModel(new ByteArrayInputStream(out.toByteArray()));
      Assertions.assertEquals(getLanguageCode(), restored.getLanguage());
    }
  }

  /**
   * Tests that the factory is accessible from a deserialized model.
   */
  @Test
  void testFactoryAccessibleAfterDeserialization() throws IOException {
    final BPEModel original = trainModel(5);

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      original.serialize(out);

      final BPEModel restored = new BPEModel(new ByteArrayInputStream(out.toByteArray()));
      Assertions.assertNotNull(restored.getFactory());
      Assertions.assertInstanceOf(BPETokenizerFactory.class, restored.getFactory());
    }
  }
}
