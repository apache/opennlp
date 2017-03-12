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

import org.junit.Assert;
import org.junit.Test;

/**
 * This is the test class for {@link BilouNameFinderSequenceValidator}..
 * inputSequence is actually not used, but provided in the test to describe the cases.
 */
public class BilouNameFinderSequenceValidatorTest {

  private static BilouNameFinderSequenceValidator validator = new BilouNameFinderSequenceValidator();
  private static String START_A = "TypeA-" + BilouCodec.START;
  private static String CONTINUE_A = "TypeA-" + BilouCodec.CONTINUE;
  private static String LAST_A = "TypeA-" + BilouCodec.LAST;
  private static String UNIT_A = "TypeA-" + BilouCodec.UNIT;


  private static String START_B = "TypeB-" + BilouCodec.START;
  private static String CONTINUE_B = "TypeB-" + BilouCodec.CONTINUE;
  private static String LAST_B = "TypeB-" + BilouCodec.LAST;

  //private static String UNIT = BilouCodec.UNIT;
  private static String OTHER = BilouCodec.OTHER;

  @Test
  public void testStartAsFirstLabel() {
    String outcome = START_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something"};
    String[] outcomesSequence = new String[] { };
    Assert.assertTrue(validator.validSequence(0, inputSequence, outcomesSequence, outcome));
  }

