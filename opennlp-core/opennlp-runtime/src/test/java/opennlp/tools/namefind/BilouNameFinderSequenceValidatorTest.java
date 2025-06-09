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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * This is the test class for {@link BilouNameFinderSequenceValidator}.
 * inputSequence is actually not used, but provided in the test to describe the cases.
 */
public class BilouNameFinderSequenceValidatorTest {

  private static final BilouNameFinderSequenceValidator validator = new BilouNameFinderSequenceValidator();
  private static final String START_A = "TypeA-" + BilouCodec.START;
  private static final String CONTINUE_A = "TypeA-" + BilouCodec.CONTINUE;
  private static final String LAST_A = "TypeA-" + BilouCodec.LAST;
  private static final String UNIT_A = "TypeA-" + BilouCodec.UNIT;


  private static final String START_B = "TypeB-" + BilouCodec.START;
  private static final String CONTINUE_B = "TypeB-" + BilouCodec.CONTINUE;
  private static final String LAST_B = "TypeB-" + BilouCodec.LAST;

  //private static String UNIT = BilouCodec.UNIT;
  private static final String OTHER = BilouCodec.OTHER;

  @Test
  void testStartAsFirstLabel() {
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something"};
    String[] outcomesSequence = new String[] {};
    Assertions.assertTrue(validator.validSequence(0, inputSequence, outcomesSequence, START_A));
  }

  @Test
  void testContinueAsFirstLabel() {
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {};
    Assertions.assertFalse(validator.validSequence(0, inputSequence, outcomesSequence, CONTINUE_A));
  }

  @Test
  void testLastAsFirstLabel() {
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {};
    Assertions.assertFalse(validator.validSequence(0, inputSequence, outcomesSequence, LAST_A));
  }

  @Test
  void testUnitAsFirstLabel() {
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {};
    Assertions.assertTrue(validator.validSequence(0, inputSequence, outcomesSequence, UNIT_A));
  }

  @Test
  void testOtherAsFirstLabel() {
    String[] inputSequence = new String[] {"something", "TypeA", "something"};
    String[] outcomesSequence = new String[] {};
    Assertions.assertTrue(validator.validSequence(0, inputSequence, outcomesSequence, OTHER));
  }

  /**
   * Start, Any Start => Invalid
   */
  @Test
  void testBeginFollowedByBegin() {

    String[] outcomesSequence = new String[] {START_A};

    // Same Types
    String outcome = START_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something"};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = START_B;
    inputSequence = new String[] {"TypeA", "TypeB", "something"};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Start, Continue, Same type => Valid
   * Start, Continue, Diff. Type => Invalid
   */
  @Test
  void testBeginFollowedByContinue() {

    String[] outcomesSequence = new String[] {START_A};

    // Same Types
    String outcome = CONTINUE_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "something"};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // Different Types
    outcome = CONTINUE_B;
    inputSequence = new String[] {"TypeA", "TypeB", "TypeB", "something"};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Start, Last, Same Type => Valid
   * Start, Last, Diff. Type => Invalid
   */
  @Test
  void testStartFollowedByLast() {

    String[] outcomesSequence = new String[] {START_A};

    // Same Type
    String outcome = LAST_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something"};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = LAST_B;
    inputSequence = new String[] {"TypeA", "TypeB", "something"};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Start, Other => Invalid
   */
  @Test
  void testStartFollowedByOther() {
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {START_A};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, OTHER));
  }

