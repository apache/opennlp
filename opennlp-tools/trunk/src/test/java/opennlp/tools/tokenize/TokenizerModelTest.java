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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;
import opennlp.tools.util.InvalidFormatException;

/**
 * Tests for the {@link TokenizerModel} class. 
 */
public class TokenizerModelTest extends TestCase {
  
  public void testSentenceModel() throws IOException, InvalidFormatException {
    
    TokenizerModel model = TokenizerTestUtil.createMaxentTokenModel();
    
    ByteArrayOutputStream arrayOut = new ByteArrayOutputStream();
    model.serialize(arrayOut);
    arrayOut.close();
    
    new TokenizerModel(new ByteArrayInputStream(arrayOut.toByteArray()));
    
    // TODO: check that both maxent models are equal
  }
}