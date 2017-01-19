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

    DetokenizationOperation operations[] = new DetokenizationOperation[tokens.length];

    Set<String> matchingTokens = new HashSet<>();

    for (int i = 0; i < tokens.length; i++) {
      DetokenizationDictionary.Operation dictOperation = dict.getOperation(tokens[i]);

      if (dictOperation == null) {
        operations[i] = Detokenizer.DetokenizationOperation.NO_OPERATION;
      }
      else if (DetokenizationDictionary.Operation.MOVE_LEFT.equals(dictOperation)) {
        operations[i] = Detokenizer.DetokenizationOperation.MERGE_TO_LEFT;
      }
      else if (DetokenizationDictionary.Operation.MOVE_RIGHT.equals(dictOperation)) {
        operations[i] = Detokenizer.DetokenizationOperation.MERGE_TO_RIGHT;
      }
      else if (DetokenizationDictionary.Operation.MOVE_BOTH.equals(dictOperation)) {
        operations[i] = Detokenizer.DetokenizationOperation.MERGE_BOTH;
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
        throw new IllegalStateException("Unknown operation: " + dictOperation);
      }
    }

    return operations;
  }

  public String detokenize(String tokens[], String splitMarker) {

    DetokenizationOperation operations[] = detokenize(tokens);

    if (tokens.length != operations.length)
      throw new IllegalArgumentException("tokens and operations array must have same length: tokens=" +
          tokens.length + ", operations=" + operations.length + "!");


    StringBuilder untokenizedString = new StringBuilder();

    for (int i = 0; i < tokens.length; i++) {

      // attach token to string buffer
      untokenizedString.append(tokens[i]);

      boolean isAppendSpace;
      boolean isAppendSplitMarker;

      // if this token is the last token do not attach a space
      if (i + 1 == operations.length) {
        isAppendSpace = false;
        isAppendSplitMarker = false;
      }
      // if next token move left, no space after this token,
      // its safe to access next token
      else if (operations[i + 1].equals(DetokenizationOperation.MERGE_TO_LEFT)
          || operations[i + 1].equals(DetokenizationOperation.MERGE_BOTH)) {
        isAppendSpace = false;
        isAppendSplitMarker = true;
      }
      // if this token is move right, no space
      else if (operations[i].equals(DetokenizationOperation.MERGE_TO_RIGHT)
          || operations[i].equals(DetokenizationOperation.MERGE_BOTH)) {
        isAppendSpace = false;
        isAppendSplitMarker = true;
      }
      else {
        isAppendSpace = true;
        isAppendSplitMarker = false;
      }

      if (isAppendSpace) {
        untokenizedString.append(' ');
      }

      if (isAppendSplitMarker && splitMarker != null) {
        untokenizedString.append(splitMarker);
      }
    }

    return untokenizedString.toString();
  }
}
