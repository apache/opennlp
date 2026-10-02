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

package opennlp.tools;

import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Tests for the {@link RestoreDefaultLocale} extension. The tests run in a fixed order, so
 * the second one observes the default locale the first one left behind.
 */
@RestoreDefaultLocale
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestoreDefaultLocaleTest {

  private static Locale initialLocale;

  @BeforeAll
  static void captureInitialLocale() {
    initialLocale = Locale.getDefault();
  }

  /**
   * Pins the property that makes {@link RestoreDefaultLocale#TURKISH} a useful probe: the
   * upper case {@code 'I'} lowercases to the dotless {@code 'ı'} (U+0131).
   */
  @Test
  @Order(1)
  void testTurkishLowercasesToDotlessI() {
    Assertions.assertEquals("ı", "I".toLowerCase(RestoreDefaultLocale.TURKISH));
  }

  @Test
  @Order(2)
  void testChangesDefaultLocale() {
    Locale changed = initialLocale.equals(RestoreDefaultLocale.TURKISH)
        ? Locale.GERMANY : RestoreDefaultLocale.TURKISH;
    Locale.setDefault(changed);
    Assertions.assertEquals(changed, Locale.getDefault());
  }

  @Test
  @Order(3)
  void testRestoresDefaultLocaleAfterEachTest() {
    Assertions.assertEquals(initialLocale, Locale.getDefault());
  }
}
