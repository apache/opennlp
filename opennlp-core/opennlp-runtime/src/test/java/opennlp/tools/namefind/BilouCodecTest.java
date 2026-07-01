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

package opennlp.tools.namefind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

/**
 * This is the test class for {@link BilouCodec}.
 */
public class BilouCodecTest {

  private static final BilouCodec codec = new BilouCodec();

  private static final String A_TYPE = "atype";
  private static final String A_START = A_TYPE + "-" + BilouCodec.START;
  private static final String A_CONTINUE = A_TYPE + "-" + BilouCodec.CONTINUE;
  private static final String A_LAST = A_TYPE + "-" + BilouCodec.LAST;
  private static final String A_UNIT = A_TYPE + "-" + BilouCodec.UNIT;

  private static final String B_TYPE = "btype";
  private static final String B_START = B_TYPE + "-" + BilouCodec.START;
  private static final String B_CONTINUE = B_TYPE + "-" + BilouCodec.CONTINUE;
  private static final String B_LAST = B_TYPE + "-" + BilouCodec.LAST;
  private static final String B_UNIT = B_TYPE + "-" + BilouCodec.UNIT;

  private static final String C_TYPE = "ctype";
  private static final String C_START = C_TYPE + "-" + BilouCodec.START;
  private static final String C_CONTINUE = C_TYPE + "-" + BilouCodec.CONTINUE;
  private static final String C_LAST = C_TYPE + "-" + BilouCodec.LAST;
  private static final String C_UNIT = C_TYPE + "-" + BilouCodec.UNIT;

  private static final String OTHER = BilouCodec.OTHER;

  @Test
  void testEncodeNoNames() {
    NameSample nameSample = new NameSample("Once upon a time.".split(" "), new Span[] {}, true);
    String[] expected = new String[] {OTHER, OTHER, OTHER, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, acutal, "Only 'Other' is expected.");
  }

