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

package opennlp.tools.tokenize;

import java.util.HashSet;
import java.util.Set;

/**
 * A rule based detokenizer. Simple rules which indicate in which direction a token should be
 * moved are looked up in a {@link DetokenizationDictionary} object.
 * 
 * @see Detokenizer
 * @see DetokenizationDictionary
 */
public class DictionaryDetokenizer implements Detokenizer {

  private final DetokenizationDictionary dict;
  
  public DictionaryDetokenizer(DetokenizationDictionary dict) {
    this.dict = dict;
  }
  
  public DetokenizationOperation[] detokenize(String[] tokens) {
    
    DetokenizationOperation operations[] = 
        new DetokenizationOperation[tokens.length];
    
    Set<String> matchingTokens = new HashSet<String>();
    
    for (int i = 0; i < tokens.length; i++) {
      DetokenizationDictionary.Operation dictOperation = 
        dict.getOperation(tokens[i]);
      
      if (dictOperation == null) {
        operations[i] = Detokenizer.DetokenizationOperation.NO_OPERATION;
      }
      else if (DetokenizationDictionary.Operation.MOVE_LEFT.equals(dictOperation)) {
        operations[i] = Detokenizer.DetokenizationOperation.MERGE_TO_LEFT;
      }
      else if (DetokenizationDictionary.Operation.MOVE_RIGHT.equals(dictOperation)) {
        operations[i] = Detokenizer.DetokenizationOperation.MERGE_TO_RIGHT;
      }
      else if (DetokenizationDictionary.Operation.RIGHT_LEFT_MATCHING.equals(dictOperation)) {
        
        if (matchingTokens.contains(tokens[i])) {
          // The token already occurred once, move it to the left
          // and clear the occurrence flag
          operations[i] = Detokenizer.DetokenizationOperation.MERGE_TO_LEFT;
          matchingTokens.remove(tokens[i]);
        }
        else {
          // First time this token is seen, move it to the right
          // and remember it
          operations[i] = Detokenizer.DetokenizationOperation.MERGE_TO_RIGHT;
          matchingTokens.add(tokens[i]);
        }
      }
      else {
        throw new IllegalStateException("Unkown operation: " + dictOperation);
      }
    }
    
    return operations;
  }
}
