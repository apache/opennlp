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

package opennlp.tools.namefind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.util.DFA;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.Span;

public class BilouCodec implements SequenceCodec<String> {

  public static final String START = "start";
  public static final String CONTINUE = "cont";
  public static final String LAST = "last";
  public static final String UNIT = "unit";
  public static final String OTHER = "other";

  private String currentType;
  private static final int[][] MOVE_FUNCTION = {
      { 0, 1, DFA.ERROR_STATE, DFA.ERROR_STATE, DFA.ERROR_STATE,
          DFA.ERROR_STATE, 2},                // state == 0
      { DFA.ERROR_STATE, DFA.ERROR_STATE, 1, DFA.ERROR_STATE, 2,
          DFA.ERROR_STATE, DFA.ERROR_STATE},  // state == 1
      { 2, 1, DFA.ERROR_STATE, DFA.ERROR_STATE, DFA.ERROR_STATE,
          DFA.ERROR_STATE, 2}                 // state == 2
  };

  @Override
  public Span[] decode(List<String> c) {
    int start = -1;
    int end = -1;
    List<Span> spans = new ArrayList<>(c.size());
    for (int li = 0; li < c.size(); li++) {
      String chunkTag = c.get(li);
      if (chunkTag.endsWith(BilouCodec.START)) {
        start = li;
        end = li + 1;
      }
      else if (chunkTag.endsWith(BilouCodec.CONTINUE)) {
        end = li + 1;
      }
      else if (chunkTag.endsWith(LAST)) {
        if (start != -1) {
          spans.add(new Span(start, end + 1, BioCodec.extractNameType(c.get(li - 1))));
          start = -1;
          end = -1;
        }
      }
      else if (chunkTag.endsWith(UNIT)) {
        spans.add(new Span(li, li + 1, BioCodec.extractNameType(c.get(li))));
      }
    }

    return spans.toArray(new Span[spans.size()]);
  }

  @Override
  public String[] encode(Span[] names, int length) {
    String[] outcomes = new String[length];
    Arrays.fill(outcomes, BilouCodec.OTHER);

    for (Span name : names) {

      if (name.length() > 1) {
        if (name.getType() == null) {
          outcomes[name.getStart()] = "default" + "-" + BilouCodec.START;
        }
        else {
          outcomes[name.getStart()] = name.getType() + "-" + BilouCodec.START;
        }
        // now iterate from begin + 1 till end
        for (int i = name.getStart() + 1; i < name.getEnd() - 1; i++) {
          if (name.getType() == null) {
            outcomes[i] = "default" + "-" + BilouCodec.CONTINUE;
          }
          else {
            outcomes[i] = name.getType() + "-" + BilouCodec.CONTINUE;
          }
        }

        if (name.getType() == null) {
          outcomes[name.getEnd() - 1] = "default" + "-" + BilouCodec.LAST;
        }
        else {
          outcomes[name.getEnd() - 1] = name.getType() + "-" + BilouCodec.LAST;
        }
      }
      else {
        if (name.getType() == null) {
          outcomes[name.getEnd() - 1] = "default" + "-" + BilouCodec.UNIT;
        }
        else {
          outcomes[name.getEnd() - 1] = name.getType() + "-" + BilouCodec.UNIT;
        }
      }
    }

    return outcomes;
  }

  @Override
  public SequenceValidator<String> createSequenceValidator() {
    return new BilouNameFinderSequenceValidator();
  }

  /*
   * state transition chart
   *
   *                    |   state   |
   *                    +---+---+---+
   *       symbol       | 0 | 1 | 2 |
   * -------------------+---+---+---+
   * 0 (other)          | 0 |ERR| 2 |
   * 1 (start)          | 1 |ERR| 1 |
   * 2 (cont; same type)|ERR| 1 |ERR|
   * 3 (cont; diff type)|ERR|ERR|ERR|
   * 4 (last; same type)|ERR| 2 |ERR|
   * 5 (last; diff type)|ERR|ERR|ERR|
   * 6 (unit)           | 2 |ERR| 2 |
   *
   * initial state: 0
   * accepting state set: { 2 }
   */
  @Override
  public boolean areOutcomesCompatible(String[] outcomes) {
    DFA dfa = new DFA(0, MOVE_FUNCTION, new int[]{ 2 });
    for (String outcome: outcomes) {
      int symbol = getSymbol(outcome);
      if (symbol == -1) {
        return false;
      }
      else {
        if (!dfa.read(symbol)) {
          return false;
        }
      }
    }
    return dfa.accept();
  }

  private int getSymbol(String outcome) {
    if (outcome.endsWith(BilouCodec.OTHER)) {
      return 0;
    }
    else if (outcome.endsWith(BilouCodec.START)) {
      currentType = outcome.substring(0, outcome.length() - BilouCodec.START.length());
      return 1;
    }
    else if (outcome.endsWith(BilouCodec.CONTINUE)) {
      String theType = outcome.substring(0, outcome.length() - BilouCodec.CONTINUE.length());
      if (currentType.equals(theType)) {
        return 2;
      }
      else {
        return 3;
      }
    }
    else if (outcome.endsWith(BilouCodec.LAST)) {
      String theType = outcome.substring(0, outcome.length() - BilouCodec.LAST.length());
      if (currentType.equals(theType)) {
        return 4;
      }
      else {
        return 5;
      }
    }
    else if (outcome.endsWith(BilouCodec.UNIT)) {
      return 6;
    }

    return -1; //unknown symbol
  }
}
