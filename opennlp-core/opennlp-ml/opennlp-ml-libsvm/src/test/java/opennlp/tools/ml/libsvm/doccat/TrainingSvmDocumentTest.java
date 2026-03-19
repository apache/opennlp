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

import java.util.ArrayList;
import java.util.List;

import de.hhn.mi.domain.SvmClassLabel;
import de.hhn.mi.domain.SvmFeature;
import de.hhn.mi.domain.SvmFeatureImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingSvmDocumentTest {

  @Test
  void testEmptyFeatures() {
    TrainingSvmDocument doc = new TrainingSvmDocument(List.of());
    assertTrue(doc.getSvmFeatures().isEmpty());
    assertTrue(doc.getAllClassLabels().isEmpty());
    assertNull(doc.getClassLabelWithHighestProbability());
  }

  @Test
  void testFeaturesAreRetained() {
    List<SvmFeature> features = new ArrayList<>();
    features.add(new SvmFeatureImpl(1, 0.5));
    features.add(new SvmFeatureImpl(3, 1.2));
    TrainingSvmDocument doc = new TrainingSvmDocument(features);

    assertEquals(2, doc.getSvmFeatures().size());
    assertEquals(1, doc.getSvmFeatures().get(0).getIndex());
    assertEquals(0.5, doc.getSvmFeatures().get(0).getValue());
    assertEquals(3, doc.getSvmFeatures().get(1).getIndex());
  }

  @Test
  void testAddAndRetrieveClassLabels() {
    TrainingSvmDocument doc = new TrainingSvmDocument(List.of());

    doc.addClassLabel(new TrainingSvmClassLabel(0, "cat_a", 0.3));
    doc.addClassLabel(new TrainingSvmClassLabel(1, "cat_b", 0.7));

    assertEquals(2, doc.getAllClassLabels().size());
    SvmClassLabel best = doc.getClassLabelWithHighestProbability();
    assertNotNull(best);
    assertEquals(1.0, best.getNumeric());
    assertEquals(0.7, best.getProbability());
  }

  @Test
  void testGetClassLabelWithHighestProbability() {
    TrainingSvmDocument doc = new TrainingSvmDocument(List.of());
    doc.addClassLabel(new TrainingSvmClassLabel(0, "low", 0.1));
    doc.addClassLabel(new TrainingSvmClassLabel(1, "mid", 0.5));
    doc.addClassLabel(new TrainingSvmClassLabel(2, "high", 0.9));

    assertEquals("high", doc.getClassLabelWithHighestProbability().getName());
  }

  @Test
  void testAllClassLabelsIsUnmodifiable() {
    TrainingSvmDocument doc = new TrainingSvmDocument(List.of());
    doc.addClassLabel(new TrainingSvmClassLabel(0, "a", 1.0));

    assertThrows(UnsupportedOperationException.class, () ->
        doc.getAllClassLabels().add(new TrainingSvmClassLabel(1, "b", 0.5)));
  }

  @Test
  void testNullFeaturesThrows() {
    assertThrows(NullPointerException.class, () -> new TrainingSvmDocument(null));
  }

  @Test
  void testNullClassLabelThrows() {
    TrainingSvmDocument doc = new TrainingSvmDocument(List.of());
    assertThrows(NullPointerException.class, () -> doc.addClassLabel(null));
  }
}
