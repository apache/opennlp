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
package opennlp.tools.geo;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeoResolutionTest {

  private static final GazetteerEntry TOKYO = new GazetteerEntry("naturalearth", "1159151479",
      "Tokyo", List.of(), new GeoPoint(35.6839, 139.7744), "JP", List.of(), 37977000L, "CITY",
      Map.of());

  @Test
  void testHoldsComponents() {
    final Span mention = new Span(10, 15);
    final GeoResolution resolution = new GeoResolution(mention, TOKYO, 0.9);
    assertEquals(mention, resolution.mention());
    assertEquals(TOKYO, resolution.entry());
    assertEquals(0.9, resolution.confidence());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 0.5, 1.0})
  void testAcceptsConfidenceRange(double confidence) {
    assertEquals(confidence, new GeoResolution(new Span(0, 1), TOKYO, confidence).confidence());
  }

  @Test
  void testRejectsNullMention() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new GeoResolution(null, TOKYO, 0.5));
    assertTrue(e.getMessage().startsWith("Mention must not be null"), e.getMessage());
  }

  @Test
  void testRejectsNullEntry() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new GeoResolution(new Span(0, 1), null, 0.5));
    assertTrue(e.getMessage().startsWith("Entry must not be null"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.001, 1.001, Double.NaN})
  void testRejectsOutOfRangeConfidence(double confidence) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new GeoResolution(new Span(0, 1), TOKYO, confidence));
    assertTrue(e.getMessage().startsWith("Confidence must be in [0, 1]"), e.getMessage());
  }
}
