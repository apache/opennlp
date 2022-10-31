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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link NGramCharModel}
 */
public class NGramCharModelTest {

  @Test
  public void testZeroGetCount() throws Exception {
    NGramCharModel ngramModel = new NGramCharModel();
    int count = ngramModel.getCount("");
    Assert.assertEquals(0, count);
    Assert.assertEquals(0, ngramModel.size());
  }

  @Test
  public void testZeroGetCount2() throws Exception {
    NGramCharModel ngramModel = new NGramCharModel();
    ngramModel.add("the");
    int count = ngramModel.getCount("fox");
    Assert.assertEquals(0, count);
    Assert.assertEquals(1, ngramModel.size());
  }

  @Test
  public void testAdd() throws Exception {
    NGramCharModel ngramModel = new NGramCharModel();
    ngramModel.add("fox");
    int count = ngramModel.getCount("the");
    Assert.assertEquals(0, count);
    Assert.assertEquals(1, ngramModel.size());
  }

  @Test
  public void testAdd1() throws Exception {
    NGramCharModel ngramModel = new NGramCharModel();
    ngramModel.add("the");
    int count = ngramModel.getCount("the");
    Assert.assertEquals(1, count);
    Assert.assertEquals(1, ngramModel.size());
  }

  @Test
  public void testAdd2() throws Exception {
    NGramCharModel ngramModel = new NGramCharModel();
    ngramModel.add("the", 1, 3);
    int count = ngramModel.getCount("th");
    Assert.assertEquals(1, count);
    Assert.assertEquals(6, ngramModel.size());
  }

  @Test
  public void testRemove() throws Exception {
    NGramCharModel ngramModel = new NGramCharModel();
    String ngram = "the";
    ngramModel.add(ngram);
    ngramModel.remove(ngram);
    Assert.assertEquals(0, ngramModel.size());
  }

  @Test
  public void testContains() throws Exception {
    NGramCharModel ngramModel = new NGramCharModel();
    String token = "the";
    ngramModel.add(token);
    Assert.assertFalse(ngramModel.contains("fox"));
  }

  @Test
  public void testContains2() throws Exception {
    NGramCharModel ngramModel = new NGramCharModel();
    String token = "the";
    ngramModel.add(token, 1, 3);
    Assert.assertTrue(ngramModel.contains("the"));
  }


  @Test
  public void testCutoff1() throws Exception {
    NGramCharModel ngramModel = new NGramCharModel();
    String token = "the";
    ngramModel.add(token, 1, 3);
    ngramModel.cutoff(2, 4);
    Assert.assertEquals(0, ngramModel.size());
  }
}
