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

package opennlp.tools.relation;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.StringUtil;

/**
 * An extraction rule with a dependency path, output relation type, and optional trigger.
 *
 * <p>The path is a whitespace-separated sequence of steps. Each character that
 * {@link StringUtil#isWhitespace(char)} accepts separates steps, so no-break spaces and
 * other Unicode space separators act like ASCII blanks. A step
 * {@code <label} walks up from the subject's head token over an arc with that relation
 * label; a step {@code >label} walks down toward the object's head token. All up steps
 * precede all down steps. For example, {@code <nsubj >obj} matches a subject and object
 * of the same verb.</p>
 *
 * <p>The optional trigger constrains the pivot token, the highest token on the path: the
 * pattern matches only when the pivot's form, lowercased with
 * {@link StringUtil#toLowerCase(CharSequence)}, equals the trigger. Without a trigger the
 * path alone decides. The trigger is not rewritten and must already be lowercased under
 * that mapping, so {@code founded} is accepted and {@code Founded} is rejected.</p>
 *
 * @param type The relation type to emit. Must not be {@code null} or blank.
 * @param path The path shape as described above. Must not be {@code null} or blank,
 *             direction markers may appear only at the start of a step, and all up
 *             steps must precede the first down step.
 * @param trigger The required pivot form, or {@code null} for any pivot. Must not be
 *                blank or contain whitespace, since it is matched against a single
 *                token, and must be unchanged by
 *                {@link StringUtil#toLowerCase(CharSequence)}.
 *
 * @since 3.0.0
 */
public record RelationPattern(String type, String path, String trigger) {

  /** The step prefix walking up from the subject's head token toward the pivot token. */
  public static final char UP_STEP = '<';

  /** The step prefix walking down from the pivot token toward the object's head token. */
  public static final char DOWN_STEP = '>';

  /**
   * Validates the rule.
   *
   * @throws IllegalArgumentException Thrown if {@code type} or {@code path} is
   *         {@code null} or blank, {@code path} is malformed, or {@code trigger} is
   *         blank, contains whitespace, or is not already lowercased under
   *         {@link StringUtil#toLowerCase(CharSequence)}.
   */
  public RelationPattern {
    if (type == null || StringUtil.isBlank(type)) {
      throw new IllegalArgumentException("type must not be null or blank");
    }
    if (path == null || StringUtil.isBlank(path)) {
      throw new IllegalArgumentException("path must not be null or blank");
    }
    if (trigger != null) {
      if (trigger.isEmpty()) {
        throw new IllegalArgumentException("trigger must not be blank");
      }
      for (int i = 0; i < trigger.length(); ) {
        final int cp = trigger.codePointAt(i);
        if (StringUtil.isWhitespace(cp)) {
          throw new IllegalArgumentException("trigger must not contain whitespace,"
              + " since it is matched against a single token: " + trigger);
        }
        i += Character.charCount(cp);
      }
      if (!StringUtil.toLowerCase(trigger).equals(trigger)) {
        throw new IllegalArgumentException("trigger must be lowercased, but was: " + trigger);
      }
    }
    boolean down = false;
    for (final String step : splitSteps(path)) {
      if (!isValidStep(step)) {
        throw new IllegalArgumentException("not a valid path step: " + step);
      }
      if (step.charAt(0) == DOWN_STEP) {
        down = true;
      } else if (down) {
        throw new IllegalArgumentException(
            "up steps must come before down steps: " + path);
      }
    }
  }

  /**
   * Splits the path into its steps.
   *
   * @return The steps in order. Never {@code null} and never empty.
   */
  public List<String> steps() {
    return splitSteps(path);
  }

  /** Returns whether a step has one direction marker followed by a relation label. */
  private static boolean isValidStep(String step) {
    if (step.length() < 2 || (step.charAt(0) != UP_STEP && step.charAt(0) != DOWN_STEP)) {
      return false;
    }
    for (int i = 1; i < step.length(); i++) {
      if (step.charAt(i) == UP_STEP || step.charAt(i) == DOWN_STEP) {
        return false;
      }
    }
    return true;
  }

  /**
   * Splits a path on every character {@link StringUtil#isWhitespace(char)} accepts. Runs
   * of consecutive separators never produce empty steps.
   *
   * @param path The path to split. Must not be {@code null}.
   * @return The whitespace-free steps in order. Never {@code null}.
   */
  private static List<String> splitSteps(String path) {
    final List<String> steps = new ArrayList<>();
    int start = -1;
    for (int i = 0; i <= path.length(); i++) {
      if (i == path.length() || StringUtil.isWhitespace(path.charAt(i))) {
        if (start >= 0) {
          steps.add(path.substring(start, i));
          start = -1;
        }
      } else if (start < 0) {
        start = i;
      }
    }
    return steps;
  }
}