  @Test
  public void testContinueAsFirstLabel() {
    String outcome = CONTINUE_A;
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] { };
    Assert.assertFalse(validator.validSequence(0, inputSequence, outcomesSequence, outcome));
  }

  @Test
  public void testLastAsFirstLabel() {
    String outcome = LAST_A;
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] { };
    Assert.assertFalse(validator.validSequence(0, inputSequence, outcomesSequence, outcome));
  }

  @Test
  public void testUnitAsFirstLabel() {
    String outcome = UNIT_A;
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] { };
    Assert.assertTrue(validator.validSequence(0, inputSequence, outcomesSequence, outcome));
  }

  @Test
  public void testOtherAsFirstLabel() {
    String outcome = OTHER;
    String[] inputSequence = new String[] {"something", "TypeA", "something"};
    String[] outcomesSequence = new String[] { };
    Assert.assertTrue(validator.validSequence(0, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Start, Any Start => Invalid
   */
  @Test
  public void testBeginFollowedByBegin() {

    String[] outcomesSequence = new String[] {START_A};

    // Same Types
    String outcome = START_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something"};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = START_B;
    inputSequence = new String[] {"TypeA", "TypeB", "something"};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Start, Continue, Same type => Valid
   * Start, Continue, Diff. Type => Invalid
   */
  @Test
  public void testBeginFollowedByContinue() {

    String[] outcomesSequence = new String[] {START_A};

    // Same Types
    String outcome = CONTINUE_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "something"};
    Assert.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // Different Types
    outcome = CONTINUE_B;
    inputSequence = new String[] {"TypeA", "TypeB", "TypeB", "something"};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Start, Last, Same Type => Valid
   * Start, Last, Diff. Type => Invalid
   */
  @Test
  public void testStartFollowedByLast() {

    String[] outcomesSequence = new String[] {START_A};

    // Same Type
    String outcome = LAST_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something"};
    Assert.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = LAST_B;
    inputSequence = new String[] {"TypeA", "TypeB", "something"};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Start, Other => Invalid
   */
  @Test
  public void testStartFollowedByOther() {
    String outcome = OTHER;
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {START_A};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   *    Start, Unit => Invalid
   */
  @Test
  public void testStartFollowedByUnit() {
    String outcome = UNIT_A;
    String[] inputSequence = new String[] {"TypeA", "AnyType", "something"};
    String[] outcomesSequence = new String[] {START_A};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Continue, Any Begin => Invalid
   */
  @Test
  public void testContinueFollowedByStart() {

    String[] outcomesSequence = new String[] {START_A, CONTINUE_A};

    // Same Types
    String outcome = START_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "something"};
    Assert.assertFalse(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = START_B;
    inputSequence = new String[] {"TypeA", "TypeA", "TypeB", "something"};
    Assert.assertFalse(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

  }

  /**
   * Continue, Continue, Same type => Valid
   * Continue, Continue, Diff. Type => Invalid
   */
  @Test
  public void testContinueFollowedByContinue() {

    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, CONTINUE_A};

    // Same Types
    String outcome = CONTINUE_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeA", "something"};
    Assert.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));

    // Different Types
    outcome = CONTINUE_B;
    inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeB", "something"};
    Assert.assertFalse(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Continue, Last, Same Type => Valid
   * Continue, Last, Diff. Type => Invalid
   */
  @Test
  public void testContinueFollowedByLast() {

    String[] outcomesSequence = new String[] {OTHER, START_A, CONTINUE_A};

    // Same Types
    String outcome = LAST_A;
    String[] inputSequence = new String[] {"something", "TypeA", "TypeA", "TypeA", "something"};
    Assert.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));

    // Different Types
    outcome = LAST_B;
    inputSequence = new String[] {"something", "TypeA", "TypeA", "TypeB", "something"};
    Assert.assertFalse(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Continue, Other => Invalid
   */
  @Test
  public void testContinueFollowedByOther() {
    String outcome = OTHER;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A};
    Assert.assertFalse(validator.validSequence(2, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Continue, Unit => Invalid
   */
  @Test
  public void testContinueFollowedByUnit() {
    String outcome = UNIT_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "AnyType", "something"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A};
    Assert.assertFalse(validator.validSequence(2, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Last, Any Start => Valid
   */
  @Test
  public void testLastFollowedByStart() {

    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, LAST_A};

    // Same Types
    String outcome = START_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeA", "TypeA"};
    Assert.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));

    // Same Types
    outcome = START_B;
    inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeB", "TypeB"};
    Assert.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   *    Last, Any Continue => Invalid
   */
  @Test
  public void testLastFollowedByContinue() {

    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, LAST_A};

    String outcome = CONTINUE_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeA", "something"};
    Assert.assertFalse(validator.validSequence(3, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = CONTINUE_B;
    inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "TypeB", "something"};
    Assert.assertFalse(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   *    Last, Any Last => Invalid
   */
  @Test
  public void testLastFollowedByLast() {

    String[] outcomesSequence = new String[] {OTHER, OTHER, START_A, CONTINUE_A, LAST_A};

    // Same Types
    String outcome = LAST_A;
    String[] inputSequence = new String[] {"something", "something", "TypeA",
        "TypeA", "TypeA", "TypeA", "something"};
    Assert.assertFalse(validator.validSequence(5, inputSequence, outcomesSequence, outcome));

    // Diff. Types
    outcome = LAST_B;
    inputSequence = new String[] {"something", "something", "TypeA", "TypeA",
        "TypeA", "TypeB", "something"};
    Assert.assertFalse(validator.validSequence(5, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Last, Other => Valid
   */
  @Test
  public void testLastFollowedByOther() {
    String outcome = OTHER;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, LAST_A};
    Assert.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Last, Unit => Valid
   */
  @Test
  public void testLastFollowedByUnit() {
    String outcome = UNIT_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "TypeA", "AnyType", "something"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A, LAST_A};
    Assert.assertTrue(validator.validSequence(3, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Other, Any Start => Valid
   */
  @Test
  public void testOtherFollowedByBegin() {
    String outcome = START_A;
    String[] inputSequence = new String[] {"something", "TypeA", "TypeA"};
    String[] outcomesSequence = new String[] {OTHER};
    Assert.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Other, Any Continue => Invalid
   */
  @Test
  public void testOtherFollowedByContinue() {
    String outcome = CONTINUE_A;
    String[] inputSequence = new String[] {"something", "TypeA", "TypeA"};
    String[] outcomesSequence = new String[] {OTHER};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Other, Any Last => Invalid
   */
  @Test
  public void testOtherFollowedByLast() {
    String outcome = LAST_A;
    String[] inputSequence = new String[] {"something", "TypeA", "TypeA"};
    String[] outcomesSequence = new String[] {OTHER};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Outside, Unit => Valid
   */
  @Test
  public void testOtherFollowedByUnit() {
    String outcome = UNIT_A;
    String[] inputSequence = new String[] {"something", "AnyType", "something"};
    String[] outcomesSequence = new String[] {OTHER};
    Assert.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Other, Other => Valid
   */
  @Test
  public void testOutsideFollowedByOutside() {
    String outcome = OTHER;
    String[] inputSequence = new String[] {"something", "something", "something"};
    String[] outcomesSequence = new String[] {OTHER};
    Assert.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Unit, Any Start => Valid
   */
  @Test
  public void testUnitFollowedByBegin() {
    String outcome = START_A;
    String[] inputSequence = new String[] {"AnyType", "TypeA", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assert.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Unit, Any Continue => Invalid
   */
  @Test
  public void testUnitFollowedByInside() {
    String outcome = CONTINUE_A;
    String[] inputSequence = new String[] {"TypeA", "TypeA", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Unit, Any Last => Invalid
   */
  @Test
  public void testUnitFollowedByLast() {
    String outcome = LAST_A;
    String[] inputSequence = new String[] {"AnyType", "TypeA", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assert.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Unit, Other => Valid
   */
  @Test
  public void testUnitFollowedByOutside() {
    String outcome = OTHER;
    String[] inputSequence = new String[] {"TypeA", "something", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assert.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

  /**
   * Unit, Unit => Valid
   */
  @Test
  public void testUnitFollowedByUnit() {
    String outcome = UNIT_A;
    String[] inputSequence = new String[] {"AnyType", "AnyType", "something"};
    String[] outcomesSequence = new String[] {UNIT_A};
    Assert.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));
  }

}
