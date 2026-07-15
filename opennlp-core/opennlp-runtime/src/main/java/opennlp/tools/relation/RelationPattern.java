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
 * One extraction rule for {@link RelationAnnotator}: a dependency path shape between two
 * entity heads and the relation type to emit when the shape matches.
 *
 * <p>The path is a space-separated sequence of steps. A step {@code <label} walks up
 * from the subject's head token over an arc with that relation label; a step
 * {@code >label} walks down toward the object's head token. All up steps come before all
 * down steps, so the path always runs from the subject up to a single pivot token and
 * down to the object. {@code <nsubj >obj} matches a subject and object of the same verb;
 * {@code >nmod} matches an object directly attached below the subject.</p>
 *
 * <p>The optional trigger constrains the pivot token, the highest token on the path: the
 * pattern matches only when the pivot's lowercased form equals the trigger. Without a
 * trigger the path shape alone decides.</p>
 *
 * @param type The relation type to emit. Must not be {@code null} or blank.
 * @param path The path shape as described above. Must not be {@code null} or blank, and
 *             every up step must come before the first down step.
 * @param trigger The required lowercased pivot form, or {@code null} for any pivot. Must
 *                not be blank.
 *
 * @since 3.0.0
 */
public record RelationPattern(String type, String path, String trigger) {

  /**
   * Validates the rule.
   *
   * @throws IllegalArgumentException Thrown if {@code type} or {@code path} is
   *         {@code null} or blank, {@code path} is malformed, or {@code trigger} is
   *         blank.
   */
  public RelationPattern {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("type must not be null or blank");
    }
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("path must not be null or blank");
    }
    if (trigger != null && trigger.isBlank()) {
      throw new IllegalArgumentException("trigger must not be blank");
    }
    boolean down = false;
    for (final String step : splitSteps(path)) {
      if (step.length() < 2 || (step.charAt(0) != '<' && step.charAt(0) != '>')) {
        throw new IllegalArgumentException("not a valid path step: " + step);
      }
      if (step.charAt(0) == '>') {
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
   * @return The steps in order. Never {@code null} or empty.
   */
  public List<String> steps() {
    return splitSteps(path);
  }

  /**
   * Splits a path on whitespace with a single character scan.
   *
   * @param path The path to split.
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
