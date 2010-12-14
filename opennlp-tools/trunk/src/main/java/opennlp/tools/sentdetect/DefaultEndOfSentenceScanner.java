/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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


package opennlp.tools.sentdetect;

import java.util.ArrayList;
import java.util.List;

import opennlp.maxent.IntegerPool;

/**
 * Default implementation of the {@link EndOfSentenceScanner}.
 * It uses an character array with possible end of sentence chars
 * to identify potential sentence endings.
 */
public class DefaultEndOfSentenceScanner implements EndOfSentenceScanner {

  protected static final IntegerPool INT_POOL = new IntegerPool(500);

  private char eosCharacters[];

  /**
   * Initializes the current instance.
   *
   * @param eosCharacters
   */
  public DefaultEndOfSentenceScanner(char eosCharacters[]) {
    this.eosCharacters = eosCharacters;
  }

  public List<Integer> getPositions(String s) {
    return getPositions(s.toCharArray());
  }

  public List<Integer> getPositions(StringBuffer buf) {
    return getPositions(buf.toString().toCharArray());
  }

  public List<Integer> getPositions(char[] cbuf) {
    List<Integer> l = new ArrayList<Integer>();
    char[] eosCharacters = getEndOfSentenceCharacters();
    for (int i = 0; i < cbuf.length; i++) {
      for (int ci=0;ci<eosCharacters.length;ci++) {
        if (cbuf[i] == eosCharacters[ci]) {
            l.add(INT_POOL.get(i));
            break;
        }
      }
    }
    return l;
  }

  public char[] getEndOfSentenceCharacters() {
    return eosCharacters;
  }
}
