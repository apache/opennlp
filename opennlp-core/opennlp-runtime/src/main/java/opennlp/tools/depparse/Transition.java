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

package opennlp.tools.depparse;

import opennlp.tools.util.StringUtil;

/**
 * One action of the arc-standard transition system: shift the next buffer token onto the
 * stack, or attach one of the two topmost stack tokens to the other under a relation label.
 *
 * <p>A transition doubles as a classification outcome: {@link #encode()} renders it as the
 * outcome string a model is trained on, and {@link #decode(String)} restores it.</p>
 *
 * @param type The kind of action. Must not be {@code null}.
 * @param label The relation label for arc actions, {@code null} for {@link Type#SHIFT}.
 *
 * @since 3.0.0
 */
public record Transition(Type type, String label) {

  /**
   * The kinds of arc-standard action.
   */
  public enum Type {
    /** Pushes the front of the buffer onto the stack. */
    SHIFT,
    /** Attaches the second stack token to the top one and removes the second. */
    LEFT_ARC,
    /** Attaches the top stack token to the second one and removes the top. */
    RIGHT_ARC
  }

  /** The single shift transition; shifts carry no label. */
  public static final Transition SHIFT = new Transition(Type.SHIFT, null);

  private static final char SEPARATOR = ':';

  /**
   * Validates the pairing of type and label.
   *
   * @throws IllegalArgumentException Thrown if {@code type} is {@code null}, a shift
   *         carries a label, or an arc action has a {@code null} or blank label.
   */
  public Transition {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (type == Type.SHIFT) {
      if (label != null) {
        throw new IllegalArgumentException("a shift must not carry a label: " + label);
      }
    } else if (label == null || StringUtil.isBlank(label)) {
      throw new IllegalArgumentException("an arc transition needs a relation label");
    }
  }


  /**
   * Creates a left-arc transition.
   *
   * @param label The relation label. Must not be {@code null} or blank.
   * @return A {@link Transition} of {@link Type#LEFT_ARC}. Never {@code null}.
   */
  public static Transition leftArc(String label) {
    return new Transition(Type.LEFT_ARC, label);
  }

  /**
   * Creates a right-arc transition.
   *
   * @param label The relation label. Must not be {@code null} or blank.
   * @return A {@link Transition} of {@link Type#RIGHT_ARC}. Never {@code null}.
   */
  public static Transition rightArc(String label) {
    return new Transition(Type.RIGHT_ARC, label);
  }

  /**
   * Renders the transition as a model outcome string, for example {@code SHIFT} or
   * {@code LEFT_ARC:nsubj}.
   *
   * @return The outcome string. Never {@code null}.
   */
  public String encode() {
    return type == Type.SHIFT ? type.name() : type.name() + SEPARATOR + label;
  }

  /**
   * Restores a transition from a model outcome string produced by {@link #encode()}.
   *
   * @param outcome The outcome string. Must not be {@code null}.
   * @return The decoded {@link Transition}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code outcome} is {@code null} or does not
   *         name a valid transition.
   */
  public static Transition decode(String outcome) {
    if (outcome == null) {
      throw new IllegalArgumentException("outcome must not be null");
    }
    if (Type.SHIFT.name().equals(outcome)) {
      return SHIFT;
    }
    final int separator = outcome.indexOf(SEPARATOR);
    if (separator < 0) {
      throw new IllegalArgumentException("not a transition outcome: " + outcome);
    }
    final Type type;
    try {
      type = Type.valueOf(outcome.substring(0, separator));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("not a transition outcome: " + outcome, e);
    }
    return new Transition(type, outcome.substring(separator + 1));
  }
}
