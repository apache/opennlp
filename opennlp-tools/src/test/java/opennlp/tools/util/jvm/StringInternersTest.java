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

package opennlp.tools.util.jvm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.ext.ExtensionNotLoadedException;

public class StringInternersTest {

  @Test
  void testIntern() {
    final String interned = StringInterners.intern("opennlp");
    Assertions.assertSame(interned, StringInterners.intern(new String("opennlp".toCharArray())));
  }

  /**
   * Positive: all built-in interner implementations can be loaded via the
   * ExtensionLoader, as done by {@link StringInterners} when configured through
   * the {@code opennlp.interner.class} system property.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "opennlp.tools.util.jvm.CHMStringInterner",
      "opennlp.tools.util.jvm.CHMStringDeduplicator",
      "opennlp.tools.util.jvm.HMStringInterner",
      "opennlp.tools.util.jvm.JvmStringInterner",
      "opennlp.tools.util.jvm.NoOpStringInterner"})
  void testBuiltInInternersLoadable(String clazzName) {
    final StringInterner interner =
        ExtensionLoader.instantiateExtension(StringInterner.class, clazzName);
    Assertions.assertNotNull(interner);
  }

  /**
   * Negative: an interner class in a non-allowed package is rejected by
   * the ExtensionLoader allowlist before Class.forName() is called.
   */
  @Test
  void testInternerFromBlockedPackageThrows() {
    Assertions.assertThrows(ExtensionNotLoadedException.class, () ->
        ExtensionLoader.instantiateExtension(StringInterner.class, "com.malicious.Interner"));
  }

  /**
   * Negative: an opennlp.* class that does NOT implement StringInterner is
   * rejected by the isAssignableFrom type check.
   */
  @Test
  void testInternerOfWrongTypeThrows() {
    Assertions.assertThrows(ExtensionNotLoadedException.class, () ->
        ExtensionLoader.instantiateExtension(StringInterner.class,
            "opennlp.tools.util.jvm.StringInterners"));
  }
}
