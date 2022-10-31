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

import org.junit.Assert;
import org.junit.Test;

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
  private static final String C_UNIT = C_TYPE + "-" + BilouCodec.UNIT;

  private static final String OTHER = BilouCodec.OTHER;

  @Test
  public void testEncodeNoNames() {
    NameSample nameSample = new NameSample("Once upon a time.".split(" "), new Span[] {}, true);
    String[] expected = new String[] {OTHER, OTHER, OTHER, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assert.assertArrayEquals("Only 'Other' is expected.", expected, acutal);
  }

  @Test
  public void testEncodeSingleUnitTokenSpan() {
    String[] sentence = "I called Julie again.".split(" ");
    Span[] singleSpan = new Span[] { new Span(2,3, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, singleSpan, true);
    String[] expected = new String[] {OTHER, OTHER, A_UNIT, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assert.assertArrayEquals("'Julie' should be 'unit' only, the rest should be 'other'.", expected, acutal);
  }

  @Test
  public void testEncodeDoubleTokenSpan() {
    String[] sentence = "I saw Stefanie Schmidt today.".split(" ");
    Span[] singleSpan = new Span[] { new Span(2,4, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, singleSpan, true);
    String[] expected = new String[] {OTHER, OTHER, A_START, A_LAST, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assert.assertArrayEquals("'Stefanie' should be 'start' only, 'Schmidt' is 'last' " +
        "and the rest should be 'other'.", expected, acutal);
  }

  @Test
  public void testEncodeTripleTokenSpan() {
    String[] sentence = "Secretary - General Anders Fogh Rasmussen is from Denmark.".split(" ");
    Span[] singleSpan = new Span[] { new Span(3,6, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, singleSpan, true);
    String[] expected = new String[] {OTHER, OTHER, OTHER, A_START, A_CONTINUE,
        A_LAST, OTHER, OTHER, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assert.assertArrayEquals("'Anders' should be 'start' only, 'Fogh' is 'inside', " +
        "'Rasmussen' is 'last' and the rest should be 'other'.", expected, acutal);
  }

  @Test
  public void testEncodeAdjacentUnitSpans() {
    String[] sentence = "word PersonA PersonB word".split(" ");
    Span[] singleSpan = new Span[] { new Span(1,2, A_TYPE), new Span(2, 3, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, singleSpan, true);
    String[] expected = new String[] {OTHER, A_UNIT, A_UNIT, OTHER};
    String[] acutal = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assert.assertArrayEquals("Both PersonA and PersonB are 'unit' tags", expected, acutal);
  }

  @Test
  public void testCreateSequenceValidator() {
    Assert.assertTrue(codec.createSequenceValidator() instanceof BilouNameFinderSequenceValidator);
  }

  @Test
  public void testDecodeEmpty() {
    Span[] expected = new Span[] {};
    Span[] actual = codec.decode(new ArrayList<String>());
    Assert.assertArrayEquals(expected, actual);
  }

  /**
   * Unit, Other
   */
  @Test
  public void testDecodeSingletonFirst() {

    List<String> encoded = Arrays.asList(A_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(0,1, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assert.assertArrayEquals(expected, actual);
  }

  /**
   * Unit, Unit, Other
   */
  @Test
  public void testDecodeAdjacentSingletonFirst() {
    List<String> encoded = Arrays.asList(A_UNIT, A_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(0, 1, A_TYPE), new Span(1, 2, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assert.assertArrayEquals(expected, actual);
  }

  /**
   * Start, Last, Other
   */
  @Test
  public void testDecodePairFirst() {
    List<String> encoded = Arrays.asList(A_START, A_LAST, OTHER);
    Span[] expected = new Span[] {new Span(0, 2, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assert.assertArrayEquals(expected, actual);
  }

  /**
   * Start, Continue, Last, Other
   */
  @Test
  public void testDecodeTripletFirst() {
    List<String> encoded = Arrays.asList(A_START, A_CONTINUE, A_LAST, OTHER);
    Span[] expected = new Span[] {new Span(0, 3, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assert.assertArrayEquals(expected, actual);
  }

  /**
   * Start, Continue, Continue, Last, Other
   */
  @Test
  public void testDecodeTripletContinuationFirst() {
    List<String> encoded = Arrays.asList(A_START, A_CONTINUE, A_CONTINUE,
        A_LAST, OTHER);
    Span[] expected = new Span[] {new Span(0, 4, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assert.assertArrayEquals(expected, actual);
  }

  /**
   * Start, Last, Unit, Other
   */
  @Test
  public void testDecodeAdjacentPairSingleton() {
    List<String> encoded = Arrays.asList(A_START, A_LAST, A_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(0, 2, A_TYPE),
        new Span(2, 3, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assert.assertArrayEquals(expected, actual);
  }

  /**
   * Other, Unit, Other
   */
  @Test
  public void testDecodeOtherFirst() {
    List<String> encoded = Arrays.asList(OTHER, A_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(1, 2, A_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assert.assertArrayEquals(expected, actual);
  }

  /**
   * Other, A-Start, A-Continue, A-Last, Other, B-Start, B-Last, Other, C-Unit, Other
   */
  @Test
  public void testDecodeMultiClass() {
    List<String> encoded = Arrays.asList(OTHER, A_START, A_CONTINUE, A_LAST, OTHER,
        B_START, B_LAST, OTHER, C_UNIT, OTHER);
    Span[] expected = new Span[] {new Span(1, 4, A_TYPE),
        new Span(5, 7, B_TYPE), new Span(8,9, C_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assert.assertArrayEquals(expected, actual);
  }


  @Test
  public void testCompatibilityEmpty() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {}));
  }

  /**
   * Singles and singles in combination with other valid type (unit/start+last)
   */

  /**
   * B-Start => Fail
   * A-Unit, B-Start => Fail
   * A-Start, A-Last, B-Start => Fail
   */
  @Test
  public void testCompatibilitySinglesStart() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_START}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START}));
  }

  /**
   * B-Continue => Fail
   * A-Unit, B-Continue => Fail
   * A-Start, A-Last, B-Continue => Fail
   */
  @Test
  public void testCompatibilitySinglesContinue() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_CONTINUE}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_CONTINUE}));
  }

  /**
   * B-Last => Fail
   * A-Unit, B-Last => Fail
   * A-Start, A-Last, B-Last => Fail
   */
  @Test
  public void testCompatibilitySinglesLast() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_LAST}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_LAST}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_LAST}));
  }

  /**
   * Other => Fail
   * A-Unit, Other => Pass
   * A-Start, A-Last, Other => Pass
   */
  @Test
  public void testCompatibilitySinglesOther() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {OTHER}));
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT, OTHER}));
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, OTHER}));
  }

  /**
   * B-Unit => Pass
   * A-Unit, B-Unit => Pass
   * A-Start, A-Last, B-Unit => Pass
   */
  @Test
  public void testCompatibilitySinglesUnit() {
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {B_UNIT}));
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT, B_UNIT}));
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_UNIT}));
  }

  /**
   * Doubles and doubles in combination with other valid type (unit/start+last)
   *
   * B-Start, B-Continue => Fail
   * A-Unit, B-Start, B-Continue => Fail
   * A-Start, A-Last, B-Start, B-Continue => Fail
   */
  @Test
  public void testCompatibilityStartContinue() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_START, B_CONTINUE}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START, B_CONTINUE}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START, B_CONTINUE}));
  }

  /**
   * B-Start, B-Last => Pass
   * A-Unit, B-Start, B-Last => Pass
   * A-Start, A-Last, B-Start, B-Last => Pass
   */
  @Test
  public void testCompatibilityStartLast() {
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {B_START, B_LAST}));
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START, B_LAST}));
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START, B_LAST}));
  }

  /**
   * B-Start, Other => Fail
   * A-Unit, B-Start, Other => Fail
   * A-Start, A-Last, B-Start, Other => Fail
   */
  @Test
  public void testCompatibilityStartOther() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_START, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START, OTHER}));
  }

  /**
   * B-Start, B-Unit => Fail
   * A-Unit, B-Start, B-Unit => Fail
   * A-Start, A-Last, B-Start, B-Unit => Fail
   */
  @Test
  public void testCompatibilityStartUnit() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_START, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_START, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_START, B_UNIT}));
  }

  /**
   * B-Continue, C-Last => Fail
   * A-Unit, B-Continue, C-Last => Fail
   * A-Start, A-Last, B-Continue, B-Last => Fail
   */
  @Test
  public void testCompatibilityContinueLast() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE, B_LAST}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_CONTINUE, B_LAST}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_CONTINUE, B_LAST}));
  }

  /**
   * B-Continue, Other => Fail
   * A-Unit, B-Continue, Other => Fail
   * A-Start, A-Last, B-Continue, Other => Fail
   */
  @Test
  public void testCompatibilityContinueOther() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_CONTINUE, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_CONTINUE, OTHER}));
  }

  /**
   * B-Continue, B-Unit => Fail
   * A-Unit, B-Continue, B-Unit => Fail
   * A-Start, A-Last, B-Continue, B-Unit => Fail
   */
  @Test
  public void testCompatibilityContinueUnit() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_CONTINUE, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_CONTINUE, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_CONTINUE, B_UNIT}));
  }

  /**
   * B-Last, Other => Fail
   * A-Unit, B-Last, Other => Fail
   * A-Start, A-Last, B-Last, Other => Fail
   */
  @Test
  public void testCompatibilityLastOther() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_LAST, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_LAST, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_LAST, OTHER}));
  }

  /**
   * B-Last, B-Unit => Fail
   * A-Unit, B-Last, B-Unit => Fail
   * A-Start, A-Last, B-Last, B-Unit => Fail
   */
  @Test
  public void testCompatibilityLastUnit() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {B_LAST, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_UNIT, B_LAST, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, B_LAST, B_UNIT}));
  }

  /**
   * Other, B-Unit => Pass
   * A-Unit, Other, B-Unit => Pass
   * A-Start, A-Last, Other, B-Unit => Pass
   */
  @Test
  public void testCompatibilityOtherUnit() {
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {OTHER, B_UNIT}));
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {A_UNIT, OTHER, B_UNIT}));
    Assert.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_LAST, OTHER, B_UNIT}));
  }

  /**
   * Triples and triples in combination with other valid type (unit/start+last)
   *
   * B-Start, B-Continue, B-Last => Pass
   * A-Unit, B-Start, B-Continue, B-Last => Pass
   * A-Start, A-Last, B-Start, B-Continue, B-Last => Pass
   */
  @Test
  public void testCompatibilityStartContinueLast() {
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_LAST}));
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_LAST}));
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_LAST}));
  }

  /**
   * B-Start, B-Continue, Other => Fail
   * A-Unit, B-Start, B-Continue, Other => Fail
   * A-Start, A-Last, B-Start, B-Continue, Other => Fail
   */
  @Test
  public void testCompatibilityStartContinueOther() {
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, OTHER}));
  }

  /**
   * B-Start, B-Continue, B-Unit => Fail
   * A-Unit, B-Start, B-Continue, B-Unit => Fail
   * A-Start, A-Last, B-Start, B-Continue, B-Unit => Fail
   */
  @Test
  public void testCompatibilityStartContinueUnit() {
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_UNIT}));
  }

  /**
   * B-Continue, B-Last, Other => Fail
   * A-Unit, B-Continue, B-Last, Other => Fail
   * A-Start, A-Last, B-Continue, B-Last, Other => Fail
   */
  @Test
  public void testCompatibilityContinueLastOther() {
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_CONTINUE, B_LAST, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_CONTINUE, B_LAST, OTHER}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_CONTINUE, B_LAST, OTHER}));
  }

  /**
   * B-Continue, B-Last, B-Unit => Fail
   * A-Unit, B-Continue, B-Last, B_Unit => Fail
   * A-Start, A-Last, B-Continue, B-Last, B_Unit => Fail
   */
  @Test
  public void testCompatibilityContinueLastUnit() {
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_CONTINUE, B_LAST, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_CONTINUE, B_LAST, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_CONTINUE, B_LAST, B_UNIT}));
  }

  /**
   * B-Last, Other, B-Unit => Fail
   * A-Unit, B-Continue, B-Last, B_Unit => Fail
   * A-Start, A-Last, B-Continue, B-Last, B_Unit => Fail
   */
  @Test
  public void testCompatibilityLastOtherUnit() {
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_LAST, OTHER, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_LAST, OTHER, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_LAST, OTHER, B_UNIT}));
  }

  /**
   * Quadruples and quadruple in combination of unit/start+last
   *
   * B-Start, B-Continue, B-Last, Other => Pass
   * A-Unit, B-Start, B-Continue, B-Last, Other => Pass
   * A-Start, A-Last, B-Start, B-Continue, B-Last, Other => Pass
   */
  @Test
  public void testCompatibilityStartContinueLastOther() {
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_LAST, OTHER}));
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_LAST, OTHER}));
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_LAST, OTHER}));
  }

  /**
   * B-Start, B-Continue, B-Last, B-Unit => Pass
   * A-Unit, B-Start, B-Continue, B-Last, B-Unit => Pass
   * A-Start, A-Last, B-Start, B-Continue, B-Last, B-Unit => Pass
   */
  @Test
  public void testCompatibilityStartContinueLastUnit() {
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_LAST, B_UNIT}));
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_LAST, B_UNIT}));
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_LAST, B_UNIT}));
  }


  /**
   * B-Continue, B-Last, Other, B-Unit => Fail
   * A-Unit, B-Continue, B-Last, Other, B-Unit => Fail
   * A-Start, A-Last, B-Continue, B-Last, Other, B-Unit => Fail
   */
  @Test
  public void testCompatibilityContinueLastOtherUnit() {
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {B_CONTINUE, B_LAST, OTHER, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
  }

  /**
   * Quintuple
   *
   * B-Start, B-Continue, B-Last, Other, B-Unit => Pass
   * A-Unit, B-Start, B-Continue, B-Last, Other, B-Unit => Pass
   * A-Staart, A-Last, B-Start, B-Continue, B-Last, Other, B-Unit => Pass
   */
  @Test
  public void testCompatibilityUnitOther() {
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_START, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_UNIT, B_START, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_LAST, B_START, B_CONTINUE, B_LAST, OTHER, B_UNIT}));
  }

  /**
   * Multiclass
   */
  @Test
  public void testCompatibilityMultiClass() {
    Assert.assertTrue(codec.areOutcomesCompatible(
        new String[] {B_UNIT, A_CONTINUE, A_LAST, A_UNIT,
            B_START, B_LAST, A_START, C_UNIT, OTHER}));
  }

  /**
   * Bad combinations
   */
  @Test
  public void testCompatibilityBadTag() {
    Assert.assertFalse(codec.areOutcomesCompatible(
        new String[] {A_START, A_CONTINUE, OTHER, "BAD"}));
  }

  @Test
  public void testCompatibilityWrongClass() {
    Assert.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, B_LAST, OTHER}));
  }



}
