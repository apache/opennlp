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

import junit.framework.TestCase;

/**
 * Tests for the {@link StringUtil} class.
 */
public class StringUtilTest extends TestCase {

  public void testNoBreakSpace() {
    assertTrue(StringUtil.isWhitespace(0x00A0));
    assertTrue(StringUtil.isWhitespace(0x2007));
    assertTrue(StringUtil.isWhitespace(0x202F));
  }
}
