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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.cmdline.tokenizer.DictionaryDetokenizerTool;
import opennlp.tools.tokenize.DetokenizationDictionary.Operation;
import opennlp.tools.tokenize.Detokenizer.DetokenizationOperation;

import org.junit.Test;

public class DictionaryDetokenizerTest{

  @Test
  public void testDetokenizer() {
    
    String tokens[] = new String[]{".", "!", "(", ")", "\""};
    
    Operation operations[] = new Operation[]{
        Operation.MOVE_LEFT,
        Operation.MOVE_LEFT,
        Operation.MOVE_RIGHT,
        Operation.MOVE_LEFT,
        Operation.RIGHT_LEFT_MATCHING
      };
    
    DetokenizationDictionary dict = new DetokenizationDictionary(tokens, operations);
    Detokenizer detokenizer = new DictionaryDetokenizer(dict);
    
    DetokenizationOperation detokenizeOperations[] = 
      detokenizer.detokenize(new String[]{"Simple",  "test", "."});
    
    assertEquals(DetokenizationOperation.NO_OPERATION, detokenizeOperations[0]);
    assertEquals(DetokenizationOperation.NO_OPERATION, detokenizeOperations[1]);
    assertEquals(DetokenizationOperation.MERGE_TO_LEFT, detokenizeOperations[2]);
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
    DetokenizationOperation operations[] = detokenizer.detokenize(tokens);
      
    String sentence = DictionaryDetokenizerTool.detokenize(tokens, operations);
      
    assertEquals("A test, (string).", sentence);
  }
}