  /**
   * Start, Unit => Invalid
   */
  @Test
  void testStartFollowedByUnit() {
    String[] inputSequence = new String[] {"TypeA", "AnyType", "something"};
    String[] outcomesSequence = new String[] {START_A};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, UNIT_A));
  }

  /**
   * Continue, Any Begin => Invalid
   */
  @Test
  void testContinueFollowedByStart() {

    String[] outcomesSequence = new String[] {START_A, CONTINUE_A};

    // Same Types
    String outcome = START_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "something"};
    Assertions.assertFalse(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = START_B;
    inputSequence = new String[] {"TypeA", "TypeA", "TypeB", "something"};
    Assertions.assertFalse(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

  }

  /**
   * Continue, Continue, Same type => Valid
   * Continue, Continue, Diff. Type => Invalid
   */
  @Test
  void testContinueFollowedByContinue() {

    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, CONTINUE_A};

    // Same Types
    String outcome = CONTINUE_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeA", "something"};
    Assertions.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));

    // Different Types
    outcome = CONTINUE_B;
    inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeB", "something"};
    Assertions.assertFalse(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Continue, Last, Same Type => Valid
   * Continue, Last, Diff. Type => Invalid
   */
  @Test
  void testContinueFollowedByLast() {

    String[] outcomesSequence = new String[] {OTHER, START_A, CONTINUE_A};

    // Same Types
    String outcome = LAST_A;
    String[] inputSequence = new String[] {"something", "TypeA", "TypeA", "TypeA", "something"};
    Assertions.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));

    // Different Types
    outcome = LAST_B;
    inputSequence = new String[] {"something", "TypeA", "TypeA", "TypeB", "something"};
    Assertions.assertFalse(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Continue, Other => Invalid
   */
  @Test
  void testContinueFollowedByOther() {
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A};
    Assertions.assertFalse(validator.validSequence(2, inputSequence, outcomesSequence, OTHER));
  }

  /**
   * Continue, Unit => Invalid
   */
  @Test
  void testContinueFollowedByUnit() {
    String[] inputSequence = new String[] {"TypeA", "TypeA", "AnyType", "something"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A};
    Assertions.assertFalse(validator.validSequence(2, inputSequence, outcomesSequence, UNIT_A));
  }

  /**
   * Last, Any Start => Valid
   */
  @Test
  void testLastFollowedByStart() {

    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, LAST_A};

    // Same Types
    String outcome = START_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeA", "TypeA"};
    Assertions.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));

    // Same Types
    outcome = START_B;
    inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeB", "TypeB"};
    Assertions.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Last, Any Continue => Invalid
   */
  @Test
  void testLastFollowedByContinue() {

    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, LAST_A};

    String outcome = CONTINUE_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeA", "something"};
    Assertions.assertFalse(validator.validSequence(3, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = CONTINUE_B;
    inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeB", "something"};
    Assertions.assertFalse(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Last, Any Last => Invalid
   */
  @Test
  void testLastFollowedByLast() {

    String[] outcomesSequence = new String[] {OTHER, OTHER, START_A, CONTINUE_A, LAST_A};

    // Same Types
    String outcome = LAST_A;
    String[] inputSequence = new String[] {"something", "something", "TypeA",
        "TypeA", "TypeA", "TypeA", "something"};
    Assertions.assertFalse(validator.validSequence(5, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = LAST_B;
    inputSequence = new String[] {"something", "something", "TypeA", "TypeA",
        "TypeA", "TypeB", "something"};
    Assertions.assertFalse(validator.validSequence(5, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Last, Other => Valid
   */
  @Test
  void testLastFollowedByOther() {
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, LAST_A};
    Assertions.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, OTHER));
  }

  /**
   * Last, Unit => Valid
   */
  @Test
  void testLastFollowedByUnit() {
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "AnyType", "something"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, LAST_A};
    Assertions.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, UNIT_A));
  }

  /**
   * Other, Any Start => Valid
   */
  @Test
  void testOtherFollowedByBegin() {
    String[] inputSequence = new String[] {"something", "TypeA", "TypeA"};
    String[] outcomesSequence = new String[] {OTHER};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, START_A));
  }

  /**
   * Other, Any Continue => Invalid
   */
  @Test
  void testOtherFollowedByContinue() {
    String[] inputSequence = new String[] {"something", "TypeA", "TypeA"};
    String[] outcomesSequence = new String[] {OTHER};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, CONTINUE_A));
  }

  /**
   * Other, Any Last => Invalid
   */
  @Test
  void testOtherFollowedByLast() {
    String[] inputSequence = new String[] {"something", "TypeA", "TypeA"};
    String[] outcomesSequence = new String[] {OTHER};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, LAST_A));
  }

  /**
   * Outside, Unit => Valid
   */
  @Test
  void testOtherFollowedByUnit() {
    String[] inputSequence = new String[] {"something", "AnyType", "something"};
    String[] outcomesSequence = new String[] {OTHER};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, UNIT_A));
  }

  /**
   * Other, Other => Valid
   */
  @Test
  void testOutsideFollowedByOutside() {
    String[] inputSequence = new String[] {"something", "something", "something"};
    String[] outcomesSequence = new String[] {OTHER};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, OTHER));
  }

  /**
   * Unit, Any Start => Valid
   */
  @Test
  void testUnitFollowedByBegin() {
    String[] inputSequence = new String[] {"AnyType", "TypeA", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, START_A));
  }

  /**
   * Unit, Any Continue => Invalid
   */
  @Test
  void testUnitFollowedByInside() {
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, CONTINUE_A));
  }

  /**
   * Unit, Any Last => Invalid
   */
  @Test
  void testUnitFollowedByLast() {
    String[] inputSequence = new String[] {"AnyType", "TypeA", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, LAST_A));
  }

  /**
   * Unit, Other => Valid
   */
  @Test
  void testUnitFollowedByOutside() {
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, OTHER));
  }

  /**
   * Unit, Unit => Valid
   */
  @Test
  void testUnitFollowedByUnit() {
    String[] inputSequence = new String[] {"AnyType", "AnyType", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, UNIT_A));
  }

}
