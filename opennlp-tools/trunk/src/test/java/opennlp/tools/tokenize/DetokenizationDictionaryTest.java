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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;
import opennlp.tools.tokenize.DetokenizationDictionary.Operation;
import opennlp.tools.util.InvalidFormatException;

public class DetokenizationDictionaryTest extends TestCase {
  
  private String tokens[];
  private Operation operations[];
  private DetokenizationDictionary dict;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    
    tokens = new String[]{"\"", "(", ")"};
    
    operations = new Operation[]{Operation.RIGHT_LEFT_MATCHING,
        Operation.MOVE_RIGHT, Operation.MOVE_LEFT};
    
    dict = new DetokenizationDictionary(tokens, operations);
  }
  
  private static void testEntries(DetokenizationDictionary dict) {
    assertEquals(Operation.RIGHT_LEFT_MATCHING, dict.getOperation("\""));
    assertEquals(Operation.MOVE_RIGHT, dict.getOperation("("));
    assertEquals(Operation.MOVE_LEFT, dict.getOperation(")"));
  }
  
  public void testSimpleDict() {
    testEntries(dict);
  }
  
  public void testSerialization() throws IOException, InvalidFormatException {
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    dict.serialize(out);
    
    DetokenizationDictionary parsedDict = new DetokenizationDictionary(
        new ByteArrayInputStream(out.toByteArray()));
   
    // should contain the same entries like the original
    testEntries(parsedDict);
  }
}
