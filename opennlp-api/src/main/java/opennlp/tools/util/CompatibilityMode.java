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
 * Selects, for the classes that corrected their output in 3.0.0, between the corrected output
 * and the output of the 1.x/2.x releases. A model trained under the old output may depend on
 * it until it is retrained. Each class that consults the mode documents what differs.
 * <p>
 * Resolved from the {@value #MODE_PROPERTY} system property when this class is initialized
 * and shared process-wide, so a model is trained and decoded under one mode. Tests and
 * embedders may override the mode via {@link #setActive(CompatibilityMode)} and
 * {@link #reset()}. The mode is independent of {@link WhitespaceMode}.
 *
 * @since 3.0.0
 */
public enum CompatibilityMode {

  /**
   * The output of OpenNLP 1.x/2.x. Restores byte-identical output for models trained with
   * those releases in the classes that consult the mode.
   */
  LEGACY,

  /**
   * The corrected output. The default from 3.0 onward.
   */
  CURRENT;

  /**
   * System property that selects the active {@link CompatibilityMode} at startup. Accepts
   * {@code LEGACY} or {@code CURRENT}, case-insensitive; unset or blank resolves to
   * {@link #CURRENT}, any other value raises an {@link IllegalArgumentException} when the
   * mode is resolved.
   */
  public static final String MODE_PROPERTY = "opennlp.compat.mode";

  private static final Logger logger = LoggerFactory.getLogger(CompatibilityMode.class);
  private static final AtomicBoolean LEGACY_WARNED = new AtomicBoolean();

  private static volatile CompatibilityMode active = fromProperty();

  /**
   * Returns the active {@link CompatibilityMode}: the value resolved from the
   * {@value #MODE_PROPERTY} system property when this class was initialized, or the value
   * most recently passed to {@link #setActive(CompatibilityMode)}.
   *
   * @return The active {@link CompatibilityMode}.
   */
  public static CompatibilityMode current() {
    return active;
  }

  /**
   * Overrides the active {@link CompatibilityMode} for the whole process, taking precedence
   * over the {@value #MODE_PROPERTY} system property. Intended for tests and embedders;
   * callers pinning a mode temporarily should call {@link #reset()} afterward.
   *
   * @param mode The {@link CompatibilityMode} to activate. Must not be {@code null}.
   *
   * @throws IllegalArgumentException Thrown if {@code mode} is {@code null}.
   */
  public static void setActive(CompatibilityMode mode) {
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    active = mode;
    warnIfLegacy(mode);
  }

  /**
   * Discards any override set via {@link #setActive(CompatibilityMode)} and re-resolves the
   * active mode from the {@value #MODE_PROPERTY} system property.
   *
   * @throws IllegalArgumentException Thrown if the property holds a value other than
   *     {@code LEGACY} or {@code CURRENT} (case-insensitive); the previous mode is retained.
   */
  public static void reset() {
    active = fromProperty();
  }

  /**
   * Resolves the mode from the {@value #MODE_PROPERTY} system property; unset or blank
   * resolves to {@link #CURRENT}. Warns once per process when {@link #LEGACY} is selected.
   */
  private static CompatibilityMode fromProperty() {
    String value = System.getProperty(MODE_PROPERTY);
    CompatibilityMode mode;
    if (value == null || value.isBlank()) {
      mode = CURRENT;
    } else {
      try {
        mode = CompatibilityMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid value '" + value + "' for system property '"
            + MODE_PROPERTY + "': expected LEGACY or CURRENT", e);
      }
    }
    warnIfLegacy(mode);
    return mode;
  }

  /**
   * Logs the legacy-mode removal warning, once per process.
   */
  private static void warnIfLegacy(CompatibilityMode mode) {
    if (mode == LEGACY && LEGACY_WARNED.compareAndSet(false, true)) {
      logger.warn("Using the legacy (pre-3.0) output of the classes that consult " + MODE_PROPERTY
          + ". This compatibility mode is scheduled for removal in 4.0.");
    }
  }
}
