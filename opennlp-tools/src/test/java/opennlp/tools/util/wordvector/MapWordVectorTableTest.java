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

package opennlp.tools.util.wordvector;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MapWordVectorTableTest extends AbstractWordVectorTest {

  private WordVectorTable wvTable;

  @BeforeEach
  public void setup() throws IOException {
    try (InputStream glove = getResourceStream("glove-example-short.txt")) {
      wvTable = Glove.parse(glove);
      Assertions.assertNotNull(wvTable);
    }
  }

  @Test
  public void testSize() {
    // see content of: 'glove-example-short.txt'
    Assertions.assertEquals(4f,  wvTable.size());
  }

  @Test
  public void testDimension() {
    // see content of: 'glove-example-short.txt'
    Assertions.assertEquals(50f,  wvTable.dimension());
  }

  @Test
  public void testTokens() {
    // reference
    Set<String> refTokens = Set.of("the",  "of",  "to",  "and");
    Iterator<String> tokens = wvTable.tokens();
    Assertions.assertNotNull(tokens);
    String token;
    while (tokens.hasNext()) {
      token = tokens.next();
      Assertions.assertTrue(refTokens.contains(token));
    }
  }

  @Test
  public void testGetWordVectors() {
    // reference
    Set<String> refTokens = Set.of("the",  "of",  "to",  "and");

    for (String token: refTokens) {
      WordVector wv = wvTable.get(token);
      Assertions.assertNotNull(wv);
      Assertions.assertEquals(50f,  wv.dimension());
    }
  }

  @Test
  public void testGetWordVectorEquality() {
    // the word vector of "the" token,  see content of: 'glove-example-short.txt'
    float[] wordVectorOfThe = new float[] {
        0.418f, 0.24968f, -0.41242f, 0.1217f, 0.34527f, -0.044457f, -0.49688f, -0.17862f,
        -0.00066023f, -0.6566f, 0.27843f, -0.14767f, -0.55677f, 0.14658f, -0.0095095f,
        0.011658f, 0.10204f, -0.12792f, -0.8443f, -0.12181f, -0.016801f, -0.33279f, -0.1552f,
        -0.23131f, -0.19181f, -1.8823f, -0.76746f, 0.099051f, -0.42125f, -0.19526f, 4.0071f,
        -0.18594f, -0.52287f, -0.31681f, 0.00059213f, 0.0074449f, 0.17778f, -0.15897f, 0.012041f,
        -0.054223f, -0.29871f, -0.15749f, -0.34758f, -0.045637f, -0.44251f, 0.18785f, 0.0027849f,
        -0.18411f, -0.11514f, -0.78581f};
    FloatArrayVector refWordVector = new FloatArrayVector(wordVectorOfThe);
    WordVector wv = wvTable.get("the");
    Assertions.assertNotNull(wv);
    for (int i = 0; i < wordVectorOfThe.length; i++) {
      Assertions.assertEquals(refWordVector.getAsFloat(i), wv.getAsFloat(i));
    }
  }
}
