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

package opennlp.tools.ml.libsvm.doccat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hhn.mi.configuration.KernelType;
import de.hhn.mi.configuration.SvmConfigurationImpl;
import org.junit.jupiter.api.Test;

import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvmDoccatModelTest {

  private static final FeatureGenerator BOW = (text, extra) -> {
    List<String> f = new ArrayList<>(text.length);
    for (String w : text) f.add("bow=" + w);
    return f;
  };

  @Test
  void testModelPropertiesAfterTraining() throws IOException {
    SvmDoccatModel model = trainSimpleModel();

    assertEquals("eng", model.getLanguageCode());
    assertEquals(2, model.getNumberOfCategories());
    assertNotNull(model.getSvmModel());
    assertNotNull(model.getConfiguration());
    assertTrue(model.getFeatureVocabulary().size() > 0);
    assertTrue(model.getIdfValues().size() > 0);
  }

  @Test
  void testVocabularyIsUnmodifiable() throws IOException {
    SvmDoccatModel model = trainSimpleModel();

    assertThrows(UnsupportedOperationException.class, () ->
        model.getFeatureVocabulary().put("new", 999));
  }

  @Test
  void testCategoryMapsAreUnmodifiable() throws IOException {
    SvmDoccatModel model = trainSimpleModel();

    assertThrows(UnsupportedOperationException.class, () ->
        model.getIndexToCategory().put(999, "new"));

    assertThrows(UnsupportedOperationException.class, () ->
        model.getCategoryToIndex().put("new", 999));
  }

  @Test
  void testIdfValuesAreUnmodifiable() throws IOException {
    SvmDoccatModel model = trainSimpleModel();

    assertThrows(UnsupportedOperationException.class, () ->
        model.getIdfValues().put("new", 1.0));
  }

  @Test
  void testFeatureMinMaxAreUnmodifiable() throws IOException {
    SvmDoccatModel model = trainSimpleModel();

    assertThrows(UnsupportedOperationException.class, () ->
        model.getFeatureMinValues().put(999, 0.0));

    assertThrows(UnsupportedOperationException.class, () ->
        model.getFeatureMaxValues().put(999, 1.0));
  }

  @Test
  void testCategoryMappingConsistency() throws IOException {
    SvmDoccatModel model = trainSimpleModel();

    for (Map.Entry<String, Integer> entry : model.getCategoryToIndex().entrySet()) {
      assertEquals(entry.getKey(), model.getIndexToCategory().get(entry.getValue()));
    }
  }

  @Test
  void testSerializeAndDeserialize() throws IOException, ClassNotFoundException {
    SvmDoccatModel model = trainSimpleModel();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    model.serialize(baos);
    byte[] bytes = baos.toByteArray();
    assertTrue(bytes.length > 0);

    SvmDoccatModel restored = SvmDoccatModel.deserialize(new ByteArrayInputStream(bytes));

    assertEquals(model.getLanguageCode(), restored.getLanguageCode());
    assertEquals(model.getNumberOfCategories(), restored.getNumberOfCategories());
    assertEquals(model.getFeatureVocabulary(), restored.getFeatureVocabulary());
    assertEquals(model.getIndexToCategory(), restored.getIndexToCategory());
    assertEquals(model.getCategoryToIndex(), restored.getCategoryToIndex());
    assertEquals(model.getIdfValues(), restored.getIdfValues());
    assertEquals(model.getFeatureMinValues(), restored.getFeatureMinValues());
    assertEquals(model.getFeatureMaxValues(), restored.getFeatureMaxValues());
  }

  @Test
  void testDeserializeInvalidStreamThrows() {
    byte[] garbage = {0x00, 0x01, 0x02};
    assertThrows(IOException.class, () ->
        SvmDoccatModel.deserialize(new ByteArrayInputStream(garbage)));
  }

  @Test
  void testDeserializeRejectsForeignClass() throws IOException {
    // A well-formed Java serialization stream containing a class that is NOT
    // part of the SvmDoccatModel graph. Without ObjectInputFilter, readObject()
    // would fully deserialize the foreign object before the cast threw
    // ClassCastException — the gadget-chain attack window. With the filter,
    // the read fails at the class check.
    byte[] payload = serialize(new File("/tmp/poc"));
    InvalidClassException ex = assertThrows(InvalidClassException.class, () ->
        SvmDoccatModel.deserialize(new ByteArrayInputStream(payload)));
    assertTrue(ex.getMessage().contains("filter"),
        "expected filter rejection, got: " + ex.getMessage());
  }

  @Test
  void testDeserializeRejectsForeignCollectionType() throws IOException {
    // java.util.HashMap is on the allow-list, but java.util.Hashtable is not.
    // Confirms the filter does not blanket-allow java.util collections.
    byte[] payload = serialize(new java.util.Hashtable<>(Map.of("k", "v")));
    assertThrows(InvalidClassException.class, () ->
        SvmDoccatModel.deserialize(new ByteArrayInputStream(payload)));
  }

  @Test
  void testDeserializeRejectsUnrelatedSerializable() throws IOException {
    // A user-defined Serializable that is not in the allow-list must be
    // rejected even though it is structurally valid.
    byte[] payload = serialize(new ForeignPayload("yolo"));
    assertThrows(InvalidClassException.class, () ->
        SvmDoccatModel.deserialize(new ByteArrayInputStream(payload)));
  }

  @Test
  void testDeserializeRejectsDeeplyNestedGraph() throws IOException {
    // Build a chain of HashMaps deeper than the configured maxdepth (64).
    // HashMap is on the allow-list, so this exercises the resource limits.
    HashMap<String, Object> root = new HashMap<>();
    HashMap<String, Object> cursor = root;
    for (int i = 0; i < 200; i++) {
      HashMap<String, Object> next = new HashMap<>();
      cursor.put("n", next);
      cursor = next;
    }
    byte[] payload = serialize(root);
    assertThrows(InvalidClassException.class, () ->
        SvmDoccatModel.deserialize(new ByteArrayInputStream(payload)));
  }

  @Test
  void testDeserializeWithCustomLimitsRoundTrips() throws IOException, ClassNotFoundException {
    // The configurable overload accepts a real model with custom limits and
    // round-trips it identically to the default-limits path.
    SvmDoccatModel model = trainSimpleModel();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    model.serialize(baos);
    byte[] bytes = baos.toByteArray();

    SvmDoccatModel.DeserializationLimits raised =
        new SvmDoccatModel.DeserializationLimits(128, 50_000_000L, 50_000_000L);
    SvmDoccatModel restored = SvmDoccatModel.deserialize(
        new ByteArrayInputStream(bytes), raised);

    assertEquals(model.getLanguageCode(), restored.getLanguageCode());
    assertEquals(model.getNumberOfCategories(), restored.getNumberOfCategories());
    assertEquals(model.getFeatureVocabulary(), restored.getFeatureVocabulary());
  }

  @Test
  void testDeserializeWithCustomLimitsAcceptsDeeperGraphs() throws IOException, ClassNotFoundException {
    // A 100-deep HashMap chain exceeds the default maxDepth (64) but fits
    // within a raised limit. Confirms the configuration knob actually moves.
    HashMap<String, Object> root = new HashMap<>();
    HashMap<String, Object> cursor = root;
    for (int i = 0; i < 100; i++) {
      HashMap<String, Object> next = new HashMap<>();
      cursor.put("n", next);
      cursor = next;
    }
    byte[] payload = serialize(root);

    // Default limits reject this graph...
    assertThrows(InvalidClassException.class, () ->
        SvmDoccatModel.deserialize(new ByteArrayInputStream(payload)));

    // ...but a wider depth limit lets it through far enough to fail at the
    // final cast (HashMap is not an SvmDoccatModel) — proving the depth check
    // is no longer the gate.
    SvmDoccatModel.DeserializationLimits raised =
        new SvmDoccatModel.DeserializationLimits(256, 5_000_000L, 10_000_000L);
    assertThrows(ClassCastException.class, () ->
        SvmDoccatModel.deserialize(new ByteArrayInputStream(payload), raised));
  }

  @Test
  void testDeserializationLimitsValidatesArguments() {
    assertThrows(IllegalArgumentException.class, () ->
        new SvmDoccatModel.DeserializationLimits(0, 1, 1));
    assertThrows(IllegalArgumentException.class, () ->
        new SvmDoccatModel.DeserializationLimits(1, 0, 1));
    assertThrows(IllegalArgumentException.class, () ->
        new SvmDoccatModel.DeserializationLimits(1, 1, 0));
  }

  @Test
  void testDeserializeRejectsNullLimits() {
    byte[] empty = {};
    assertThrows(IllegalArgumentException.class, () ->
        SvmDoccatModel.deserialize(new ByteArrayInputStream(empty), null));
  }

  @Test
  void testDeserializeRejectsNullStream() {
    assertThrows(IllegalArgumentException.class, () ->
        SvmDoccatModel.deserialize(null));
    assertThrows(IllegalArgumentException.class, () ->
        SvmDoccatModel.deserialize(null, SvmDoccatModel.DeserializationLimits.DEFAULT));
  }

  @Test
  void testSerializeRejectsNullStream() throws IOException {
    SvmDoccatModel model = trainSimpleModel();
    assertThrows(IllegalArgumentException.class, () -> model.serialize(null));
  }

  private static byte[] serialize(Object obj) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(obj);
    }
    return baos.toByteArray();
  }

  private static class ForeignPayload implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    @SuppressWarnings("unused")
    private final String name;
    ForeignPayload(String name) { this.name = name; }
  }

  private SvmDoccatModel trainSimpleModel() throws IOException {
    List<DocumentSample> samples = new ArrayList<>();
    samples.add(new DocumentSample("a", new String[]{"x", "y"}));
    samples.add(new DocumentSample("a", new String[]{"x", "z"}));
    samples.add(new DocumentSample("b", new String[]{"p", "q"}));
    samples.add(new DocumentSample("b", new String[]{"p", "r"}));

    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setScaleFeatures(false)
        .build();

    return DocumentCategorizerSVM.train("eng",
        new CollectionObjectStream<>(samples), config, BOW);
  }

  static class CollectionObjectStream<T> implements ObjectStream<T> {
    private final List<T> items;
    private int index = 0;
    CollectionObjectStream(List<T> items) { this.items = items; }
    @Override public T read() { return index < items.size() ? items.get(index++) : null; }
    @Override public void reset() { index = 0; }
    @Override public void close() { }
  }
}
