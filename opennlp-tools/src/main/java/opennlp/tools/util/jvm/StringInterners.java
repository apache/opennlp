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

import java.lang.reflect.Constructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides string interning utility methods. Interning mechanism can be configured via the
 * system property {@code opennlp.interner.class} by specifying an implementation via its
 * fully qualified classname. It needs to implement {@link StringInterner}.
 * <p>
 * If not specified by the user, the default interner is {@link CHMStringInterner}.
 */
public class StringInterners {

  private static final Logger LOGGER = LoggerFactory.getLogger(StringInterners.class);
  private static final StringInterner INTERNER;

  static {

    final String clazzName = System.getProperty("opennlp.interner.class",
        CHMStringInterner.class.getCanonicalName());

    try {
      final Class<?> clazz = Class.forName(clazzName);
      final Constructor<?> cons = clazz.getDeclaredConstructor();
      INTERNER = (StringInterner) cons.newInstance();
      LOGGER.debug("Using '{}' as String interner implementation.", clazzName);
    } catch (Exception e) {
      throw new RuntimeException("Could not load specified String interner implementation: '"
          + clazzName + "'. Reason: " + e.getLocalizedMessage(), e);
    }

  }

  /**
   * Interns and returns a reference to the representative instance
   * for any collection of string instances that are equal to each other.
   *
   * @param sample string instance to be interned
   * @return reference to the interned string instance
   */
  public static String intern(String sample) {
    if (sample == null) {
      return null;
    }
    return INTERNER.intern(sample);
  }
}
