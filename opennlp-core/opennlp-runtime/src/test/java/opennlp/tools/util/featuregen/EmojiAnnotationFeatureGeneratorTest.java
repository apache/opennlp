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

package opennlp.tools.util.featuregen;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.normalizer.EmojiAnnotation;
import opennlp.tools.util.normalizer.EmojiAnnotator;

import static opennlp.tools.util.normalizer.NormalizerTestUtil.cp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmojiAnnotationFeatureGeneratorTest {

  @Test
  void annotatedTokensContributeTypedFeatures() {
    final AdaptiveFeatureGenerator generator = new EmojiAnnotationFeatureGenerator();
    final String[] tokens = {"I", cp(0x2764, 0xFE0F), "Berlin", cp(0x1F1E9, 0x1F1EA)};
    final List<String> features = new ArrayList<>();
    generator.createFeatures(features, tokens, 1, null);
    assertEquals(List.of("emojiSentiment=2", "emojiType=HEART",
        "emojiCategory=SMILEYS_AND_EMOTION"), features);
    features.clear();
    generator.createFeatures(features, tokens, 3, null);
    // The flag's entity type acts as gazetteer-like evidence for the name finder, and the region
    // is decoded from the sequence with no dictionary. There is no sentiment feature: a record
    // never fabricates a value it has no source for.
    assertEquals(List.of("emojiType=FLAG", "emojiCategory=FLAGS", "emojiRegion=DE"), features);
  }

  @Test
  void ordinaryAndDegenerateTokensContributeNothing() {
    final AdaptiveFeatureGenerator generator = new EmojiAnnotationFeatureGenerator();
    final List<String> features = new ArrayList<>();
    final String[] tokens = {"word", "", ":-)", cp(0x1F1E9), cp(0x1F9F8)};
    for (int i = 0; i < tokens.length; i++) {
      generator.createFeatures(features, tokens, i, null);
    }
    // Includes the lone regional indicator: bulk feature generation must survive damaged text.
    assertTrue(features.isEmpty(), "Unexpected features: " + features);
  }

  @Test
  void joinedFactsFlowIntoFeaturesThroughAConfiguredAnnotator() {
    final EmojiAnnotator annotator = new EmojiAnnotator((symbol, isoRegion) -> Map.of());
    final AdaptiveFeatureGenerator generator = new EmojiAnnotationFeatureGenerator(annotator);
    final List<String> features = new ArrayList<>();
    generator.createFeatures(features, new String[] {cp(0x1F642)}, 0, null);
    assertEquals(List.of("emojiSentiment=1", "emojiType=FACE",
        "emojiCategory=SMILEYS_AND_EMOTION"), features);
    assertThrows(IllegalArgumentException.class,
        () -> new EmojiAnnotationFeatureGenerator(null));
  }

  @Test
  void wiredThroughAFeatureGenerationDescriptor() throws Exception {
    // The opt-in seam: only a descriptor that names the factory activates the generator; no
    // default descriptor is touched.
    final String descriptor = "<featureGenerators name=\"test\">\n"
        + "  <generator class=\"opennlp.tools.util.featuregen.AggregatedFeatureGeneratorFactory\">\n"
        + "    <generator class=\"opennlp.tools.util.featuregen.TokenFeatureGeneratorFactory\"/>\n"
        + "    <generator class=\""
        + "opennlp.tools.util.featuregen.EmojiAnnotationFeatureGeneratorFactory\"/>\n"
        + "  </generator>\n"
        + "</featureGenerators>\n";
    final AdaptiveFeatureGenerator generator = GeneratorFactory.create(
        new ByteArrayInputStream(descriptor.getBytes(StandardCharsets.UTF_8)), null);
    final List<String> features = new ArrayList<>();
    generator.createFeatures(features, new String[] {cp(0x1F622)}, 0, null);
    assertTrue(features.contains("w=" + cp(0x1F622)), "Missing token feature in: " + features);
    assertTrue(features.contains("emojiSentiment=-2"), "Missing emoji feature in: " + features);
    assertTrue(features.contains("emojiType=FACE"), "Missing emoji feature in: " + features);
  }

  @Test
  void isStatelessUnderAdaptiveDataCalls() {
    final AdaptiveFeatureGenerator generator = new EmojiAnnotationFeatureGenerator();
    generator.updateAdaptiveData(new String[] {"a"}, new String[] {"o"});
    generator.clearAdaptiveData();
    final List<String> features = new ArrayList<>();
    generator.createFeatures(features, new String[] {cp(0x1F642)}, 0, null);
    assertEquals(3, features.size());
  }

  @Test
  void featurePrefixesMatchTheAnnotationAttributes() {
    // Guards the feature vocabulary: a rename here silently orphans trained models, so the names
    // are pinned against the annotation attribute keys they project.
    assertEquals("sentiment", EmojiAnnotation.SENTIMENT);
    assertEquals("entityType", EmojiAnnotation.ENTITY_TYPE);
    assertEquals("category", EmojiAnnotation.CATEGORY);
    assertEquals("isoRegion", EmojiAnnotation.ISO_REGION);
  }
  @Test
  void nullArgumentsFailLoud() {
    final AdaptiveFeatureGenerator generator = new EmojiAnnotationFeatureGenerator();
    final IllegalArgumentException nullFeatures = assertThrows(IllegalArgumentException.class,
        () -> generator.createFeatures(null, new String[] {"x"}, 0, null));
    assertEquals("Features must not be null", nullFeatures.getMessage());
    final IllegalArgumentException nullTokens = assertThrows(IllegalArgumentException.class,
        () -> generator.createFeatures(new ArrayList<>(), null, 0, null));
    assertEquals("Tokens must not be null", nullTokens.getMessage());
  }
}
