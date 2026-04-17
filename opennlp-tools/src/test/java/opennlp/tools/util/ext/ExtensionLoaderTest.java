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

package opennlp.tools.util.ext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExtensionLoaderTest {

  interface TestStringGenerator {
    String generateTestString();
  }

  static public class TestStringGeneratorImpl implements TestStringGenerator {
    public String generateTestString() {
      return "test";
    }
  }

  @AfterEach
  void reset() {
    System.clearProperty(ExtensionLoader.ALLOWED_PACKAGES_PROPERTY);
    ExtensionLoader.resetAllowedPackages();
  }

  // --- existing test ---

  @Test
  void testLoadingStringGenerator() {
    TestStringGenerator g = ExtensionLoader.instantiateExtension(TestStringGenerator.class,
        TestStringGeneratorImpl.class.getName());
    Assertions.assertEquals("test", g.generateTestString());
  }

  // --- allowlist tests ---

  /**
   * Classes in opennlp.* are allowed by default — no registration needed.
   */
  @Test
  void testBuiltinOpennlpPackageAllowedByDefault() {
    Assertions.assertDoesNotThrow(() ->
        ExtensionLoader.instantiateExtension(TestStringGenerator.class,
            TestStringGeneratorImpl.class.getName()));
  }

  /**
   * A class outside opennlp.* is rejected without registration.
   * This is the core security invariant — untrusted class names from model
   * manifests must not reach Class.forName() without an explicit allowlist entry.
   */
  @Test
  void testUnregisteredPackageIsRejected() {
    ExtensionNotLoadedException ex = Assertions.assertThrows(
        ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(
            TestStringGenerator.class,
            "com.example.exploit.MaliciousFactory"));

    Assertions.assertTrue(ex.getMessage().contains("not in an allowed package"),
        "exception message should mention allowed package");
  }

  /**
   * After registerAllowedPackage(), a class from that package is permitted.
   */
  @Test
  void testRegisteredPackageIsAllowed() {
    ExtensionLoader.registerAllowedPackage("opennlp.tools.util.ext");

    Assertions.assertDoesNotThrow(() ->
        ExtensionLoader.instantiateExtension(TestStringGenerator.class,
            TestStringGeneratorImpl.class.getName()));
  }

  /**
   * Prefix collision: registering "com.acme" must not permit "com.acmeevil.*".
   */
  @Test
  void testPrefixCollisionPrevented() {
    ExtensionLoader.registerAllowedPackage("com.acme");

    ExtensionNotLoadedException ex = Assertions.assertThrows(
        ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(
            TestStringGenerator.class,
            "com.acmeevil.Exploit"));

    Assertions.assertTrue(ex.getMessage().contains("not in an allowed package"));
  }

  /**
   * registerAllowedPackage() rejects null and blank inputs.
   */
  @Test
  void testRegisterAllowedPackageRejectsNullAndBlank() {
    Assertions.assertThrows(NullPointerException.class,
        () -> ExtensionLoader.registerAllowedPackage(null));

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ExtensionLoader.registerAllowedPackage(""));

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ExtensionLoader.registerAllowedPackage("   "));
  }

  // --- system property escape hatch tests ---

  /**
   * OPENNLP_EXT_ALLOWED_PACKAGES system property permits a custom package
   * without requiring a registerAllowedPackage() call — the operational escape hatch.
   */
  @Test
  void testSystemPropertyAddsAllowedPackage() {
    System.setProperty(ExtensionLoader.ALLOWED_PACKAGES_PROPERTY, "opennlp.tools.util.ext");
    ExtensionLoader.resetAllowedPackages(); // re-initialize from updated system property

    Assertions.assertDoesNotThrow(() ->
        ExtensionLoader.instantiateExtension(TestStringGenerator.class,
            TestStringGeneratorImpl.class.getName()));
  }

  /**
   * OPENNLP_EXT_ALLOWED_PACKAGES accepts multiple comma-separated prefixes.
   */
  @Test
  void testSystemPropertyMultiplePackages() {
    System.setProperty(ExtensionLoader.ALLOWED_PACKAGES_PROPERTY,
        "com.example.nlp, opennlp.tools.util.ext");
    ExtensionLoader.resetAllowedPackages();

    Assertions.assertDoesNotThrow(() ->
        ExtensionLoader.instantiateExtension(TestStringGenerator.class,
            TestStringGeneratorImpl.class.getName()));
  }

  /**
   * System property prefix collision prevention — same dot-normalization applies.
   */
  @Test
  void testSystemPropertyPrefixCollisionPrevented() {
    System.setProperty(ExtensionLoader.ALLOWED_PACKAGES_PROPERTY, "com.acme");
    ExtensionLoader.resetAllowedPackages();

    Assertions.assertThrows(ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(
            TestStringGenerator.class,
            "com.acmeevil.Exploit"));
  }
}
