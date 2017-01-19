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

import java.io.IOException;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.tokenize.DetokenizationDictionary.Operation;
import opennlp.tools.tokenize.Detokenizer.DetokenizationOperation;

public class DictionaryDetokenizerTest {

  @Test
  public void testDetokenizer() {

    String tokens[] = new String[]{".", "!", "(", ")", "\"", "-"};

    Operation operations[] = new Operation[]{
        Operation.MOVE_LEFT,
        Operation.MOVE_LEFT,
        Operation.MOVE_RIGHT,
        Operation.MOVE_LEFT,
        Operation.RIGHT_LEFT_MATCHING,
        Operation.MOVE_BOTH};

    DetokenizationDictionary dict = new DetokenizationDictionary(tokens, operations);
    Detokenizer detokenizer = new DictionaryDetokenizer(dict);

    DetokenizationOperation detokenizeOperations[] =
      detokenizer.detokenize(new String[]{"Simple",  "test", ".", "co", "-", "worker"});

    Assert.assertEquals(DetokenizationOperation.NO_OPERATION, detokenizeOperations[0]);
    Assert.assertEquals(DetokenizationOperation.NO_OPERATION, detokenizeOperations[1]);
    Assert.assertEquals(DetokenizationOperation.MERGE_TO_LEFT, detokenizeOperations[2]);
    Assert.assertEquals(DetokenizationOperation.NO_OPERATION, detokenizeOperations[3]);
    Assert.assertEquals(DetokenizationOperation.MERGE_BOTH, detokenizeOperations[4]);
    Assert.assertEquals(DetokenizationOperation.NO_OPERATION, detokenizeOperations[5]);
  }

  static Detokenizer createLatinDetokenizer() throws IOException {
    InputStream dictIn = DictionaryDetokenizerTest.class.getResourceAsStream(
        "/opennlp/tools/tokenize/latin-detokenizer.xml");

    DetokenizationDictionary dict = new DetokenizationDictionary(dictIn);

    dictIn.close();

    return new DictionaryDetokenizer(dict);
  }

  @Test
  public void testDetokenizeToString() throws IOException {

    Detokenizer detokenizer = createLatinDetokenizer();

    String tokens[] = new String[]{"A", "test", ",", "(", "string", ")", "."};

    String sentence = detokenizer.detokenize(tokens, null);

    Assert.assertEquals("A test, (string).", sentence);
  }

  @Test
  public void testDetokenizeToString2() throws IOException {

    Detokenizer detokenizer = createLatinDetokenizer();

    String tokens[] = new String[]{"A", "co", "-", "worker", "helped", "."};

    String sentence = detokenizer.detokenize(tokens, null);

    Assert.assertEquals("A co-worker helped.", sentence);
  }
}
