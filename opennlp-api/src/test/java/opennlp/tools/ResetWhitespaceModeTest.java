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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import opennlp.tools.util.WhitespaceMode;

/**
 * Tests for {@link ResetWhitespaceMode}. The tests run in a fixed order, so the second one
 * observes the mode the first one left behind.
 */
@ResetWhitespaceMode
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ResetWhitespaceModeTest {

  private static WhitespaceMode resolvedMode;

  @BeforeAll
  static void captureResolvedMode() {
    resolvedMode = WhitespaceMode.current();
  }

  @Test
  @Order(1)
  void testChangesMode() {
    WhitespaceMode changed = resolvedMode == WhitespaceMode.LEGACY
        ? WhitespaceMode.UNICODE : WhitespaceMode.LEGACY;
    WhitespaceMode.setActive(changed);
    Assertions.assertEquals(changed, WhitespaceMode.current());
  }

  @Test
  @Order(2)
  void testResetsModeAfterEachTest() {
    Assertions.assertEquals(resolvedMode, WhitespaceMode.current());
  }
}
