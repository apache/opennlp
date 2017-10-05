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

import java.util.HashSet;
import java.util.Set;

/**
 * Deterministic Finite Automaton
 */
public class DFA {

  public static final int ERROR_STATE = -1;
  private final int initialState;
  private final int[][] moveFunction;
  private final Set<Integer> acceptingStateSet;
  private int currentState;

  public DFA(int initialState, int[][] moveFunction, int[] acceptingStateSet) {
    this.initialState = initialState;
    this.moveFunction = moveFunction;
    this.acceptingStateSet = new HashSet<>(acceptingStateSet.length);
    for (int acceptingState : acceptingStateSet) {
      this.acceptingStateSet.add(acceptingState);
    }
    this.currentState = initialState;
  }

  /**
   * Do state transition
   * @param symbol
   * @return true if state isn't {@link #ERROR_STATE}
   */
  public boolean read(int symbol) {
    currentState = moveFunction[currentState][symbol];
    return currentState != ERROR_STATE;
  }

  /**
   * Do state transition according to the sequence of the symbols
   * @param symbolSeq sequence of the symbols
   * @return true if the sequence of the symbols is accepted by DFA
   */
  public boolean read(int[] symbolSeq) {
    for (int symbol: symbolSeq) {
      if (!read(symbol))
        return false;
    }
    return accept();
  }

  /**
   * check the final state
   * @return true if the state is accepted by DFA
   */
  public boolean accept() {
    return acceptingStateSet.contains(currentState);
  }

  public void reset() {
    currentState = initialState;
  }
}
