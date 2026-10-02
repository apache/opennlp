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
package opennlp.tools.util.normalizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MailAddressScan#isAddress(CharSequence)}.
 */
public class MailAddressScanTest {

  @ParameterizedTest
  @ValueSource(strings = {"user@example.com", "a@bc", "a.b+c_d-e@x-y.z", "1@23", "-@--",
      "user@localhost", "user@example.", ".user@example.com"})
  void acceptsWholeAddresses(String text) {
    assertTrue(MailAddressScan.isAddress(text));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "@", "user", "user@", "@example.com", "user@b",
      "user@.example.com", "user@@example.com", "user@exa_mple.com", "user@example.com!",
      " user@example.com", "usér@example.com", "user@exämple.com",
      "user@example.com😀", "😀user@example.com"})
  void rejectsOtherTexts(String text) {
    assertFalse(MailAddressScan.isAddress(text));
  }

  @Test
  void rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> MailAddressScan.isAddress(null));
  }
}
