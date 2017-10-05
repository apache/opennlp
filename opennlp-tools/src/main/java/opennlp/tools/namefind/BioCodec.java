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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.util.DFA;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.Span;

public class BioCodec implements SequenceCodec<String> {

  public static final String START = "start";
  public static final String CONTINUE = "cont";
  public static final String OTHER = "other";

  private static final Pattern typedOutcomePattern = Pattern.compile("(.+)-\\w+");

  private String currentType;
  private static final int[][] MOVE_FUNCTION = {
      { 0, 1, DFA.ERROR_STATE, DFA.ERROR_STATE}, // state == 0
      { 1, 1, 1, DFA.ERROR_STATE}                // state == 1
  };

  static String extractNameType(String outcome) {
    Matcher matcher = typedOutcomePattern.matcher(outcome);
    if (matcher.matches()) {
      return matcher.group(1);
    }

    return null;
  }

  public Span[] decode(List<String> c) {
    int start = -1;
    int end = -1;
    List<Span> spans = new ArrayList<>(c.size());
    for (int li = 0; li < c.size(); li++) {
      String chunkTag = c.get(li);
      if (chunkTag.endsWith(BioCodec.START)) {
        if (start != -1) {
          spans.add(new Span(start, end, extractNameType(c.get(li - 1))));
        }

        start = li;
        end = li + 1;

      }
      else if (chunkTag.endsWith(BioCodec.CONTINUE)) {
        end = li + 1;
      }
      else if (chunkTag.endsWith(BioCodec.OTHER)) {
        if (start != -1) {
          spans.add(new Span(start, end, extractNameType(c.get(li - 1))));
          start = -1;
          end = -1;
        }
      }
    }

    if (start != -1) {
      spans.add(new Span(start, end, extractNameType(c.get(c.size() - 1))));
    }

    return spans.toArray(new Span[spans.size()]);
  }

  public String[] encode(Span[] names, int length) {
    String[] outcomes = new String[length];
    for (int i = 0; i < outcomes.length; i++) {
      outcomes[i] = BioCodec.OTHER;
    }
    for (Span name : names) {
      if (name.getType() == null) {
        outcomes[name.getStart()] = "default" + "-" + BioCodec.START;
      }
      else {
        outcomes[name.getStart()] = name.getType() + "-" + BioCodec.START;
      }
      // now iterate from begin + 1 till end
      for (int i = name.getStart() + 1; i < name.getEnd(); i++) {
        if (name.getType() == null) {
          outcomes[i] = "default" + "-" + BioCodec.CONTINUE;
        }
        else {
          outcomes[i] = name.getType() + "-" + BioCodec.CONTINUE;
        }
      }
    }

    return outcomes;
  }

  public NameFinderSequenceValidator createSequenceValidator() {
    return new NameFinderSequenceValidator();
  }

  /*
   * state transition chart
   *
   *                    | state |
   *                    +---+---+
   *       symbol       | 0 | 1 |
   * -------------------+---+---+
   * 0 (other)          | 0 | 1 |
   * 1 (start)          | 1 | 1 |
   * 2 (cont; same type)|ERR| 1 |
   * 3 (cont; diff type)|ERR|ERR|
   *
   * initial state: 0
   * accepting state set: { 1 }
   */
  @Override
  public boolean areOutcomesCompatible(String[] outcomes) {
    DFA dfa = new DFA(0, MOVE_FUNCTION, new int[]{ 1 });
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
    if (outcome.endsWith(BioCodec.OTHER)) {
      return 0;
    }
    else if (outcome.endsWith(BioCodec.START)) {
      currentType = outcome.substring(0, outcome.length() - BioCodec.START.length());
      return 1;
    }
    else if (outcome.endsWith(BioCodec.CONTINUE)) {
      String theType = outcome.substring(0, outcome.length() - BioCodec.CONTINUE.length());
      if (currentType.equals(theType)) {
        return 2;
      }
      else {
        return 3;
      }
    }

    return -1; //unknown symbol
  }
}
