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


/**
 * The mutable configuration of an arc-standard parse: a stack, a buffer of remaining
 * tokens, and the arcs assigned so far.
 *
 * <p>The stack bottom holds the artificial root, exposed as {@link #ROOT}. Positions that
 * do not exist, such as the second stack element in the initial configuration, are exposed
 * as {@link #NONE}. A right arc from the artificial root is only applicable once the buffer
 * is empty and the root is the only other stack element, which guarantees every completed
 * parse has exactly one sentence root.</p>
 *
 * <p>Instances are confined to a single parse and must not be shared between threads.</p>
 *
 * @since 3.0.0
 */
public final class ArcStandardState {

  /** The stack value representing the artificial root node. */
  public static final int ROOT = -1;

  /** The value returned for stack or buffer positions that do not exist. */
  public static final int NONE = -2;

  private final int tokenCount;
  private final int[] stack;
  private final int[] heads;
  private final String[] relations;
  private final int[] assignedDependents;
  private final int[] leftmostDependents;
  private final int[] rightmostDependents;

  private int top;
  private int bufferFront;

  /**
   * Initializes the start configuration for a sentence: the artificial root on the stack
   * and every token in the buffer.
   *
   * @param tokenCount The number of tokens in the sentence. Must be greater than zero.
   * @throws IllegalArgumentException Thrown if {@code tokenCount} is not positive.
   */
  public ArcStandardState(int tokenCount) {
    if (tokenCount <= 0) {
      throw new IllegalArgumentException("tokenCount must be positive: " + tokenCount);
    }
    this.tokenCount = tokenCount;
    this.stack = new int[tokenCount + 1];
    this.stack[0] = ROOT;
    this.top = 0;
    this.bufferFront = 0;
    this.heads = new int[tokenCount];
    this.relations = new String[tokenCount];
    this.assignedDependents = new int[tokenCount];
    this.leftmostDependents = new int[tokenCount];
    this.rightmostDependents = new int[tokenCount];
    java.util.Arrays.fill(this.leftmostDependents, NONE);
    java.util.Arrays.fill(this.rightmostDependents, NONE);
  }

  /**
   * @return {@code true} if the buffer is empty and only the artificial root remains on
   *         the stack, so the parse is complete.
   */
  public boolean isTerminal() {
    return bufferFront == tokenCount && top == 0;
  }

  /**
   * Checks whether a transition may be applied in the current configuration.
   *
   * @param transition The transition to check. Must not be {@code null}.
   * @return {@code true} if {@link #apply(Transition)} would succeed.
   * @throws IllegalArgumentException Thrown if {@code transition} is {@code null}.
   */
  public boolean canApply(Transition transition) {
    if (transition == null) {
      throw new IllegalArgumentException("transition must not be null");
    }
    return switch (transition.type()) {
      case SHIFT -> bufferFront < tokenCount;
      case LEFT_ARC -> top >= 2;
      case RIGHT_ARC -> top >= 2 || (top == 1 && bufferFront == tokenCount);
    };
  }

  /**
   * Applies a transition, updating stack, buffer, and arcs.
   *
   * @param transition The transition to apply. Must not be {@code null} and must be
   *                   applicable per {@link #canApply(Transition)}.
   * @throws IllegalArgumentException Thrown if the transition is {@code null} or not
   *         applicable in the current configuration.
   */
  public void apply(Transition transition) {
    if (!canApply(transition)) {
      throw new IllegalArgumentException("transition not applicable: " + transition
          + " in " + this);
    }
    switch (transition.type()) {
      case SHIFT -> {
        top++;
        stack[top] = bufferFront++;
      }
      case LEFT_ARC -> {
        final int dependent = stack[top - 1];
        attach(stack[top], dependent, transition.label());
        stack[top - 1] = stack[top];
        top--;
      }
      case RIGHT_ARC -> {
        attach(stack[top - 1], stack[top], transition.label());
        top--;
      }
      default -> throw new IllegalArgumentException("unsupported type: " + transition.type());
    }
  }

