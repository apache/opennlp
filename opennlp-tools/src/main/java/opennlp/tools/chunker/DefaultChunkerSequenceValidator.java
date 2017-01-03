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

package opennlp.tools.chunker;

import opennlp.tools.util.SequenceValidator;

public class DefaultChunkerSequenceValidator implements SequenceValidator<String> {

  private boolean validOutcome(String outcome, String prevOutcome) {
    if (outcome.startsWith("I-")) {
      if (prevOutcome == null) {
        return false;
      }
      else {
        if (prevOutcome.equals("O")) {
          return false;
        }
        if (!prevOutcome.substring(2).equals(outcome.substring(2))) {
          return false;
        }
      }
    }
    return true;
  }

  protected boolean validOutcome(String outcome, String[] sequence) {
    String prevOutcome = null;
    if (sequence.length > 0) {
      prevOutcome = sequence[sequence.length - 1];
    }
    return validOutcome(outcome,prevOutcome);
  }

  public boolean validSequence(int i, String[] sequence, String[] s, String outcome) {
    return validOutcome(outcome, s);
  }

}
