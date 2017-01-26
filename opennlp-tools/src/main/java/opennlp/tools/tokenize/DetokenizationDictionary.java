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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import opennlp.tools.dictionary.serializer.Attributes;
import opennlp.tools.dictionary.serializer.DictionaryEntryPersistor;
import opennlp.tools.dictionary.serializer.Entry;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringList;

public class DetokenizationDictionary {

  public enum Operation {

    /**
     * Attaches the token to the token on the right side.
     */
    MOVE_RIGHT,

    /**
     * Attaches the token to the token on the left side.
     */
    MOVE_LEFT,

    /**
     * Attaches the token to the token on the left and right sides.
     */
    MOVE_BOTH,

    /**
     * Attaches the token token to the right token on first occurrence, and
     * to the token on the left side on the second occurrence.
     */
    RIGHT_LEFT_MATCHING;

    public static Operation parse(String operation) {

      if (MOVE_RIGHT.toString().equals(operation)) {
        return MOVE_RIGHT;
      }
      else if (MOVE_LEFT.toString().equals(operation)) {
        return MOVE_LEFT;
      }
      else if (MOVE_BOTH.toString().equals(operation)) {
        return MOVE_BOTH;
      }
      else if (RIGHT_LEFT_MATCHING.toString().equals(operation)) {
        return RIGHT_LEFT_MATCHING;
      }
      else {
        return null;
      }
    }
  }

  private final Map<String, DetokenizationDictionary.Operation> operationTable = new HashMap<>();

  /**
   * Initializes the current instance.
   *
   * @param tokens an array of tokens that should be detokenized according to an operation
   * @param operations an array of operations which specifies which operation
   *        should be used for the provided tokens
   */
  public DetokenizationDictionary(String tokens[],
      DetokenizationDictionary.Operation operations[]) {
    if (tokens.length != operations.length)
      throw new IllegalArgumentException("tokens and ops must have the same length: tokens=" +
          tokens.length + ", operations=" + operations.length + "!");

    for (int i = 0; i < tokens.length; i++) {
      String token = tokens[i];
      DetokenizationDictionary.Operation operation = operations[i];

      if (token == null)
        throw new IllegalArgumentException("token at index " + i + " must not be null!");

      if (operation == null)
        throw new IllegalArgumentException("operation at index " + i + " must not be null!");

      operationTable.put(token, operation);
    }
  }

  public DetokenizationDictionary(InputStream in) throws IOException {
    init(in);
  }

  public DetokenizationDictionary(File file) throws IOException {
    try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
      init(in);
    }
  }

  private void init(InputStream in) throws IOException {
    DictionaryEntryPersistor.create(in, entry -> {

      String operationString = entry.getAttributes().getValue("operation");

      StringList word = entry.getTokens();

      if (word.size() != 1)
        throw new InvalidFormatException("Each entry must have exactly one token! " + word);

      // parse operation
      Operation operation = Operation.parse(operationString);

      if (operation == null)
        throw new InvalidFormatException("Unknown operation type: " + operationString);

      operationTable.put(word.getToken(0), operation);
    });
  }

  DetokenizationDictionary.Operation getOperation(String token) {
    return operationTable.get(token);
  }

  // serialize method
  public void serialize(OutputStream out) throws IOException {
    Iterator<Entry> entries = new Iterator<Entry>() {

      Iterator<String> iterator = operationTable.keySet().iterator();

      public boolean hasNext() {
        return iterator.hasNext();
      }

      public Entry next() {

        String token = iterator.next();

        Attributes attributes = new Attributes();
        attributes.setValue("operation", getOperation(token).toString());

        return new Entry(new StringList(token), attributes);
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };

    DictionaryEntryPersistor.serialize(out, entries, false);
  }
}
