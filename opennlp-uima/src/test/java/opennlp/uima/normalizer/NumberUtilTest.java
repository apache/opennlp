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
package opennlp.uima.normalizer;

import java.text.ParseException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for opennlp.uima.normalizer.NumberUtil
 */
class NumberUtilTest {
  String VALID_LANGUAGE_CODE = "en";
  String INVALID_LANGUAGE_CODE = "INVALID";

  @Test
  void isLanguageSupported_EN_Pass() {
    Assertions.assertTrue(NumberUtil.isLanguageSupported(VALID_LANGUAGE_CODE));
  }

  @Test
  void isLanguageSupported_INVALID_FAIL() {
    Assertions.assertFalse(NumberUtil.isLanguageSupported(INVALID_LANGUAGE_CODE));
  }


  @Test
  void parse_long() throws ParseException {
    String numberStr = "  1 2 3 4 5 6 7 8 9 1 0      ";
    Long longValue = 12345678910L;
    Number result = NumberUtil.parse(numberStr , VALID_LANGUAGE_CODE);
    Assertions.assertEquals(longValue , result);
  }


  @Test
  void parse_double() throws ParseException {
    String numberStr = "     12   3456.78   910      ";
    Double doubleValue = 123456.78910;
    Number result = NumberUtil.parse(numberStr , VALID_LANGUAGE_CODE);
    Assertions.assertEquals(doubleValue , result);
  }

  @Test
  void parse_double_with_exception() throws ParseException {
    String numberStr = "     12   3456.78   910      ";
    Double doubleValue = 123456.78910;
    IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class , () -> {
      Number result = NumberUtil.parse(numberStr , INVALID_LANGUAGE_CODE);
    } , "java.lang.IllegalArgumentException: Language INVALID is not supported!");
  }

}