  @Test
  void testEncodeSingleUnitTokenSpan() {
    String[] sentence = "I called Julie again.".split(" ");
    Span[] singleSpan = new Span[] {new Span(2, 3, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, singleSpan, true);
    String[] expected = new String[] {OTHER, OTHER, A_UNIT, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, acutal,
        "'Julie' should be 'unit' only, the rest should be 'other'.");
  }

  @Test
  void testEncodeDoubleTokenSpan() {
    String[] sentence = "I saw Stefanie Schmidt today.".split(" ");
    Span[] singleSpan = new Span[] {new Span(2, 4, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, singleSpan, true);
    String[] expected = new String[] {OTHER, OTHER, A_START, A_LAST, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, acutal, "'Stefanie' should be 'start' only, 'Schmidt' is 'last' " +
        "and the rest should be 'other'.");
  }

  @Test
  void testEncodeTripleTokenSpan() {
    String[] sentence = "Secretary - General Anders Fogh Rasmussen is from Denmark.".split(" ");
    Span[] singleSpan = new Span[] {new Span(3, 6, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, singleSpan, true);
    String[] expected = new String[] {OTHER, OTHER, OTHER, A_START, A_CONTINUE,
        A_LAST, OTHER, OTHER, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, acutal, "'Anders' should be 'start' only, 'Fogh' is 'inside', " +
        "'Rasmussen' is 'last' and the rest should be 'other'.");
  }

  @Test
  void testEncodeAdjacentUnitSpans() {
    String[] sentence = "word PersonA PersonB word".split(" ");
    Span[] singleSpan = new Span[] {new Span(1, 2, A_TYPE), new Span(2, 3, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, singleSpan, true);
    String[] expected = new String[] {OTHER, A_UNIT, A_UNIT, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, acutal, "Both PersonA and PersonB are 'unit' tags");
  }

  @Test
  void testCreateSequenceValidator() {
    Assertions.assertInstanceOf(BilouNameFinderSequenceValidator.class, codec.createSequenceValidator());
  }

  @Test
  void testDecodeEmpty() {
    Span[] expected = new Span[] {};
    Span[] actual = codec.decode(new ArrayList<>());
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Unit, Other
   */
  @Test
  void testDecodeSingletonFirst() {

    List<String> encoded = Arrays.asList(A_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(0, 1, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Unit, Unit, Other
   */
  @Test
  void testDecodeAdjacentSingletonFirst() {
    List<String> encoded = Arrays.asList(A_UNIT, A_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(0, 1, A_TYPE), new Span(1, 2, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Start, Last, Other
   */
  @Test
  void testDecodePairFirst() {
    List<String> encoded = Arrays.asList(A_START, A_LAST, OTHER);
    Span[] expected = new Span[] {new Span(0, 2, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Start, Continue, Last, Other
   */
  @Test
  void testDecodeTripletFirst() {
    List<String> encoded = Arrays.asList(A_START, A_CONTINUE, A_LAST, OTHER);
    Span[] expected = new Span[] {new Span(0, 3, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Start, Continue, Continue, Last, Other
   */
  @Test
  void testDecodeTripletContinuationFirst() {
    List<String> encoded = Arrays.asList(A_START, A_CONTINUE, A_CONTINUE,
        A_LAST, OTHER);
    Span[] expected = new Span[] {new Span(0, 4, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Start, Last, Unit, Other
   */
  @Test
  void testDecodeAdjacentPairSingleton() {
    List<String> encoded = Arrays.asList(A_START, A_LAST, A_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(0, 2, A_TYPE),
        new Span(2, 3, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Other, Unit, Other
   */
  @Test
  void testDecodeOtherFirst() {
    List<String> encoded = Arrays.asList(OTHER, A_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(1, 2, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Other, A-Start, A-Continue, A-Last, Other, B-Start, B-Last, Other, C-Unit, Other
   */
  @Test
  void testDecodeMultiClass() {
    List<String> encoded = Arrays.asList(OTHER, A_START, A_CONTINUE, A_LAST, OTHER,
        B_START, B_LAST, OTHER, C_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(1, 4, A_TYPE),
        new Span(5, 7, B_TYPE), new Span(8, 9, C_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }


  @Test
  void testCompatibilityEmpty() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {}));
  }

  /*
   * Singles and singles in combination with other valid type (unit/start+last)
   */

  /**
   * B-Start => Fail
   * A-Unit, B-Start => Fail
   * A-Start, A-Last, B-Start => Fail
   */
  @Test
  void testCompatibilitySinglesStart() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_START}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START}));
  }

  /**
   * B-Continue => Fail
   * A-Unit, B-Continue => Fail
   * A-Start, A-Last, B-Continue => Fail
   */
  @Test
  void testCompatibilitySinglesContinue() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_CONTINUE}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_CONTINUE}));
  }

  /**
   * B-Last => Fail
   * A-Unit, B-Last => Fail
   * A-Start, A-Last, B-Last => Fail
   */
  @Test
  void testCompatibilitySinglesLast() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_LAST}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_LAST}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_LAST}));
  }

  /**
   * Other => Fail
   * A-Unit, Other => Pass
   * A-Start, A-Last, Other => Pass
   */
  @Test
  void testCompatibilitySinglesOther() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {OTHER}));
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT, OTHER}));
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, OTHER}));
  }

