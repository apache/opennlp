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

package opennlp.tools.parser;

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.SequenceValidator;

public class ParserChunkerSequenceValidator implements SequenceValidator<String> {

  private Map<String, String> continueStartMap;

  public ParserChunkerSequenceValidator(String outcomes[]) {

    continueStartMap = new HashMap<>(outcomes.length);
    for (int oi = 0, on = outcomes.length; oi < on; oi++) {
      String outcome = outcomes[oi];
      if (outcome.startsWith(Parser.CONT)) {
        continueStartMap.put(outcome,Parser.START + outcome.substring(
            Parser.CONT.length()));
      }
    }
  }

  public boolean validSequence(int i, String[] inputSequence,
      String[] tagList, String outcome) {
    if (continueStartMap.containsKey(outcome)) {
      int lti = tagList.length - 1;

      if (lti == -1) {
        return false;
      }
      else {
        String lastTag = tagList[lti];

        if (lastTag.equals(outcome)) {
          return true;
        }

        if (lastTag.equals(continueStartMap.get(outcome))) {
          return true;
        }

        if (lastTag.equals(Parser.OTHER)) {
          return false;
        }
        return false;
      }
    }
    return true;
  }
}