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

package opennlp.tools.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link StringUtil} class.
 */

public class StringUtilTest {

  @Test
  void testNoBreakSpace() {
    Assertions.assertTrue(StringUtil.isWhitespace(0x00A0));
    Assertions.assertTrue(StringUtil.isWhitespace(0x2007));
    Assertions.assertTrue(StringUtil.isWhitespace(0x202F));

    Assertions.assertTrue(StringUtil.isWhitespace((char) 0x00A0));
    Assertions.assertTrue(StringUtil.isWhitespace((char) 0x2007));
    Assertions.assertTrue(StringUtil.isWhitespace((char) 0x202F));
  }

  @Test
  void testToLowerCase() {
    Assertions.assertEquals("test", StringUtil.toLowerCase("TEST"));
    Assertions.assertEquals("simple", StringUtil.toLowerCase("SIMPLE"));
  }

  @Test
  void testToUpperCase() {
    Assertions.assertEquals("TEST", StringUtil.toUpperCase("test"));
    Assertions.assertEquals("SIMPLE", StringUtil.toUpperCase("simple"));
  }

  @Test
  void testIsEmpty() {
    Assertions.assertTrue(StringUtil.isEmpty(""));
    Assertions.assertFalse(StringUtil.isEmpty("a"));
  }

  @Test
  void testIsEmptyWithNullString() {
    // should raise a NPE
    Assertions.assertThrows(NullPointerException.class, () -> {
      // should raise a NPE
      StringUtil.isEmpty(null);
    });
  }

  @Test
  void testLowercaseBeyondBMP() {
    int[] codePoints = new int[] {65, 66578, 67};    //A,Deseret capital BEE,C
    int[] expectedCodePoints = new int[] {97, 66618, 99};//a,Deseret lowercase b,c
    String input = new String(codePoints, 0, codePoints.length);
    String lc = StringUtil.toLowerCase(input);
    Assertions.assertArrayEquals(expectedCodePoints, lc.codePoints().toArray());
  }
}
