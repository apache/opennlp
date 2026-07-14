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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link WhitespaceMode} class.
 */
public class WhitespaceModeTest {

  /**
   * Initializes {@link WhitespaceMode} while the property is unset, so tests that set an
   * invalid value exercise {@link WhitespaceMode#reset()} rather than class initialization.
   */
  @BeforeAll
  static void initializeWithCleanProperty() {
    System.clearProperty(WhitespaceMode.MODE_PROPERTY);
    WhitespaceMode.reset();
  }

  /**
   * Restores property resolution after each test, so no mode or property state leaks.
   */
  @AfterEach
  void resetWhitespaceMode() {
    System.clearProperty(WhitespaceMode.MODE_PROPERTY);
    WhitespaceMode.reset();
  }

  @Test
  void testDefaultsToUnicode() {
    WhitespaceMode.reset();
    assertEquals(WhitespaceMode.UNICODE, WhitespaceMode.current());
  }

  @Test
  void testBlankPropertyDefaultsToUnicode() {
    System.setProperty(WhitespaceMode.MODE_PROPERTY, "   ");
    WhitespaceMode.reset();
    assertEquals(WhitespaceMode.UNICODE, WhitespaceMode.current());
  }

  @Test
  void testPropertyResolvesLegacy() {
    System.setProperty(WhitespaceMode.MODE_PROPERTY, "LEGACY");
    WhitespaceMode.reset();
    assertEquals(WhitespaceMode.LEGACY, WhitespaceMode.current());
  }

  @Test
  void testPropertyResolvesCaseInsensitively() {
    System.setProperty(WhitespaceMode.MODE_PROPERTY, "legacy");
    WhitespaceMode.reset();
    assertEquals(WhitespaceMode.LEGACY, WhitespaceMode.current());
  }

  @Test
  void testPropertyResolvesUnicodeExplicitly() {
    System.setProperty(WhitespaceMode.MODE_PROPERTY, "unicode");
    WhitespaceMode.reset();
    assertEquals(WhitespaceMode.UNICODE, WhitespaceMode.current());
  }

  @Test
  void testInvalidPropertyValueThrows() {
    WhitespaceMode before = WhitespaceMode.current();
    System.setProperty(WhitespaceMode.MODE_PROPERTY, "sloppy");
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, WhitespaceMode::reset);
    assertTrue(e.getMessage().contains(WhitespaceMode.MODE_PROPERTY));
    assertTrue(e.getMessage().contains("sloppy"));
    // The previous mode is retained when re-resolution fails.
    assertEquals(before, WhitespaceMode.current());
  }

  @Test
  void testCurrentCachesUntilReset() {
    WhitespaceMode.reset();
    assertEquals(WhitespaceMode.UNICODE, WhitespaceMode.current());

    // The property is only read during resolution; setting it later has no effect until reset().
    System.setProperty(WhitespaceMode.MODE_PROPERTY, "LEGACY");
    assertEquals(WhitespaceMode.UNICODE, WhitespaceMode.current());

    WhitespaceMode.reset();
    assertEquals(WhitespaceMode.LEGACY, WhitespaceMode.current());
  }

  @Test
  void testSetActiveOverridesProperty() {
    System.setProperty(WhitespaceMode.MODE_PROPERTY, "UNICODE");
    WhitespaceMode.reset();
    WhitespaceMode.setActive(WhitespaceMode.LEGACY);
    assertEquals(WhitespaceMode.LEGACY, WhitespaceMode.current());
  }

  @Test
  void testSetActiveRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> WhitespaceMode.setActive(null));
  }

  @Test
  void testResetReturnsToPropertyResolution() {
    WhitespaceMode.setActive(WhitespaceMode.LEGACY);
    assertEquals(WhitespaceMode.LEGACY, WhitespaceMode.current());

    WhitespaceMode.reset();
    assertEquals(WhitespaceMode.UNICODE, WhitespaceMode.current());
  }
}
