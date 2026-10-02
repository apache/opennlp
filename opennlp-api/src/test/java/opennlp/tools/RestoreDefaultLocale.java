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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/**
 * Restores the JVM default {@link Locale} after each test of the annotated class, so a test
 * may call {@link Locale#setDefault(Locale)} without leaking the change into later tests.
 * The class also holds the {@link Resources#LOCALE} lock, so it never runs concurrently with
 * other tests that read or change the default locale.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RestoreDefaultLocale.DefaultLocaleExtension.class)
@ResourceLock(Resources.LOCALE)
public @interface RestoreDefaultLocale {

  /**
   * Turkish folds {@code 'I'} to the dotless {@code 'ı'} (U+0131) instead of {@code 'i'},
   * which makes it the canonical probe for case mapping that depends on the default locale.
   */
  Locale TURKISH = Locale.of("tr", "TR");

  /**
   * Saves the default {@link Locale} before each test and restores it afterward.
   */
  final class DefaultLocaleExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(DefaultLocaleExtension.class);

    private static final String DEFAULT_LOCALE_KEY = "defaultLocale";

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeEach(ExtensionContext context) {
      context.getStore(NAMESPACE).put(DEFAULT_LOCALE_KEY, Locale.getDefault());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterEach(ExtensionContext context) {
      Locale.setDefault(context.getStore(NAMESPACE).get(DEFAULT_LOCALE_KEY, Locale.class));
    }
  }
}
