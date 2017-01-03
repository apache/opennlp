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

/**
 * The {@link ExtensionLoader} is responsible to load extensions to the OpenNLP library.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ExtensionLoader {

  private static boolean isOsgiAvailable = false;

  private ExtensionLoader() {
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
   */
  // TODO: Throw custom exception if loading fails ...
  @SuppressWarnings("unchecked")
  public static <T> T instantiateExtension(Class<T> clazz, String extensionClassName) {

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
