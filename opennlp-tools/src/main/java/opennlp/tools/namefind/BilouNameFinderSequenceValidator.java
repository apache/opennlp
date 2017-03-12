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

import opennlp.tools.util.SequenceValidator;

public class BilouNameFinderSequenceValidator implements
    SequenceValidator<String> {

  public boolean validSequence(int i, String[] inputSequence,
                               String[] outcomesSequence, String outcome) {

    if (outcome.endsWith(BilouCodec.CONTINUE) || outcome.endsWith(BilouCodec.LAST)) {

      int li = outcomesSequence.length - 1;

      if (li == -1) {
        return false;
      } else if (outcomesSequence[li].endsWith(BilouCodec.OTHER) ||
          outcomesSequence[li].endsWith(BilouCodec.UNIT)) {
        return false;
      } else if (outcomesSequence[li].endsWith(BilouCodec.LAST) &&
          (outcome.endsWith(BilouCodec.CONTINUE) || outcome.endsWith(BilouCodec.LAST))) {
        return false;
      } else if (outcomesSequence[li].endsWith(BilouCodec.CONTINUE) ||
          outcomesSequence[li].endsWith(BilouCodec.START)) {
        // if it is continue, we have to check if previous match was of the same type
        String previousNameType = NameFinderME.extractNameType(outcomesSequence[li]);
        String nameType = NameFinderME.extractNameType(outcome);
        if (previousNameType != null || nameType != null) {
          if (nameType != null) {
            if (nameType.equals(previousNameType)) {
              return true;
            }
          }
          return false; // outcomes types are not equal
        }
      }
    }

    if (outcomesSequence.length > 0) {
      if (outcome.endsWith(BilouCodec.START)
          || outcome.endsWith(BilouCodec.OTHER)
          || outcome.endsWith(BilouCodec.UNIT)) {
        if (outcomesSequence[outcomesSequence.length - 1].endsWith(BilouCodec.START)
            || outcomesSequence[outcomesSequence.length - 1].endsWith(BilouCodec.CONTINUE)) {
          return false;
        }
      }
    }

    return true;
  }
}
