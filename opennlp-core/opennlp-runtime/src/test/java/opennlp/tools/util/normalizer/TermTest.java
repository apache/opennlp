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

import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the public API of {@link Term}, the layered per-token view produced by
 * {@link TermAnalyzer}: {@link Term#original()}, {@link Term#normalized()}, {@link Term#span()},
 * {@link Term#at(Dimension)}, and {@link Term#peel()}.
 */
public class TermTest {

  @Test
  void originalIsTheSourceOfTruth() {
    final Term term = TermAnalyzer.builder().caseFold().build().analyze("Hello").get(0);
    assertEquals("Hello", term.original());
  }

  @Test
  void normalizedIsTheFinalConfiguredDimension() {
    final Term term = TermAnalyzer.builder().caseFold().build().analyze("HELLO").get(0);
    assertEquals("hello", term.normalized());
  }

  @Test
  void normalizedEqualsOriginalWhenNoDimensionsConfigured() {
    final Term term = TermAnalyzer.builder().build().analyze("Hello").get(0);
    assertEquals("Hello", term.original());
    assertEquals("Hello", term.normalized());
  }

  @Test
  void spanIndexesTheAnalyzedText() {
    final List<Term> terms = TermAnalyzer.builder().build().analyze("The Cats");
    assertEquals(new Span(0, 3), terms.get(0).span());
    assertEquals(new Span(4, 8), terms.get(1).span());
  }

  @Test
  void spanIsNullForPreTokenizedInput() {
    final Term term = TermAnalyzer.builder().build()
        .analyze(new String[] {"Cat"}, new String[] {"NN"}).get(0);
    assertNull(term.span());
  }

  @Test
  void atProjectsToAnUnconfiguredDimension() {
    final Term term = TermAnalyzer.builder().build().analyze("HELLO").get(0);
    assertEquals("HELLO", term.original());
    assertEquals("hello", term.at(Dimension.CASE_FOLD));
  }

  @Test
  void atIsMemoized() {
    final Term term = TermAnalyzer.builder().build().analyze("HELLO").get(0);
    assertSame(term.at(Dimension.CASE_FOLD), term.at(Dimension.CASE_FOLD));
  }

  @Test
  void atRejectsNullDimension() {
    final Term term = TermAnalyzer.builder().build().analyze("x").get(0);
    assertThrows(IllegalArgumentException.class, () -> term.at(null));
  }

  @Test
  void atThrowsWhenDimensionNeedsAMissingEngine() {
    final Term term = TermAnalyzer.builder().build().analyze("running").get(0);
    assertThrows(IllegalStateException.class, () -> term.at(Dimension.STEM));
  }

  @Test
  void peelReturnsTheLayerBelowTheFinalDimension() {
    final Term term = TermAnalyzer.builder().caseFold().stem(new PorterStemmer())
        .build().analyze("Running").get(0);
    assertEquals("run", term.normalized()); // STEM is the final dimension
    assertEquals("running", term.peel());   // the case-folded form, before stemming
  }

  @Test
  void peelEqualsOriginalWithAtMostOneDimension() {
    final Term term = TermAnalyzer.builder().caseFold().build().analyze("Hello").get(0);
    assertEquals(term.original(), term.peel());
  }
}
