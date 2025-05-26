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
import java.lang.reflect.InvocationTargetException;

import opennlp.tools.commons.Internal;

/**
 * The {@link ExtensionLoader} is responsible to load extensions to the OpenNLP library.
 * <p>
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class ExtensionLoader {

  private ExtensionLoader() {
  }

  // Pass in the type (interface) of the class to load
  /**
   * Instantiates a user provided extension to OpenNLP.
   * <p>
   * The extension is loaded from the class path.
   * <p>
   * Initially, the load is conducted using the public no-arg constructor.
   * If no such constructor is not found, it is checked if the class follows the
   * {@code Singleton} pattern: a static field named {@code INSTANCE} that
   * returns an object of the type {@link T}.
   *
   * @param clazz A reference to {@link Class<T>}.
   * @param extensionClassName The (fully-qualified) name of the class
   *                           by which the extension shall be loaded.
   *
   * @return the instance of the extension class
   *
   * @throws ExtensionNotLoadedException Thrown if the load operation failed.
   */
  @SuppressWarnings("unchecked")
  public static <T> T instantiateExtension(Class<T> clazz, String extensionClassName) {

    // First try to load extension and instantiate extension from class path
    try {
      Class<?> extClazz = Class.forName(extensionClassName);

      if (clazz.isAssignableFrom(extClazz)) {

        try {
          return (T) extClazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | NoSuchMethodException e) {
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
        } catch (InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      }
      else {
        throw new ExtensionNotLoadedException("Extension class '" + extClazz.getName() +
                "' needs to have type: " + clazz.getName());
      }
    } catch (ClassNotFoundException e) {
      // Class is not on classpath
    }

    throw new ExtensionNotLoadedException("Unable to find implementation for " +
          clazz.getName() + ", the class or service " + extensionClassName +
          " could not be located!");
  }
}
