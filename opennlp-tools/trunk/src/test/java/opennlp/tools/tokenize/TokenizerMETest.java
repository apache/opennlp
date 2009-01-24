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


package opennlp.tools.tokenize;

import java.io.IOException;

import junit.framework.TestCase;

/**
 * Tests for the {@link TokenizerME} class.
 * 
 * This test trains the tokenizer with a few sample tokens
 * and then predicts a token. This test checks if the
 * tokenizer code can be executed.
 * 
 * @see TokenizerME
 */
public class TokenizerMETest extends TestCase {

  public void testTokenizer() throws IOException {
    
    TokenizerModel model = TokenizerTestUtil.createMaxentTokenModel();
    
    TokenizerME tokenizer = new TokenizerME(model);
    
    String tokens[] = tokenizer.tokenize("test,");
    
    assertEquals(2, tokens.length);
    assertEquals("test", tokens[0]);
    assertEquals(",", tokens[1]);
  }
}