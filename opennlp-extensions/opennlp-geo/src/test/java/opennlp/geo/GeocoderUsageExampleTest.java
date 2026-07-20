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
package opennlp.geo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.geo.GeoResolution;
import opennlp.tools.geo.Geocoder;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the cookbook path documented in {@code geo.xml}: build a
 * {@link PopulationPriorGeocoder} over a gazetteer and resolve location mentions to
 * {@link GeoResolution} entries.
 */
public class GeocoderUsageExampleTest {

  private static final String[] ROWS = {
      "naturalearth;1;Springfield;;39.79968;-89.64399;US;Illinois;750000;CITY;",
      "naturalearth;2;Springfield;;37.18095;-93.29229;US;Missouri;450000;CITY;",
      "naturalearth;3;Springfield;;42.10148;-72.58982;US;Massachusetts;680000;CITY;",
  };

  private static BundledGazetteer fixtureGazetteer() {
    final StringBuilder data = new StringBuilder();
    for (final String row : ROWS) {
      data.append(row).append('\n');
    }
    final InputStream in =
        new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8));
    try {
      return new BundledGazetteer(BundledGazetteer.parse(in, "test-gazetteer.txt"));
    } catch (IOException e) {
      throw new IllegalStateException("Unexpected IOException from an in-memory stream", e);
    }
  }

  @Test
  void testPopulationPriorResolvesAmbiguousMention() throws IOException {
    final Geocoder geocoder = new PopulationPriorGeocoder(fixtureGazetteer());
    final String text = "She grew up in Springfield.";
    final int start = text.indexOf("Springfield");
    assertTrue(start >= 0);
    final Span mention = new Span(start, start + "Springfield".length());

    final List<GeoResolution> resolutions = geocoder.resolve(text, List.of(mention));

    assertEquals(1, resolutions.size());
    assertEquals("1", resolutions.get(0).entry().recordId());
    assertEquals(mention, resolutions.get(0).mention());
  }
}
