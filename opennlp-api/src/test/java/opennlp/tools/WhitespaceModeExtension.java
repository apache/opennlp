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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import opennlp.tools.util.WhitespaceMode;

/**
 * Keeps the process-wide {@link WhitespaceMode} of a test class from leaking into other
 * tests. Registered through {@link ResetWhitespaceMode} or {@link PinWhitespaceMode}.
 * <p>
 * Without a {@link PinWhitespaceMode} on the test class, the mode is reset to property
 * resolution before the first test and after each test. With one, the pinned mode is
 * activated before the first {@code @BeforeAll} method of the class and again after each
 * test. In both cases the mode is reset to property resolution after the last test.
 */
final class WhitespaceModeExtension
    implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {

  /**
   * {@inheritDoc}
   */
  @Override
  public void beforeAll(ExtensionContext context) {
    activatePinOrReset(context);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void afterEach(ExtensionContext context) {
    activatePinOrReset(context);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void afterAll(ExtensionContext context) {
    WhitespaceMode.reset();
  }

  /**
   * Activates the mode of the {@link PinWhitespaceMode} on the test class, or resets the
   * mode to property resolution if the class has none.
   *
   * @param context The {@link ExtensionContext} of the current test or test class.
   */
  private void activatePinOrReset(ExtensionContext context) {
    AnnotationSupport.findAnnotation(context.getRequiredTestClass(), PinWhitespaceMode.class)
        .ifPresentOrElse(pin -> WhitespaceMode.setActive(pin.value()), WhitespaceMode::reset);
  }
}
