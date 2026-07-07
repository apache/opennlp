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
package opennlp.tools.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AttributeValueTest {

  @Test
  void testHoldsComponents() {
    final AttributeValue value = new AttributeValue("Tokyo-to", "naturalearth", "adm1name");
    assertEquals("Tokyo-to", value.value());
    assertEquals("naturalearth", value.source());
    assertEquals("adm1name", value.notes());
  }

  @Test
  void testAcceptsEmptyNotes() {
    assertEquals("", new AttributeValue("v", "s", "").notes());
  }

  @Test
  void testRejectsNullOrEmptyValue() {
    assertMessage("Value must not be null or empty",
        assertThrows(IllegalArgumentException.class, () -> new AttributeValue(null, "s", "")));
    assertMessage("Value must not be null or empty",
        assertThrows(IllegalArgumentException.class, () -> new AttributeValue("", "s", "")));
  }

  @Test
  void testRejectsNullOrEmptySource() {
    assertMessage("Source must not be null or empty",
        assertThrows(IllegalArgumentException.class, () -> new AttributeValue("v", null, "")));
    assertMessage("Source must not be null or empty",
        assertThrows(IllegalArgumentException.class, () -> new AttributeValue("v", "", "")));
  }

  @Test
  void testRejectsNullNotes() {
    assertMessage("Notes must not be null",
        assertThrows(IllegalArgumentException.class, () -> new AttributeValue("v", "s", null)));
  }

  private static void assertMessage(String expected, IllegalArgumentException e) {
    assertTrue(e.getMessage().startsWith(expected), e.getMessage());
  }
}
