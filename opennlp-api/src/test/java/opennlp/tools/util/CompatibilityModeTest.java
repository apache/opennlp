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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link CompatibilityMode} class.
 */
public class CompatibilityModeTest {

  /**
   * Initializes {@link CompatibilityMode} while the property is unset, so tests that set an
   * invalid value exercise {@link CompatibilityMode#reset()} rather than class initialization.
   */
  @BeforeAll
  static void initializeWithCleanProperty() {
    System.clearProperty(CompatibilityMode.MODE_PROPERTY);
    CompatibilityMode.reset();
  }

  /**
   * Restores property resolution after each test, so no mode or property state leaks.
   */
  @AfterEach
  void resetCompatibilityMode() {
    System.clearProperty(CompatibilityMode.MODE_PROPERTY);
    CompatibilityMode.reset();
  }

  @Test
  void testDefaultsToCurrent() {
    CompatibilityMode.reset();
    assertEquals(CompatibilityMode.CURRENT, CompatibilityMode.current());
  }

  @Test
  void testBlankPropertyDefaultsToCurrent() {
    System.setProperty(CompatibilityMode.MODE_PROPERTY, "   ");
    CompatibilityMode.reset();
    assertEquals(CompatibilityMode.CURRENT, CompatibilityMode.current());
  }

  @ParameterizedTest
  @CsvSource({"LEGACY, LEGACY", "legacy, LEGACY", " Legacy , LEGACY", "CURRENT, CURRENT",
      "current, CURRENT"})
  void testPropertyResolvesCaseInsensitively(String value, CompatibilityMode expected) {
    System.setProperty(CompatibilityMode.MODE_PROPERTY, value);
    CompatibilityMode.reset();
    assertEquals(expected, CompatibilityMode.current());
  }

  @ParameterizedTest
  @CsvSource({"sloppy", "UNICODE", "LEGACY CURRENT"})
  void testInvalidPropertyValueThrows(String value) {
    CompatibilityMode before = CompatibilityMode.current();
    System.setProperty(CompatibilityMode.MODE_PROPERTY, value);
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, CompatibilityMode::reset);
    assertTrue(e.getMessage().contains(CompatibilityMode.MODE_PROPERTY));
    assertTrue(e.getMessage().contains(value));
    assertEquals(before, CompatibilityMode.current());
  }

  @Test
  void testCurrentCachesUntilReset() {
    CompatibilityMode.reset();
    assertEquals(CompatibilityMode.CURRENT, CompatibilityMode.current());

    System.setProperty(CompatibilityMode.MODE_PROPERTY, "LEGACY");
    assertEquals(CompatibilityMode.CURRENT, CompatibilityMode.current());

    CompatibilityMode.reset();
    assertEquals(CompatibilityMode.LEGACY, CompatibilityMode.current());
  }

  @Test
  void testSetActiveOverridesProperty() {
    System.setProperty(CompatibilityMode.MODE_PROPERTY, "CURRENT");
    CompatibilityMode.reset();
    CompatibilityMode.setActive(CompatibilityMode.LEGACY);
    assertEquals(CompatibilityMode.LEGACY, CompatibilityMode.current());
  }

  @Test
  void testSetActiveRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> CompatibilityMode.setActive(null));
  }

  @Test
  void testIndependentOfWhitespaceMode() {
    WhitespaceMode before = WhitespaceMode.current();
    try {
      CompatibilityMode.setActive(CompatibilityMode.LEGACY);
      assertEquals(before, WhitespaceMode.current());
      WhitespaceMode.setActive(WhitespaceMode.LEGACY);
      CompatibilityMode.setActive(CompatibilityMode.CURRENT);
      assertEquals(WhitespaceMode.LEGACY, WhitespaceMode.current());
    } finally {
      WhitespaceMode.reset();
    }
  }
}
