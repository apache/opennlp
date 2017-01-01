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


package opennlp.tools.sentdetect;

import opennlp.tools.ml.maxent.IntegerPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract class for common methods related to identifying potential ends of sentences.
 * @deprecated use DefaultEndOfSentenceScanner instead
 */
@Deprecated
public abstract class AbstractEndOfSentenceScanner implements EndOfSentenceScanner {

  protected static final IntegerPool INT_POOL = new IntegerPool(500);

  public List<Integer> getPositions(String s) {
    return getPositions(s.toCharArray());
  }

  public List<Integer> getPositions(StringBuffer buf) {
    return getPositions(buf.toString().toCharArray());
  }

  public List<Integer> getPositions(char[] cbuf) {
    List<Integer> l = new ArrayList<>();
    char[] eosCharacters = getEndOfSentenceCharacters();
    for (int i = 0; i < cbuf.length; i++) {
      for (char eosCharacter : eosCharacters) {
        if (cbuf[i] == eosCharacter) {
          l.add(INT_POOL.get(i));
          break;
        }
      }
    }
    return l;
  }
}
