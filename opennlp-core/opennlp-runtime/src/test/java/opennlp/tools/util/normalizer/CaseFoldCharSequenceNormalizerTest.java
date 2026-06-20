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

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CaseFoldCharSequenceNormalizerTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void testRootLowerCases() {
    assertEquals("hello world",
        CaseFoldCharSequenceNormalizer.getInstance().normalize("HeLLo WORLD").toString());
  }

  @Test
  void testRootKeepsAsciiIForCapitalI() {
    // Locale.ROOT lower-cases "I" to ASCII "i" (no Turkish dotless surprise).
    assertEquals("i", CaseFoldCharSequenceNormalizer.getInstance().normalize("I").toString());
  }

  @Test
  void testTurkishLocaleUsesDotlessI() {
    final CaseFoldCharSequenceNormalizer turkish =
        CaseFoldCharSequenceNormalizer.getInstance(Locale.forLanguageTag("tr"));
    assertEquals(cp(0x0131), turkish.normalize("I").toString()); // dotless lower-case i
  }

  @Test
  void testGetInstanceForRootReturnsTheSharedInstance() {
    assertSame(CaseFoldCharSequenceNormalizer.getInstance(),
        CaseFoldCharSequenceNormalizer.getInstance(Locale.ROOT));
  }

  @Test
  void testNullLocaleIsRejected() {
    assertThrows(NullPointerException.class,
        () -> new CaseFoldCharSequenceNormalizer((Locale) null));
  }
}
