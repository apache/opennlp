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
package opennlp.tools.util.normalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static opennlp.tools.util.normalizer.NormalizerTestUtil.cp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmojiAnnotatorTest {

  private static final String GERMAN_FLAG = cp(0x1F1E9, 0x1F1EA);

  @Test
  void annotatesATermFromItsOriginalLayer() {
    // The end-to-end path the surface exists for: UAX #29 segmentation keeps the pictograph (with
    // its presentation selector) as one token, the fold dimension rewrites it to ASCII for
    // matching, and the annotation still resolves because it reads the original layer.
    final TermAnalyzer analyzer = TermAnalyzer.builder().emojiFold().build();
    final List<Term> terms = analyzer.analyze("I " + cp(0x2764, 0xFE0F) + " pizza");
    final Term heart = terms.get(1);
    assertEquals(cp(0x2764, 0xFE0F), heart.original());
    assertEquals("<3", heart.normalized()); // the fold layer has rewritten the pictograph away
    final EmojiAnnotation annotation = new EmojiAnnotator().annotate(heart).orElseThrow();
    assertEquals("red heart", annotation.name().orElseThrow());
    assertEquals(2, annotation.sentiment().orElseThrow());
    assertEquals(EmojiEntityType.HEART, annotation.entityType().orElseThrow());
    assertEquals(EmojiCategory.SMILEYS_AND_EMOTION, annotation.category().orElseThrow());
    assertEquals(Optional.empty(), annotation.isoRegion());
  }

  @Test
  void aPureBundledHitReturnsTheCachedRecord() {
    // No join, no derived facts: the annotator must not copy the record per token.
    final EmojiAnnotation direct = EmojiAnnotations.lookup(cp(0x1F642)).orElseThrow();
    assertSame(direct, new EmojiAnnotator().annotate(cp(0x1F642)).orElseThrow());
  }

  @Test
  void annotatesAFlagTermWithDerivedFacts() {
    // A flag has no bundled rows; entity type, category, and the region are all derived from the
    // sequence itself, provenance-tagged with the UTS #51 mechanism that defines them.
    final TermAnalyzer analyzer = TermAnalyzer.builder().build();
    final List<Term> terms = analyzer.analyze("Berlin " + GERMAN_FLAG);
    final EmojiAnnotation annotation =
        new EmojiAnnotator().annotate(terms.get(1)).orElseThrow();
    assertEquals(GERMAN_FLAG, annotation.symbol());
    assertEquals("DE", annotation.isoRegion().orElseThrow());
    assertEquals(EmojiEntityType.FLAG, annotation.entityType().orElseThrow());
    assertEquals(EmojiCategory.FLAGS, annotation.category().orElseThrow());
    assertEquals(Optional.empty(), annotation.name());
    assertEquals("UTS51:flag-sequence",
        annotation.attribute(EmojiAnnotation.ISO_REGION).orElseThrow().source());
  }

  @Test
  void annotatesASubdivisionFlag() {
    final StringBuilder england = new StringBuilder(cp(0x1F3F4));
    for (final char c : "gbeng".toCharArray()) {
      england.appendCodePoint(0xE0000 + c);
    }
    england.appendCodePoint(0xE007F);
    final EmojiAnnotation annotation =
        new EmojiAnnotator().annotate(england).orElseThrow();
    assertEquals("GB-ENG", annotation.isoRegion().orElseThrow());
    assertEquals("UTS51:tag-sequence",
        annotation.attribute(EmojiAnnotation.ISO_REGION).orElseThrow().source());
  }

  static Stream<String> nonAnnotatedTokens() {
    // A lone regional indicator in damaged text yields no annotation, never an exception; the
    // fail-loud contract lives on EmojiFlags.isoRegion for direct callers. An unmapped pictograph
    // is not annotated either.
    return Stream.of("word", "", ":-)", cp(0x1F1E9), cp(0x1F9F8));
  }

  @ParameterizedTest
  @MethodSource("nonAnnotatedTokens")
  void annotationIsTotalOverArbitraryTokens(String token) {
    assertEquals(Optional.empty(), new EmojiAnnotator().annotate(token));
  }

  @Test
  void joinedFactsMergeIntoTheRecord() {
    final List<String> calls = new ArrayList<>();
    final EmojiAnnotator annotator = new EmojiAnnotator((symbol, isoRegion) -> {
      calls.add(symbol + "/" + isoRegion);
      if ("DE".equals(isoRegion)) {
        return Map.of("geonamesId",
            new EmojiAnnotation.Value("2921044", "user:gazetteer", "join test"));
      }
      return Map.of();
    });
    final EmojiAnnotation flag = annotator.annotate(GERMAN_FLAG).orElseThrow();
    assertEquals("2921044", flag.attribute("geonamesId").orElseThrow().value());
    assertEquals("user:gazetteer", flag.attribute("geonamesId").orElseThrow().source());
    assertEquals("DE", flag.isoRegion().orElseThrow());
    // A bundled non-flag symbol is joined with a null region.
    final EmojiAnnotation heart = annotator.annotate(cp(0x2764)).orElseThrow();
    assertEquals(Optional.empty(), heart.attribute("geonamesId"));
    assertEquals("red heart", heart.name().orElseThrow());
    // The join never runs for unannotated tokens: it augments records, it does not create them.
    assertEquals(Optional.empty(), annotator.annotate("word"));
    assertEquals(List.of(GERMAN_FLAG + "/DE", cp(0x2764) + "/null"), calls);
  }

  @Test
  void aContractViolatingJoinFailsLoud() {
    final EmojiAnnotator returnsNull = new EmojiAnnotator((symbol, isoRegion) -> null);
    assertThrows(IllegalStateException.class, () -> returnsNull.annotate(GERMAN_FLAG));
    // Colliding with a bundled attribute.
    final EmojiAnnotator collidesBundled = new EmojiAnnotator((symbol, isoRegion) ->
        Map.of(EmojiAnnotation.NAME, new EmojiAnnotation.Value("x", "user:gazetteer", "")));
    assertThrows(IllegalStateException.class, () -> collidesBundled.annotate(cp(0x1F642)));
    // Colliding with a derived attribute.
    final EmojiAnnotator collidesDerived = new EmojiAnnotator((symbol, isoRegion) ->
        Map.of(EmojiAnnotation.ISO_REGION, new EmojiAnnotation.Value("XX", "user:gazetteer", "")));
    assertThrows(IllegalStateException.class, () -> collidesDerived.annotate(GERMAN_FLAG));
  }

  @Test
  void argumentsAreValidated() {
    assertThrows(IllegalArgumentException.class, () -> new EmojiAnnotator(null));
    final EmojiAnnotator annotator = new EmojiAnnotator();
    assertThrows(IllegalArgumentException.class, () -> annotator.annotate((Term) null));
    assertThrows(IllegalArgumentException.class, () -> annotator.annotate((CharSequence) null));
  }

  @Test
  void flagTokensSurviveSegmentation() {
    // Adjacent flags segment into their pairs under UAX #29 (WB15/WB16), so per-token annotation
    // sees each flag whole; this pins the assumption the bulk surface relies on.
    final TermAnalyzer analyzer = TermAnalyzer.builder().build();
    final List<Term> terms = analyzer.analyze(GERMAN_FLAG + cp(0x1F1EB, 0x1F1F7));
    assertEquals(2, terms.size());
    final EmojiAnnotator annotator = new EmojiAnnotator();
    assertEquals("DE", annotator.annotate(terms.get(0)).orElseThrow().isoRegion().orElseThrow());
    assertEquals("FR", annotator.annotate(terms.get(1)).orElseThrow().isoRegion().orElseThrow());
  }

  @Test
  void mergedFlagRecordsKeepEveryProvenanceTag() {
    // The record store contract: every value, bundled or derived, carries its own source.
    final EmojiAnnotation annotation = new EmojiAnnotator().annotate(GERMAN_FLAG).orElseThrow();
    for (final Map.Entry<String, EmojiAnnotation.Value> entry :
        annotation.attributes().entrySet()) {
      assertTrue(!entry.getValue().source().isEmpty(), "Empty source for " + entry.getKey());
    }
    assertEquals(3, annotation.attributes().size()); // isoRegion, entityType, category
  }
}
