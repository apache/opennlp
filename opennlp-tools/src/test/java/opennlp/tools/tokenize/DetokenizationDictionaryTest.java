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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class DetokenizationDictionaryTest{

  private String tokens[];
  private DetokenizationDictionary.Operation operations[];
  private DetokenizationDictionary dict;

  @Before
  public void setUp() throws Exception {

    tokens = new String[]{"\"", "(", ")", "-"};

    operations = new DetokenizationDictionary.Operation[]{ DetokenizationDictionary.Operation.RIGHT_LEFT_MATCHING,
        DetokenizationDictionary.Operation.MOVE_RIGHT, DetokenizationDictionary.Operation.MOVE_LEFT, DetokenizationDictionary.Operation.MOVE_BOTH };

    dict = new DetokenizationDictionary(tokens, operations);
  }

  private static void testEntries(DetokenizationDictionary dict) {
    Assert.assertEquals(DetokenizationDictionary.Operation.RIGHT_LEFT_MATCHING, dict.getOperation("\""));
    Assert.assertEquals(DetokenizationDictionary.Operation.MOVE_RIGHT, dict.getOperation("("));
    Assert.assertEquals(DetokenizationDictionary.Operation.MOVE_LEFT, dict.getOperation(")"));
    Assert.assertEquals(DetokenizationDictionary.Operation.MOVE_BOTH, dict.getOperation("-"));
  }

  @Test
  public void testSimpleDict() {
    testEntries(dict);
  }

  @Test
  public void testSerialization() throws IOException {

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    dict.serialize(out);

    DetokenizationDictionary parsedDict = new DetokenizationDictionary(
        new ByteArrayInputStream(out.toByteArray()));

    // should contain the same entries like the original
    testEntries(parsedDict);
  }
}
