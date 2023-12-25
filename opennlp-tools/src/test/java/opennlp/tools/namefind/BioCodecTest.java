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
 * This is the test class for {@link BioCodec}.
 */
public class BioCodecTest {

  private static final BioCodec codec = new BioCodec();

  private static final String A_TYPE = "atype";
  private static final String A_START = A_TYPE + "-" + BioCodec.START;
  private static final String A_CONTINUE = A_TYPE + "-" + BioCodec.CONTINUE;

  private static final String B_TYPE = "btype";
  private static final String B_START = B_TYPE + "-" + BioCodec.START;
  private static final String B_CONTINUE = B_TYPE + "-" + BioCodec.CONTINUE;

  private static final String C_TYPE = "ctype";
  private static final String C_START = C_TYPE + "-" + BioCodec.START;

  private static final String OTHER = BioCodec.OTHER;

  @Test
  void testEncodeNoNames() {
    NameSample nameSample = new NameSample("Once upon a time.".split(" "), new Span[] {}, true);
    String[] expected = new String[] {OTHER, OTHER, OTHER, OTHER};
    String[] actual = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, actual, "Only 'Other' is expected.");
  }

  @Test
  void testEncodeSingleTokenSpan() {
    String[] sentence = "I called Julie again.".split(" ");
    Span[] spans = new Span[] {new Span(2, 3, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, spans, true);
    String[] expected = new String[] {OTHER, OTHER, A_START, OTHER};
    String[] actual = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, actual,
        "'Julie' should be 'start' only, the rest should be 'other'.");
  }

  @Test
  void testEncodeDoubleTokenSpan() {
    String[] sentence = "I saw Stefanie Schmidt today.".split(" ");
    Span[] span = new Span[] {new Span(2, 4, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, span, true);
    String[] expected = new String[] {OTHER, OTHER, A_START, A_CONTINUE, OTHER};
    String[] actual = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, actual, "'Stefanie' should be 'start' only, 'Schmidt' is " +
        "'continue' and the rest should be 'other'.");
  }

  @Test
  void testEncodeDoubleTokenSpanNoType() {
    final String DEFAULT_START = "default" + "-" + BioCodec.START;
    final String DEFAULT_CONTINUE = "default" + "-" + BioCodec.CONTINUE;
    String[] sentence = "I saw Stefanie Schmidt today.".split(" ");
    Span[] span = new Span[] {new Span(2, 4, null)};
    NameSample nameSample = new NameSample(sentence, span, true);
    String[] expected = new String[] {OTHER, OTHER, DEFAULT_START, DEFAULT_CONTINUE, OTHER};
    String[] actual = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, actual, "'Stefanie' should be 'start' only, 'Schmidt' is " +
        "'continue' and the rest should be 'other'.");
  }

  @Test
  void testEncodeAdjacentSingleSpans() {
    String[] sentence = "something PersonA PersonB Something".split(" ");
    Span[] span = new Span[] {new Span(1, 2, A_TYPE), new Span(2, 3, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, span, true);
    String[] expected = new String[] {OTHER, A_START, A_START, OTHER};
    String[] actual = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, actual);
  }

  @Test
  void testEncodeAdjacentSpans() {
    String[] sentence = "something PersonA PersonA PersonB Something".split(" ");
    Span[] span = new Span[] {new Span(1, 3, A_TYPE), new Span(3, 4, A_TYPE)};
    NameSample nameSample = new NameSample(sentence, span, true);
    String[] expected = new String[] {OTHER, A_START, A_CONTINUE, A_START, OTHER};
    String[] actual = codec.encode(nameSample.getNames(), nameSample.getSentence().length);
    Assertions.assertArrayEquals(expected, actual);
  }

  @Test
  void testCreateSequenceValidator() {
    Assertions.assertInstanceOf(NameFinderSequenceValidator.class, codec.createSequenceValidator());
  }


  @Test
  void testDecodeEmpty() {
    Span[] expected = new Span[] {};
    Span[] actual = codec.decode(new ArrayList<>());
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Start, Other
   */
  @Test
  void testDecodeSingletonFirst() {

    List<String> encoded = Arrays.asList(B_START, OTHER);
    Span[] expected = new Span[] {new Span(0, 1, B_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Start Start Other
   */
  @Test
  void testDecodeAdjacentSingletonFirst() {
    List<String> encoded = Arrays.asList(B_START, B_START, OTHER);
    Span[] expected = new Span[] {new Span(0, 1, B_TYPE), new Span(1, 2, B_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Start Continue Other
   */
  @Test
  void testDecodePairFirst() {
    List<String> encoded = Arrays.asList(B_START, B_CONTINUE, OTHER);
    Span[] expected = new Span[] {new Span(0, 2, B_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Start Continue Continue Other
   */
  @Test
  void testDecodeTripletFirst() {
    List<String> encoded = Arrays.asList(B_START, B_CONTINUE, B_CONTINUE, OTHER);
    Span[] expected = new Span[] {new Span(0, 3, B_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Start Continue Start Other
   */
  @Test
  void testDecodeAdjacentPairSingleton() {
    List<String> encoded = Arrays.asList(B_START, B_CONTINUE, B_START, OTHER);
    Span[] expected = new Span[] {new Span(0, 2, B_TYPE), new Span(2, 3, B_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * Other Start Other
   */
  @Test
  void testDecodeOtherFirst() {
    List<String> encoded = Arrays.asList(OTHER, B_START, OTHER);
    Span[] expected = new Span[] {new Span(1, 2, B_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  /**
   * A-Start A-Continue, A-Continue, Other, B-Start, B-Continue, Other, C-Start, Other
   */
  @Test
  void testDecodeMultiClass() {
    List<String> encoded = Arrays.asList(OTHER, A_START, A_CONTINUE, A_CONTINUE,
        OTHER, B_START, B_CONTINUE, OTHER, C_START, OTHER);
    Span[] expected = new Span[] {new Span(1, 4, A_TYPE),
        new Span(5, 7, B_TYPE), new Span(8, 9, C_TYPE)};
    Span[] actual = codec.decode(encoded);
    Assertions.assertArrayEquals(expected, actual);
  }

  @Test
  void testCompatibilityEmpty() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {}));
  }

  @Test
  void testCompatibilitySingleStart() {
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_START}));
  }

  @Test
  void testCompatibilitySingleContinue() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_CONTINUE}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_START, A_CONTINUE}));
  }

  @Test
  void testCompatibilitySingleOther() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {OTHER}));
  }

  @Test
  void testCompatibilityStartContinue() {
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_CONTINUE}));
  }

  @Test
  void testCompatibilityStartOther() {
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, OTHER}));
  }

  @Test
  void testCompatibilityContinueOther() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_CONTINUE, OTHER}));
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {B_START, A_CONTINUE, OTHER}));
  }

  @Test
  void testCompatibilityStartContinueOther() {
    Assertions.assertTrue(codec.areOutcomesCompatible(new String[] {A_START, A_CONTINUE, OTHER}));
  }


  @Test
  void testCompatibilityMultiClass() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_CONTINUE, B_START, OTHER}));
  }

  @Test
  void testCompatibilityBadTag() {
    Assertions.assertFalse(codec.areOutcomesCompatible(new String[] {A_START, A_CONTINUE, "BAD"}));
  }

  @Test
  void testCompatibilityRepeated() {
    Assertions.assertTrue(codec.areOutcomesCompatible(
        new String[] {A_START, A_START, A_CONTINUE, A_CONTINUE, B_START, B_START, OTHER, OTHER}));
  }

}
