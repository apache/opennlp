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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link StringUtil} class.
 */
public class StringUtilTest {

  @Test
  public void testNoBreakSpace() {
    Assert.assertTrue(StringUtil.isWhitespace(0x00A0));
    Assert.assertTrue(StringUtil.isWhitespace(0x2007));
    Assert.assertTrue(StringUtil.isWhitespace(0x202F));

    Assert.assertTrue(StringUtil.isWhitespace((char) 0x00A0));
    Assert.assertTrue(StringUtil.isWhitespace((char) 0x2007));
    Assert.assertTrue(StringUtil.isWhitespace((char) 0x202F));
  }

  @Test
  public void testToLowerCase() {
    Assert.assertEquals("test", StringUtil.toLowerCase("TEST"));
    Assert.assertEquals("simple", StringUtil.toLowerCase("SIMPLE"));
  }

  @Test
  public void testToUpperCase() {
    Assert.assertEquals("TEST", StringUtil.toUpperCase("test"));
    Assert.assertEquals("SIMPLE", StringUtil.toUpperCase("simple"));
  }

  @Test
  public void testIsEmpty() {
    Assert.assertTrue(StringUtil.isEmpty(""));
    Assert.assertTrue(!StringUtil.isEmpty("a"));
  }

  @Test(expected = NullPointerException.class)
  public void testIsEmptyWithNullString() {
    // should raise a NPE
    StringUtil.isEmpty(null);
  }

}
