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
 * Tests {@link StringUtil#startsWithByteOrderMark(CharSequence)} and
 * {@link StringUtil#stripByteOrderMark(String)}.
 */
public class StringUtilByteOrderMarkTest {

  private static final String BOM = "﻿";

  @Test
  void testStripsOneLeadingMark() {
    Assertions.assertEquals("abc", StringUtil.stripByteOrderMark(BOM + "abc"));
    Assertions.assertEquals("", StringUtil.stripByteOrderMark(BOM));
    Assertions.assertEquals(BOM + "abc", StringUtil.stripByteOrderMark(BOM + BOM + "abc"));
  }

  @Test
  void testKeepsTextWithoutLeadingMark() {
    final String text = "a" + BOM + "b";
    Assertions.assertSame(text, StringUtil.stripByteOrderMark(text));
    Assertions.assertEquals("", StringUtil.stripByteOrderMark(""));
    Assertions.assertEquals(" " + BOM, StringUtil.stripByteOrderMark(" " + BOM));
  }

  @Test
  void testStartsWithByteOrderMark() {
    Assertions.assertTrue(StringUtil.startsWithByteOrderMark(BOM));
    Assertions.assertTrue(StringUtil.startsWithByteOrderMark(new StringBuilder(BOM + "{}")));
    Assertions.assertFalse(StringUtil.startsWithByteOrderMark(""));
    Assertions.assertFalse(StringUtil.startsWithByteOrderMark("{}" + BOM));
    // U+FFFE is the byte-swapped mark, not a mark itself
    Assertions.assertFalse(StringUtil.startsWithByteOrderMark("￾{}"));
  }

  @Test
  void testRejectsNull() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> StringUtil.startsWithByteOrderMark(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> StringUtil.stripByteOrderMark(null));
  }
}
