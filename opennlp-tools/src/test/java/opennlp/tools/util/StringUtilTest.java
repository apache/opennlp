/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the {@link StringUtil} class.
 */
public class StringUtilTest {

  @Test
  public void testNoBreakSpace() {
    assertTrue(StringUtil.isWhitespace(0x00A0));
    assertTrue(StringUtil.isWhitespace(0x2007));
    assertTrue(StringUtil.isWhitespace(0x202F));
    
    assertTrue(StringUtil.isWhitespace((char) 0x00A0));
    assertTrue(StringUtil.isWhitespace((char) 0x2007));
    assertTrue(StringUtil.isWhitespace((char) 0x202F));
  }
  
  @Test
  public void testToLowerCase() {
    assertEquals("test", StringUtil.toLowerCase("TEST"));
    assertEquals("simple", StringUtil.toLowerCase("SIMPLE"));
  }

  @Test
  public void testToUpperCase() {
    assertEquals("TEST", StringUtil.toUpperCase("test"));
    assertEquals("SIMPLE", StringUtil.toUpperCase("simple"));
  }
  
  @Test
  public void testIsEmpty() {
    assertTrue(StringUtil.isEmpty(""));
    assertTrue(!StringUtil.isEmpty("a"));
  }
  
  @Test(expected=NullPointerException.class)
  public void testIsEmptyWithNullString() {
	// should raise a NPE  
    StringUtil.isEmpty(null);
  }

}
