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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests system-property parsing for resource limits. */
public class ResourceLimitsTest {

  private static final String PROPERTY = "opennlp.test.resource.limit";

  /** Clears the test property after each test. */
  @AfterEach
  void clearProperty() {
    System.clearProperty(PROPERTY);
  }

  /** Verifies positive integer and long overrides. */
  @Test
  void testPositiveOverrides() {
    System.setProperty(PROPERTY, "1024");
    Assertions.assertEquals(1024, ResourceLimits.initLimit(PROPERTY, 7));
    Assertions.assertEquals(1024L, ResourceLimits.initLimit(PROPERTY, 7L));
  }

  /** Verifies that an absent property uses the supplied defaults. */
  @Test
  void testAbsentPropertyUsesDefaults() {
    Assertions.assertEquals(7, ResourceLimits.initLimit(PROPERTY, 7));
    Assertions.assertEquals(7L, ResourceLimits.initLimit(PROPERTY, 7L));
  }

  /**
   * Verifies that an invalid property uses the supplied defaults.
   *
   * @param value The invalid property value.
   */
  @ParameterizedTest(name = "value {0} uses the default")
  @ValueSource(strings = {"", "  ", "abc", "-1", "0"})
  void testInvalidPropertyUsesDefaults(String value) {
    System.setProperty(PROPERTY, value);
    Assertions.assertEquals(7, ResourceLimits.initLimit(PROPERTY, 7));
    Assertions.assertEquals(7L, ResourceLimits.initLimit(PROPERTY, 7L));
  }
}
