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
    ExtensionLoader.unregisterAllowedPackage("opennlp.tools.util.ext");
    ExtensionLoader.unregisterAllowedPackage("com.acme");
    ExtensionLoader.unregisterAllowedPackage("com.example.nlp");
    ExtensionLoader.unregisterAllowedPackage("java.lang");
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
   * Allowlist check runs before Class.forName() — even for non-existent classes
   * the error must be "not in an allowed package", never "could not be located".
   */
  @Test
  void testAllowlistGateRunsBeforeClassForName() {
    ExtensionNotLoadedException ex = Assertions.assertThrows(
        ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(
            TestStringGenerator.class,
            "com.example.DoesNotExistOnClasspath$$Probe"));

    Assertions.assertTrue(ex.getMessage().contains("not in an allowed package"),
        "allowlist must reject before Class.forName(); got: " + ex.getMessage());
    Assertions.assertFalse(ex.getMessage().contains("could not be located"),
        "Class.forName() must not have been reached; got: " + ex.getMessage());
  }

  /**
   * After registerAllowedPackage(), a class from that package passes the allowlist gate.
   * Uses java.lang.String — outside opennlp.* so registration is required.
   * The call fails on assignability (String is not a TestStringGenerator), not on
   * the allowlist check, proving the gate let it through.
   */
  @Test
  void testRegisteredPackageIsAllowed() {
    ExtensionLoader.registerAllowedPackage("java.lang");

    ExtensionNotLoadedException ex = Assertions.assertThrows(
        ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(TestStringGenerator.class, "java.lang.String"));

    Assertions.assertFalse(ex.getMessage().contains("not in an allowed package"),
        "gate should have passed; got: " + ex.getMessage());
  }

  /**
   * unregisterAllowedPackage() removes a previously registered prefix.
   * A class from that package is rejected after removal.
   */
  @Test
  void testUnregisterAllowedPackage() {
    ExtensionLoader.registerAllowedPackage("java.lang");
    ExtensionLoader.unregisterAllowedPackage("java.lang");

    ExtensionNotLoadedException ex = Assertions.assertThrows(
        ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(TestStringGenerator.class, "java.lang.String"));

    Assertions.assertTrue(ex.getMessage().contains("not in an allowed package"));
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
   * registerAllowedPackage() throws NullPointerException for null and
   * IllegalArgumentException for blank inputs.
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

  /**
   * A null class name is rejected with ExtensionNotLoadedException before
   * any allowlist or class-loading logic runs.
   */
  @Test
  void testNullClassNameIsRejected() {
    Assertions.assertThrows(ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(TestStringGenerator.class, null));
  }

  // --- system property escape hatch tests ---

  /**
   * OPENNLP_EXT_ALLOWED_PACKAGES is read at class-load time, so in-process tests
   * cannot re-trigger that path. The equivalent is verified programmatically:
   * a package outside opennlp.* registered via registerAllowedPackage() passes the gate.
   * Uses java.lang.String — fails on assignability, not on the allowlist check.
   */
  @Test
  void testSystemPropertyAddsAllowedPackage() {
    ExtensionLoader.registerAllowedPackage("java.lang");

    ExtensionNotLoadedException ex = Assertions.assertThrows(
        ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(TestStringGenerator.class, "java.lang.String"));

    Assertions.assertFalse(ex.getMessage().contains("not in an allowed package"),
        "registered package should pass the gate; got: " + ex.getMessage());
  }

  /**
   * Multiple packages can be registered independently.
   */
  @Test
  void testSystemPropertyMultiplePackages() {
    ExtensionLoader.registerAllowedPackage("com.example.nlp");
    ExtensionLoader.registerAllowedPackage("java.lang");

    ExtensionNotLoadedException ex = Assertions.assertThrows(
        ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(TestStringGenerator.class, "java.lang.String"));

    Assertions.assertFalse(ex.getMessage().contains("not in an allowed package"),
        "registered package should pass the gate; got: " + ex.getMessage());
  }

  /**
   * System property prefix collision prevention — same dot-normalization applies.
   */
  @Test
  void testSystemPropertyPrefixCollisionPrevented() {
    ExtensionLoader.registerAllowedPackage("com.acme");

    Assertions.assertThrows(ExtensionNotLoadedException.class,
        () -> ExtensionLoader.instantiateExtension(
            TestStringGenerator.class,
            "com.acmeevil.Exploit"));
  }
}
