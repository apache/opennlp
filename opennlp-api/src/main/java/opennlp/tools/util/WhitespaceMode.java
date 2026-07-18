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

package opennlp.tools.util;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects the whitespace definition used by {@link StringUtil#isWhitespace(char)} and
 * {@link StringUtil#isWhitespace(int)}: the Unicode {@code White_Space} property, or
 * OpenNLP's legacy definition from 1.x/2.x.
 * <p>
 * Resolved from the {@value #MODE_PROPERTY} system property when this class is initialized
 * and shared process-wide, so a model is trained and decoded under one definition. Tests and
 * embedders may override the mode via {@link #setActive(WhitespaceMode)} and {@link #reset()}.
 *
 * @since 3.0.0
 */
public enum WhitespaceMode {

  /**
   * OpenNLP 1.x/2.x whitespace: the union of {@link Character#isWhitespace(int)} and the
   * Unicode {@code Zs} category. Restores byte-identical tokenization, corpus parsing, and
   * feature generation for models trained under this definition.
   */
  LEGACY,

  /**
   * The Unicode {@code White_Space} property. The default from 3.0 onward.
   */
  UNICODE;

  /**
   * System property that selects the active {@link WhitespaceMode} at startup. Accepts
   * {@code LEGACY} or {@code UNICODE}, case-insensitive; unset or blank resolves to
   * {@link #UNICODE}, any other value raises an {@link IllegalArgumentException} when the
   * mode is resolved.
   */
  public static final String MODE_PROPERTY = "opennlp.whitespace.mode";

  private static final Logger logger = LoggerFactory.getLogger(WhitespaceMode.class);
  private static final AtomicBoolean LEGACY_WARNED = new AtomicBoolean();

  private static volatile WhitespaceMode active = fromProperty();

  /**
   * Returns the active {@link WhitespaceMode}: the value resolved from the
   * {@value #MODE_PROPERTY} system property when this class was initialized, or the value
   * most recently passed to {@link #setActive(WhitespaceMode)}.
   *
   * @return The active {@link WhitespaceMode}.
   */
  public static WhitespaceMode current() {
    return active;
  }

  /**
   * Overrides the active {@link WhitespaceMode} for the whole process, taking precedence
   * over the {@value #MODE_PROPERTY} system property. Intended for tests and embedders;
   * callers pinning a mode temporarily should call {@link #reset()} afterward.
   *
   * @param mode The {@link WhitespaceMode} to activate. Must not be {@code null}.
   *
   * @throws IllegalArgumentException If {@code mode} is {@code null}.
   */
  public static void setActive(WhitespaceMode mode) {
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    active = mode;
    warnIfLegacy(mode);
  }

  /**
   * Discards any override set via {@link #setActive(WhitespaceMode)} and re-resolves the
   * active mode from the {@value #MODE_PROPERTY} system property.
   *
   * @throws IllegalArgumentException If the property holds a value other than
   *     {@code LEGACY} or {@code UNICODE} (case-insensitive); the previous mode is retained.
   */
  public static void reset() {
    active = fromProperty();
  }

  /**
   * Resolves the mode from the {@value #MODE_PROPERTY} system property; unset or blank
   * resolves to {@link #UNICODE}. Warns once per process when {@link #LEGACY} is selected.
   */
  private static WhitespaceMode fromProperty() {
    String value = System.getProperty(MODE_PROPERTY);
    WhitespaceMode mode;
    if (value == null || value.isBlank()) {
      mode = UNICODE;
    } else {
      try {
        mode = WhitespaceMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid value '" + value + "' for system property '"
            + MODE_PROPERTY + "': expected LEGACY or UNICODE", e);
      }
    }
    warnIfLegacy(mode);
    return mode;
  }

  /**
   * Logs the legacy-mode removal warning, once per process.
   */
  private static void warnIfLegacy(WhitespaceMode mode) {
    if (mode == LEGACY && LEGACY_WARNED.compareAndSet(false, true)) {
      logger.warn("Using the legacy (pre-3.0) whitespace definition for tokenization, corpus " +
          "format parsing, and feature generation. This compatibility mode is scheduled for " +
          "removal in 4.0.");
    }
  }
}
