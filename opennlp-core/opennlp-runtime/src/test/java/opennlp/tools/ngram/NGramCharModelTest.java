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

package opennlp.tools.ngram;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NGramCharModel}
 */
public class NGramCharModelTest {

  @Test
  void testZeroGetCount() {
    NGramCharModel ngramModel = new NGramCharModel();
    int count = ngramModel.getCount("");
    Assertions.assertEquals(0, count);
    Assertions.assertEquals(0, ngramModel.size());
  }

  @Test
  void testZeroGetCount2() {
    NGramCharModel ngramModel = new NGramCharModel();
    ngramModel.add("the");
    int count = ngramModel.getCount("fox");
    Assertions.assertEquals(0, count);
    Assertions.assertEquals(1, ngramModel.size());
  }

  @Test
  void testAdd() {
    NGramCharModel ngramModel = new NGramCharModel();
    ngramModel.add("fox");
    int count = ngramModel.getCount("the");
    Assertions.assertEquals(0, count);
    Assertions.assertEquals(1, ngramModel.size());
  }

  @Test
  void testAdd1() {
    NGramCharModel ngramModel = new NGramCharModel();
    ngramModel.add("the");
    int count = ngramModel.getCount("the");
    Assertions.assertEquals(1, count);
    Assertions.assertEquals(1, ngramModel.size());
  }

  @Test
  void testAdd2() {
    NGramCharModel ngramModel = new NGramCharModel();
    ngramModel.add("the", 1, 3);
    int count = ngramModel.getCount("th");
    Assertions.assertEquals(1, count);
    Assertions.assertEquals(6, ngramModel.size());
  }

  @Test
  void testRemove() {
    NGramCharModel ngramModel = new NGramCharModel();
    String ngram = "the";
    ngramModel.add(ngram);
    ngramModel.remove(ngram);
    Assertions.assertEquals(0, ngramModel.size());
  }

  @Test
  void testContains() {
    NGramCharModel ngramModel = new NGramCharModel();
    String token = "the";
    ngramModel.add(token);
    Assertions.assertFalse(ngramModel.contains("fox"));
  }

  @Test
  void testContains2() {
    NGramCharModel ngramModel = new NGramCharModel();
    String token = "the";
    ngramModel.add(token, 1, 3);
    Assertions.assertTrue(ngramModel.contains("the"));
  }


  @Test
  void testCutoff1() {
    NGramCharModel ngramModel = new NGramCharModel();
    String token = "the";
    ngramModel.add(token, 1, 3);
    ngramModel.cutoff(2, 4);
    Assertions.assertEquals(0, ngramModel.size());
  }
}
