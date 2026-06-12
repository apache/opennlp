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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The {@link ExtensionLoader} is responsible to load extensions to the OpenNLP library.
 * <p>
 * Only classes whose fully-qualified name starts with a registered package prefix are
 * permitted. The default allowed prefix is {@code opennlp.}, which covers all built-in
 * factories and serializers.
 * <p>
 * To allow custom extension classes from other packages, either:
 * <ul>
 *   <li>Call {@link #registerAllowedPackage(String)} programmatically before loading
 *       any model that uses the custom class.</li>
 *   <li>Set the system property {@code OPENNLP_EXT_ALLOWED_PACKAGES} to a
 *       comma-separated list of package prefixes at JVM startup, e.g.
 *       {@code -DOPENNLP_EXT_ALLOWED_PACKAGES=com.acme.nlp.,com.other.}.</li>
 * </ul>
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ExtensionLoader {

  /**
   * System property for supplying additional allowed package prefixes.
   * Value is a comma-separated list, e.g. {@code com.acme.nlp.,com.other.}.
   * <p>
   * This property is read once at class-load time. If it cannot be set via
   * {@code -D} at JVM startup (e.g. in embedded or test scenarios), call
   * {@link #registerAllowedPackage(String)} before loading any model that
   * uses a custom factory or serializer.
   */
  public static final String ALLOWED_PACKAGES_PROPERTY = "OPENNLP_EXT_ALLOWED_PACKAGES";

  /**
   * Package prefixes whose classes are permitted to be instantiated as extensions.
   * Seeded from {@code opennlp.} plus any prefixes in {@link #ALLOWED_PACKAGES_PROPERTY}.
   */
  private static final Set<String> ALLOWED_PREFIXES = initAllowedPrefixes();

  private static boolean isOsgiAvailable = false;

  private static Set<String> initAllowedPrefixes() {
    Set<String> prefixes = new CopyOnWriteArraySet<String>(Collections.singleton("opennlp."));
    String prop = System.getProperty(ALLOWED_PACKAGES_PROPERTY, "");
    if (!prop.trim().isEmpty()) {
      Arrays.stream(prop.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(s -> s.endsWith(".") ? s : s + ".")
          .forEach(prefixes::add);
    }
    return prefixes;
  }

  private ExtensionLoader() {
  }

  /**
   * Registers an additional package prefix whose classes are permitted to be
   * loaded as OpenNLP extensions. Call this once at application startup, before
   * loading any model that uses a custom factory or serializer from that package.
   * <p>
   * The prefix is normalized to end with {@code '.'} to prevent collision attacks
   * (e.g. registering {@code "com.acme"} cannot be exploited via {@code "com.acmeevil.*"}).
   *
   * @param packagePrefix The package prefix to allow, e.g. {@code "com.example.nlp"}.
   *                      Must not be {@code null} or blank.
   * @throws NullPointerException if {@code packagePrefix} is {@code null}.
   * @throws IllegalArgumentException if {@code packagePrefix} is blank.
   */
  public static void registerAllowedPackage(String packagePrefix) {
    Objects.requireNonNull(packagePrefix, "packagePrefix must not be null");
    if (packagePrefix.trim().isEmpty()) {
      throw new IllegalArgumentException("packagePrefix must not be blank");
    }
    String normalized = packagePrefix.endsWith(".") ? packagePrefix : packagePrefix + ".";
    ALLOWED_PREFIXES.add(normalized);
  }

  /**
   * Removes a previously registered package prefix. Has no effect if the prefix
   * was not registered. The default {@code opennlp.} prefix can also be removed,
   * though this is not recommended.
   * <p>
   * The prefix is normalized to end with {@code '.'} before removal, matching the
   * normalization applied in {@link #registerAllowedPackage(String)}.
   *
   * @param packagePrefix The package prefix to remove, e.g. {@code "com.example.nlp"}.
   *                      Must not be {@code null}.
   * @throws NullPointerException if {@code packagePrefix} is {@code null}.
   */
  public static void unregisterAllowedPackage(String packagePrefix) {
    Objects.requireNonNull(packagePrefix, "packagePrefix must not be null");
    String normalized = packagePrefix.endsWith(".") ? packagePrefix : packagePrefix + ".";
    ALLOWED_PREFIXES.remove(normalized);
  }

  static boolean isOSGiAvailable() {
    return isOsgiAvailable;
  }

  static void setOSGiAvailable() {
    isOsgiAvailable = true;
  }

  // Pass in the type (interface) of the class to load
  /**
   * Instantiates an user provided extension to OpenNLP.
   * <p>
   * The extension is either loaded from the class path or if running
   * inside an OSGi environment via an OSGi service.
   * <p>
   * Initially it tries using the public default
   * constructor. If it is not found, it will check if the class follows the singleton
   * pattern: a static field named <code>INSTANCE</code> that returns an object of the type
   * <code>T</code>.
   *
   * @param clazz
   * @param extensionClassName
   *
   * @return the instance of the extension class
   *
   * @throws ExtensionNotLoadedException Thrown if the load operation failed or
   *         the class is not in an allowed package.
   */
  // TODO: Throw custom exception if loading fails ...
  @SuppressWarnings("unchecked")
  public static <T> T instantiateExtension(Class<T> clazz, String extensionClassName) {

    if (extensionClassName == null) {
      throw new ExtensionNotLoadedException("extensionClassName must not be null");
    }

    // Validate BEFORE Class.forName() — Class.forName() executes static initializers
    // (CWE-470), which must not run for untrusted class names.
    boolean allowed = ALLOWED_PREFIXES.stream().anyMatch(extensionClassName::startsWith);
    if (!allowed) {
      throw new ExtensionNotLoadedException(
          "Class '" + extensionClassName + "' is not in an allowed package. " +
          "Register the package via ExtensionLoader.registerAllowedPackage() or set " +
          "the system property " + ALLOWED_PACKAGES_PROPERTY + " at JVM startup.");
    }

    // First try to load extension and instantiate extension from class path
    try {
      Class<?> extClazz = Class.forName(extensionClassName);

      if (clazz.isAssignableFrom(extClazz)) {

        try {
          return (T) extClazz.newInstance();
        } catch (InstantiationException e) {
          throw new ExtensionNotLoadedException(e);
        } catch (IllegalAccessException e) {
          // constructor is private. Try to load using INSTANCE
          Field instanceField;
          try {
            instanceField = extClazz.getDeclaredField("INSTANCE");
          } catch (NoSuchFieldException | SecurityException e1) {
            throw new ExtensionNotLoadedException(e1);
          }
          if (instanceField != null) {
            try {
              return (T) instanceField.get(null);
            } catch (IllegalArgumentException | IllegalAccessException e1) {
              throw new ExtensionNotLoadedException(e1);
            }
          }
          throw new ExtensionNotLoadedException(e);
        }
      }
      else {
        throw new ExtensionNotLoadedException("Extension class '" + extClazz.getName() +
                "' needs to have type: " + clazz.getName());
      }
    } catch (ClassNotFoundException e) {
      // Class is not on classpath
    }

    // Loading from class path failed

    // Either something is wrong with the class name or OpenNLP is
    // running in an OSGi environment. The extension classes are not
    // on our classpath in this case.
    // In OSGi we need to use services to get access to extensions.

    // Determine if OSGi class is on class path

    // Now load class which depends on OSGi API
    if (isOsgiAvailable) {

      // The OSGIExtensionLoader class will be loaded when the next line
      // is executed, but not prior, and that is why it is safe to directly
      // reference it here.
      OSGiExtensionLoader extLoader = OSGiExtensionLoader.getInstance();
      return extLoader.getExtension(clazz, extensionClassName);
    }

    throw new ExtensionNotLoadedException("Unable to find implementation for " +
          clazz.getName() + ", the class or service " + extensionClassName +
          " could not be located!");
  }
}