  private void attach(int head, int dependent, String relation) {
    heads[dependent] = head;
    relations[dependent] = relation;
    if (head >= 0) {
      assignedDependents[head]++;
      if (leftmostDependents[head] == NONE || dependent < leftmostDependents[head]) {
        leftmostDependents[head] = dependent;
      }
      if (rightmostDependents[head] == NONE || dependent > rightmostDependents[head]) {
        rightmostDependents[head] = dependent;
      }
    }
  }

  /**
   * Retrieves a stack element counted from the top.
   *
   * @param fromTop Zero for the top element, one for the element below it, and so on.
   *                Must not be negative.
   * @return The token index at that position, {@link #ROOT} for the artificial root, or
   *         {@link #NONE} if the position does not exist.
   * @throws IllegalArgumentException Thrown if {@code fromTop} is negative.
   */
  public int stack(int fromTop) {
    if (fromTop < 0) {
      throw new IllegalArgumentException("fromTop must not be negative: " + fromTop);
    }
    final int position = top - fromTop;
    return position < 0 ? NONE : stack[position];
  }

  /**
   * Retrieves a buffer element counted from the front.
   *
   * @param fromFront Zero for the next token to be shifted, one for the token after it,
   *                  and so on. Must not be negative.
   * @return The token index at that position, or {@link #NONE} if the position does not
   *         exist.
   * @throws IllegalArgumentException Thrown if {@code fromFront} is negative.
   */
  public int buffer(int fromFront) {
    if (fromFront < 0) {
      throw new IllegalArgumentException("fromFront must not be negative: " + fromFront);
    }
    final int position = bufferFront + fromFront;
    return position >= tokenCount ? NONE : position;
  }

  /**
   * @return The number of stack elements including the artificial root.
   */
  public int stackSize() {
    return top + 1;
  }

  /**
   * @return The number of tokens still in the buffer.
   */
  public int bufferSize() {
    return tokenCount - bufferFront;
  }

  /**
   * Retrieves how many dependents have been attached to a token so far.
   *
   * @param index The zero-based token index. Must be within {@code [0, tokenCount)}.
   * @return The number of arcs assigned with the token as head.
   * @throws IllegalArgumentException Thrown if {@code index} is out of range.
   */
  public int assignedDependents(int index) {
    checkTokenIndex(index);
    return assignedDependents[index];
  }

  private void checkTokenIndex(int index) {
    if (index < 0 || index >= tokenCount) {
      throw new IllegalArgumentException("token index out of range: " + index);
    }
  }

  /**
   * Retrieves the leftmost dependent attached to a token so far.
   *
   * @param index The zero-based token index. Must be within {@code [0, tokenCount)}.
   * @return The dependent's token index, or {@link #NONE} when none is attached.
   * @throws IllegalArgumentException Thrown if {@code index} is out of range.
   */
  public int leftmostDependent(int index) {
    checkTokenIndex(index);
    return leftmostDependents[index];
  }

  /**
   * Retrieves the rightmost dependent attached to a token so far.
   *
   * @param index The zero-based token index. Must be within {@code [0, tokenCount)}.
   * @return The dependent's token index, or {@link #NONE} when none is attached.
   * @throws IllegalArgumentException Thrown if {@code index} is out of range.
   */
  public int rightmostDependent(int index) {
    checkTokenIndex(index);
    return rightmostDependents[index];
  }

  /**
   * Retrieves the relation a token was attached under, when it has been attached.
   *
   * @param index The zero-based token index. Must be within {@code [0, tokenCount)}.
   * @return The relation label, or {@code null} when the token is still unattached.
   * @throws IllegalArgumentException Thrown if {@code index} is out of range.
   */
  public String assignedRelation(int index) {
    checkTokenIndex(index);
    return relations[index];
  }

  /**
   * Builds the {@link DependencyGraph} of a completed parse.
   *
   * @return The parsed graph. Never {@code null}.
   * @throws IllegalStateException Thrown if the parse is not yet terminal.
   */
  public DependencyGraph toGraph() {
    if (!isTerminal()) {
      throw new IllegalStateException("parse is not terminal: " + this);
    }
    return DependencyGraph.of(heads, relations);
  }

  @Override
  public String toString() {
    return "stackSize=" + stackSize() + ", bufferSize=" + bufferSize()
        + ", tokenCount=" + tokenCount;
  }
}