  /**
   * B-Unit => Pass
   * A-Unit, B-Unit => Pass
   * A-Start, A-Last, B-Unit => Pass
   */
  @Test
  void testCompatibilitySinglesUnit() {
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {B_UNIT}));
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT, B_UNIT}));
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_UNIT}));
  }

  /**
   * Doubles and doubles in combination with other valid type (unit/start+last)
   * <p>
   * B-Start, B-Continue => Fail
   * A-Unit, B-Start, B-Continue => Fail
   * A-Start, A-Last, B-Start, B-Continue => Fail
   */
  @Test
  void testCompatibilityStartContinue() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_START, B_CONTINUE}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START, B_CONTINUE}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START, B_CONTINUE}));
  }

  /**
   * B-Start, B-Last => Pass
   * A-Unit, B-Start, B-Last => Pass
   * A-Start, A-Last, B-Start, B-Last => Pass
   */
  @Test
  void testCompatibilityStartLast() {
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {B_START, B_LAST}));
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START, B_LAST}));
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START, B_LAST}));
  }

  /**
   * B-Start, Other => Fail
   * A-Unit, B-Start, Other => Fail
   * A-Start, A-Last, B-Start, Other => Fail
   */
  @Test
  void testCompatibilityStartOther() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_START, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START, OTHER}));
  }

  /**
   * B-Start, B-Unit => Fail
   * A-Unit, B-Start, B-Unit => Fail
   * A-Start, A-Last, B-Start, B-Unit => Fail
   */
  @Test
  void testCompatibilityStartUnit() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_START, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START, B_UNIT}));
  }

  /**
   * B-Continue, C-Last => Fail
   * A-Unit, B-Continue, C-Last => Fail
   * A-Start, A-Last, B-Continue, B-Last => Fail
   */
  @Test
  void testCompatibilityContinueLast() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE, B_LAST}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_CONTINUE, B_LAST}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_CONTINUE, B_LAST}));
  }

  /**
   * B-Continue, Other => Fail
   * A-Unit, B-Continue, Other => Fail
   * A-Start, A-Last, B-Continue, Other => Fail
   */
  @Test
  void testCompatibilityContinueOther() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_CONTINUE, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_CONTINUE, OTHER}));
  }

  /**
   * B-Continue, B-Unit => Fail
   * A-Unit, B-Continue, B-Unit => Fail
   * A-Start, A-Last, B-Continue, B-Unit => Fail
   */
  @Test
  void testCompatibilityContinueUnit() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_CONTINUE, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_CONTINUE, B_UNIT}));
  }

  /**
   * B-Last, Other => Fail
   * A-Unit, B-Last, Other => Fail
   * A-Start, A-Last, B-Last, Other => Fail
   */
  @Test
  void testCompatibilityLastOther() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_LAST, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_LAST, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_LAST, OTHER}));
  }

  /**
   * B-Last, B-Unit => Fail
   * A-Unit, B-Last, B-Unit => Fail
   * A-Start, A-Last, B-Last, B-Unit => Fail
   */
  @Test
  void testCompatibilityLastUnit() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_LAST, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_LAST, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_LAST, B_UNIT}));
  }

  /**
   * Other, B-Unit => Pass
   * A-Unit, Other, B-Unit => Pass
   * A-Start, A-Last, Other, B-Unit => Pass
   */
  @Test
  void testCompatibilityOtherUnit() {
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {OTHER, B_UNIT}));
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT, OTHER, B_UNIT}));
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, OTHER, B_UNIT}));
  }

  /**
   * Triples and triples in combination with other valid type (unit/start+last)
   * <p>
   * B-Start, B-Continue, B-Last => Pass
   * A-Unit, B-Start, B-Continue, B-Last => Pass
   * A-Start, A-Last, B-Start, B-Continue, B-Last => Pass
   */
  @Test
  void testCompatibilityStartContinueLast() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_LAST}));
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_LAST}));
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_LAST}));
  }

  /**
   * B-Start, B-Continue, Other => Fail
   * A-Unit, B-Start, B-Continue, Other => Fail
   * A-Start, A-Last, B-Start, B-Continue, Other => Fail
   */
  @Test
  void testCompatibilityStartContinueOther() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, OTHER}));
  }

  /**
   * B-Start, B-Continue, B-Unit => Fail
   * A-Unit, B-Start, B-Continue, B-Unit => Fail
   * A-Start, A-Last, B-Start, B-Continue, B-Unit => Fail
   */
  @Test
  void testCompatibilityStartContinueUnit() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_UNIT}));
  }

  /**
   * B-Continue, B-Last, Other => Fail
   * A-Unit, B-Continue, B-Last, Other => Fail
   * A-Start, A-Last, B-Continue, B-Last, Other => Fail
   */
  @Test
  void testCompatibilityContinueLastOther() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_CONTINUE, B_LAST, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_CONTINUE, B_LAST, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_CONTINUE, B_LAST, OTHER}));
  }

  /**
   * B-Continue, B-Last, B-Unit => Fail
   * A-Unit, B-Continue, B-Last, B_Unit => Fail
   * A-Start, A-Last, B-Continue, B-Last, B_Unit => Fail
   */
  @Test
  void testCompatibilityContinueLastUnit() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_CONTINUE, B_LAST, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_CONTINUE, B_LAST, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_CONTINUE, B_LAST, B_UNIT}));
  }

  /**
   * B-Last, Other, B-Unit => Fail
   * A-Unit, B-Continue, B-Last, B_Unit => Fail
   * A-Start, A-Last, B-Continue, B-Last, B_Unit => Fail
   */
  @Test
  void testCompatibilityLastOtherUnit() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_LAST, OTHER, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_LAST, OTHER, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_LAST, OTHER, B_UNIT}));
  }

  /**
   * Quadruples and quadruple in combination of unit/start+last
   * <p>
   * B-Start, B-Continue, B-Last, Other => Pass
   * A-Unit, B-Start, B-Continue, B-Last, Other => Pass
   * A-Start, A-Last, B-Start, B-Continue, B-Last, Other => Pass
   */
  @Test
  void testCompatibilityStartContinueLastOther() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_LAST, OTHER}));
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_LAST, OTHER}));
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_LAST, OTHER}));
  }

  /**
   * B-Start, B-Continue, B-Last, B-Unit => Pass
   * A-Unit, B-Start, B-Continue, B-Last, B-Unit => Pass
   * A-Start, A-Last, B-Start, B-Continue, B-Last, B-Unit => Pass
   */
  @Test
  void testCompatibilityStartContinueLastUnit() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_LAST, B_UNIT}));
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_LAST, B_UNIT}));
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_LAST, B_UNIT}));
  }


  /**
   * B-Continue, B-Last, Other, B-Unit => Fail
   * A-Unit, B-Continue, B-Last, Other, B-Unit => Fail
   * A-Start, A-Last, B-Continue, B-Last, Other, B-Unit => Fail
   */
  @Test
  void testCompatibilityContinueLastOtherUnit() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_CONTINUE, B_LAST, OTHER, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
  }

  /**
   * Quintuple
   * <p>
   * B-Start, B-Continue, B-Last, Other, B-Unit => Pass
   * A-Unit, B-Start, B-Continue, B-Last, Other, B-Unit => Pass
   * A-Staart, A-Last, B-Start, B-Continue, B-Last, Other, B-Unit => Pass
   */
  @Test
  void testCompatibilityUnitOther() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
  }

  /**
   * Multiclass
   */
  @Test
  void testCompatibilityMultiClass() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_UNIT, A_CONTINUE, A_LAST, A_UNIT,
            B_START, B_LAST, A_START, C_UNIT, OTHER}));
  }

  /**
   * Bad combinations
   */
  @Test
  void testCompatibilityBadTag() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_CONTINUE, OTHER, "BAD"}));
  }

  @Test
  void testCompatibilityWrongClass() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, B_LAST, OTHER}));
  }

  // ---- Additional tests to improve mutation coverage ----

  /**
   * Decode with LAST tag appearing without prior START (dangling LAST).
   * The guard (start != -1) should prevent creating a span.
   */
  @Test
  void testDecodeDanglingLast() {
    List<String> encoded = Arrays.asList(A_LAST, OTHER);
    Span[] actual = codec.decode(encoded);
    Assertions.assertEquals(0, actual.length, "Dangling LAST without START should produce no spans");
  }

  /**
   * Decode with LAST at position 0 without prior START.
   */
  @Test
  void testDecodeLastAtStart() {
    List<String> encoded = Arrays.asList(A_LAST);
    Span[] actual = codec.decode(encoded);
    Assertions.assertEquals(0, actual.length, "LAST at position 0 without START should produce no spans");
  }

  /**
   * Decode with multiple dangling LAST tags interspersed.
   */
  @Test
  void testDecodeMultipleDanglingLast() {
    List<String> encoded = Arrays.asList(A_LAST, OTHER, B_LAST, A_UNIT);
    Span[] actual = codec.decode(encoded);
    Span[] expected = new Span[] {new Span(3, 4, A_TYPE)};
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Decode with START, LAST pair to verify exact span boundaries (end + 1).
   */
  @Test
  void testDecodeStartLastBoundaries() {
    List<String> encoded = Arrays.asList(OTHER, A_START, A_LAST, OTHER);
    Span[] actual = codec.decode(encoded);
    Span[] expected = new Span[] {new Span(1, 3, A_TYPE)};
    Assertions.assertArrayEquals(expected, actual, "Span should cover positions 1 to 3");
  }

  /**
   * Encode span with null type - single token (UNIT path).
   */
  @Test
  void testEncodeNullTypeUnit() {
    Span[] spans = new Span[] {new Span(1, 2, null)};
    String[] actual = codec.encode(spans, 3);
    String[] expected = new String[] {OTHER, "default-unit", OTHER};
    Assertions.assertArrayEquals(expected, actual, "Null type single-token span should use default-unit");
  }

  /**
   * Encode span with null type - two tokens (START + LAST, no CONTINUE).
   */
  @Test
  void testEncodeNullTypeTwoTokens() {
    Span[] spans = new Span[] {new Span(0, 2, null)};
    String[] actual = codec.encode(spans, 4);
    String[] expected = new String[] {"default-start", "default-last", OTHER, OTHER};
    Assertions.assertArrayEquals(expected, actual,
        "Null type two-token span should use default-start and default-last");
  }

  /**
   * Encode span with null type - four tokens (START + CONTINUE*2 + LAST).
   * Exercises the CONTINUE inner loop.
   */
  @Test
  void testEncodeNullTypeLong() {
    Span[] spans = new Span[] {new Span(0, 4, null)};
    String[] actual = codec.encode(spans, 6);
    String[] expected = new String[] {"default-start", "default-cont", "default-cont",
        "default-last", OTHER, OTHER};
    Assertions.assertArrayEquals(expected, actual,
        "Null type long span should use default-start, default-cont, default-last");
  }

  /**
   * areOutcomesCompatible: unit-only outcomes should be compatible.
   * Exercises the UNIT parsing path.
   */
  @Test
  void testCompatibilityUnitOnly() {
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT}),
        "Unit-only outcomes should be compatible");
  }

  /**
   * areOutcomesCompatible: multiple unit-only outcomes should be compatible.
   */
  @Test
  void testCompatibilityMultipleUnitsOnly() {
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT, B_UNIT, C_UNIT}),
        "Multiple unit-only outcomes should be compatible");
  }

  /**
   * areOutcomesCompatible: CONTINUE without matching START should fail.
   * Exercises the cont validation (start.contains check).
   */
  @Test
  void testCompatibilityContinueWithoutStart() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE, B_LAST, B_UNIT}),
        "CONTINUE without matching START should be incompatible");
  }

  /**
   * areOutcomesCompatible: CONTINUE without matching LAST should fail.
   * Exercises the cont validation (last.contains check).
   */
  @Test
  void testCompatibilityContinueWithoutLast() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_START, B_CONTINUE, B_UNIT}),
        "CONTINUE without matching LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: CONTINUE without matching START or LAST should fail.
   * Both contains checks should return false.
   */
  @Test
  void testCompatibilityContinueWithoutStartOrLast() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE, B_LAST, B_UNIT}),
        "CONTINUE without matching START or LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: UNIT + START without matching LAST should fail.
   * Exercises the start validation (last.contains check).
   */
  @Test
  void testCompatibilityUnitStartNoLast() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, A_START}),
        "UNIT with START but no matching LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: UNIT + START + CONTINUE without LAST should fail.
   * Exercises both start and cont validation paths.
   */
  @Test
  void testCompatibilityUnitStartContinueNoLast() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, A_START, A_CONTINUE}),
        "UNIT with START and CONTINUE but no LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: only START without LAST should fail.
   */
  @Test
  void testCompatibilityStartOnly() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START}),
        "START without matching LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: only CONTINUE should fail.
   */
  @Test
  void testCompatibilityContinueOnly() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_CONTINUE}),
        "CONTINUE without matching START or LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: only LAST without START should fail.
   */
  @Test
  void testCompatibilityLastOnly() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_LAST}),
        "LAST without matching START should be incompatible");
  }

  /**
   * areOutcomesCompatible: START + LAST + CONTINUE for different type should fail.
   * The CONTINUE type has no matching START or LAST.
   */
  @Test
  void testCompatibilityStartLastOtherContinue() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_CONTINUE}),
        "CONTINUE for different type without matching START/LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: UNIT + START + LAST + CONTINUE for different type should fail.
   */
  @Test
  void testCompatibilityUnitStartLastOtherContinue() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, A_START, A_LAST, B_CONTINUE}),
        "CONTINUE for different type should be incompatible even with other valid types");
  }

  /**
   * areOutcomesCompatible: only OTHER should fail (no start or unit).
   */
  @Test
  void testCompatibilityOtherOnly() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {OTHER}),
        "Only OTHER outcomes should be incompatible (no start or unit)");
  }

  /**
   * areOutcomesCompatible: multiple OTHER should fail.
   */
  @Test
  void testCompatibilityMultipleOtherOnly() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {OTHER, OTHER, OTHER}),
        "Multiple OTHER outcomes should be incompatible (no start or unit)");
  }

  /**
   * areOutcomesCompatible: empty array should fail.
   */
  @Test
  void testCompatibilityEmptyArray() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {}),
        "Empty outcomes array should be incompatible");
  }

  /**
   * Decode with only OTHER tags should produce no spans.
   */
  @Test
  void testDecodeOnlyOther() {
    List<String> encoded = Arrays.asList(OTHER, OTHER, OTHER);
    Span[] actual = codec.decode(encoded);
    Assertions.assertEquals(0, actual.length, "Only OTHER tags should produce no spans");
  }

  /**
   * Decode with only CONTINUE tags should produce no spans.
   */
  @Test
  void testDecodeOnlyContinue() {
    List<String> encoded = Arrays.asList(A_CONTINUE, A_CONTINUE);
    Span[] actual = codec.decode(encoded);
    Assertions.assertEquals(0, actual.length, "Only CONTINUE tags should produce no spans");
  }

  /**
   * Decode with START but no LAST should produce no spans.
   */
  @Test
  void testDecodeStartWithoutLast() {
    List<String> encoded = Arrays.asList(A_START, A_CONTINUE, OTHER);
    Span[] actual = codec.decode(encoded);
    Assertions.assertEquals(0, actual.length, "START without LAST should produce no spans");
  }

  /**
   * Decode with START, CONTINUE, LAST at the end of the list.
   */
  @Test
  void testDecodeSpanAtEnd() {
    List<String> encoded = Arrays.asList(OTHER, A_START, A_CONTINUE, A_LAST);
    Span[] actual = codec.decode(encoded);
    Span[] expected = new Span[] {new Span(1, 4, A_TYPE)};
    Assertions.assertArrayEquals(expected, actual, "Span at end should be decoded correctly");
  }

  /**
   * Decode with multiple consecutive spans.
   */
  @Test
  void testDecodeMultipleConsecutiveSpans() {
    List<String> encoded = Arrays.asList(A_START, A_LAST, B_START, B_LAST);
    Span[] actual = codec.decode(encoded);
    Span[] expected = new Span[] {new Span(0, 2, A_TYPE), new Span(2, 4, B_TYPE)};
    Assertions.assertArrayEquals(expected, actual, "Multiple consecutive spans should be decoded correctly");
  }

  /**
   * Decode with four-token span (START, CONTINUE, CONTINUE, LAST).
   */
  @Test
  void testDecodeFourTokenSpan() {
    List<String> encoded = Arrays.asList(A_START, A_CONTINUE, A_CONTINUE, A_LAST);
    Span[] actual = codec.decode(encoded);
    Span[] expected = new Span[] {new Span(0, 4, A_TYPE)};
    Assertions.assertArrayEquals(expected, actual, "Four-token span should be decoded correctly");
  }

  /**
   * Encode with null type and typed spans mixed.
   */
  @Test
  void testEncodeMixedNullAndTypedTypes() {
    Span[] spans = new Span[] {new Span(0, 2, null), new Span(3, 4, A_TYPE)};
    String[] actual = codec.encode(spans, 5);
    String[] expected = new String[] {"default-start", "default-last", OTHER, A_UNIT, OTHER};
    Assertions.assertArrayEquals(expected, actual, "Mixed null and typed spans should encode correctly");
  }

  /**
   * Encode with five-token span (START + CONTINUE*3 + LAST).
   */
  @Test
  void testEncodeFiveTokenSpan() {
    Span[] spans = new Span[] {new Span(0, 5, A_TYPE)};
    String[] actual = codec.encode(spans, 7);
    String[] expected = new String[] {A_START, A_CONTINUE, A_CONTINUE, A_CONTINUE, A_LAST, OTHER, OTHER};
    Assertions.assertArrayEquals(expected, actual, "Five-token span should have three CONTINUE tags");
  }

  /**
   * areOutcomesCompatible: default-prefixed outcomes should work.
   */
  @Test
  void testCompatibilityDefaultPrefix() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {"default-start", "default-last", OTHER}),
        "Default-prefixed START and LAST should be compatible");
  }

  /**
   * areOutcomesCompatible: default-prefixed with CONTINUE should work.
   */
  @Test
  void testCompatibilityDefaultPrefixWithContinue() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {"default-start", "default-cont", "default-last", OTHER}),
        "Default-prefixed START, CONTINUE, LAST should be compatible");
  }

  /**
   * areOutcomesCompatible: default-prefixed START without LAST should fail.
   */
  @Test
  void testCompatibilityDefaultPrefixStartNoLast() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {"default-start", A_UNIT}),
        "Default-prefixed START without matching LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: mixed default and typed outcomes with mismatch.
   */
  @Test
  void testCompatibilityMixedDefaultTypedMismatch() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {"default-start", "default-last", A_CONTINUE}),
        "Typed CONTINUE without matching typed START/LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: three types all valid.
   */
  @Test
  void testCompatibilityThreeTypesValid() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_LAST, C_START, C_LAST, OTHER}),
        "Three valid types should be compatible");
  }

  /**
   * areOutcomesCompatible: three types with one missing LAST.
   */
  @Test
  void testCompatibilityThreeTypesOneMissingLast() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_LAST, C_START, OTHER}),
        "Three types with one missing LAST should be incompatible");
  }

  /**
   * areOutcomesCompatible: three types with one having CONTINUE but no START.
   */
  @Test
  void testCompatibilityThreeTypesOneContinueNoStart() {
    Assertions.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_LAST, C_CONTINUE, C_LAST, OTHER}),
        "Three types with one CONTINUE missing START should be incompatible");
  }

}
