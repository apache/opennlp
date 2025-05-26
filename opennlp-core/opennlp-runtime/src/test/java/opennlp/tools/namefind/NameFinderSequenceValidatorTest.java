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
 * This is the test class for {@link NameFinderSequenceValidator}.
 */
public class NameFinderSequenceValidatorTest {

  private static final NameFinderSequenceValidator validator = new NameFinderSequenceValidator();
  private static final String START_A = "TypeA-" + NameFinderME.START;
  private static final String CONTINUE_A = "TypeA-" + NameFinderME.CONTINUE;
  private static final String START_B = "TypeB-" + NameFinderME.START;
  private static final String CONTINUE_B = "TypeB-" + NameFinderME.CONTINUE;
  private static final String OTHER = NameFinderME.OTHER;

  @Test
  void testContinueCannotBeFirstOutcome() {

    String[] inputSequence = new String[] {"PersonA", "is", "here"};
    String[] outcomesSequence = new String[] {};
    Assertions.assertFalse(validator.validSequence(0, inputSequence, outcomesSequence, CONTINUE_A));

  }

  @Test
  void testContinueAfterStartAndSameType() {

    // previous start, same name type
    String[] inputSequence = new String[] {"Stefanie", "Schmidt", "is", "German"};
    String[] outcomesSequence = new String[] {START_A};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, CONTINUE_A));

  }

  @Test
  void testContinueAfterStartAndNotSameType() {

    // previous start, not same name type
    String[] inputSequence = new String[] {"PersonA", "LocationA", "something"};
    String[] outcomesSequence = new String[] {START_A};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, CONTINUE_B));
  }

  @Test
  void testContinueAfterContinueAndSameType() {

    // previous continue, same name type
    String[] inputSequence = new String[] {"FirstName", "MidleName", "LastName", "is", "a", "long", "name"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A};
    Assertions.assertTrue(validator.validSequence(2, inputSequence, outcomesSequence, CONTINUE_A));
  }

  @Test
  void testContinueAfterContinueAndNotSameType() {

    // previous continue, not same name type
    String[] inputSequence = new String[] {"FirstName", "LastName", "LocationA", "something"};
    String[] outcomesSequence = new String[] {START_A, CONTINUE_A};
    Assertions.assertFalse(validator.validSequence(2, inputSequence, outcomesSequence, CONTINUE_B));
  }

  @Test
  void testContinueAfterOther() {

    // previous other
    String[] inputSequence = new String[] {"something", "is", "wrong", "here"};
    String[] outcomesSequence = new String[] {OTHER};
    Assertions.assertFalse(validator.validSequence(1, inputSequence, outcomesSequence, CONTINUE_A));
  }

  @Test
  void testStartIsAlwaysAValidOutcome() {

    final String outcome = START_A;

    // pos zero
    String[] inputSequence = new String[] {"PersonA", "is", "here"};
    String[] outcomesSequence = new String[] {};
    Assertions.assertTrue(validator.validSequence(0, inputSequence, outcomesSequence, outcome));

    // pos one, previous other
    inputSequence = new String[] {"it's", "PersonA", "again"};
    outcomesSequence = new String[] {OTHER};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // pos one, previous start
    inputSequence = new String[] {"PersonA", "PersonB", "something"};
    outcomesSequence = new String[] {START_A};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // pos two, previous other
    inputSequence = new String[] {"here", "is", "PersonA"};
    outcomesSequence = new String[] {OTHER, OTHER};
    Assertions.assertTrue(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

    // pos two, previous start, same name type
    inputSequence = new String[] {"is", "PersonA", "PersoneB"};
    outcomesSequence = new String[] {OTHER, START_A};
    Assertions.assertTrue(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

    // pos two, previous start, different name type
    inputSequence = new String[] {"something", "PersonA", "OrganizationA"};
    outcomesSequence = new String[] {OTHER, START_B};
    Assertions.assertTrue(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

    // pos two, previous continue, same name type
    inputSequence = new String[] {"Stefanie", "Schmidt", "PersonB", "something"};
    outcomesSequence = new String[] {START_A, CONTINUE_A};
    Assertions.assertTrue(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

    // pos two, previous continue, not same name type
    inputSequence = new String[] {"Stefanie", "Schmidt", "OrganizationA", "something"};
    outcomesSequence = new String[] {START_B, CONTINUE_B};
    Assertions.assertTrue(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

  }

  @Test
  void testOtherIsAlwaysAValidOutcome() {

    final String outcome = OTHER;

    // pos zero
    String[] inputSequence = new String[] {"it's", "a", "test"};
    String[] outcomesSequence = new String[] {};
    Assertions.assertTrue(validator.validSequence(0, inputSequence, outcomesSequence, outcome));

    // pos one, previous other
    inputSequence = new String[] {"it's", "a", "test"};
    outcomesSequence = new String[] {OTHER};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // pos one, previous start
    inputSequence = new String[] {"Mike", "is", "here"};
    outcomesSequence = new String[] {START_A};
    Assertions.assertTrue(validator.validSequence(1, inputSequence, outcomesSequence, outcome));

    // pos two, previous other
    inputSequence = new String[] {"it's", "a", "test"};
    outcomesSequence = new String[] {OTHER, OTHER};
    Assertions.assertTrue(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

    // pos two, previous start
    inputSequence = new String[] {"is", "Mike", "here"};
    outcomesSequence = new String[] {OTHER, START_A};
    Assertions.assertTrue(validator.validSequence(2, inputSequence, outcomesSequence, outcome));

    // pos two, previous continue
    inputSequence = new String[] {"Stefanie", "Schmidt", "lives", "at", "home"};
    outcomesSequence = new String[] {START_A, CONTINUE_A};
    Assertions.assertTrue(validator.validSequence(2, inputSequence, outcomesSequence, outcome));
  }

}
