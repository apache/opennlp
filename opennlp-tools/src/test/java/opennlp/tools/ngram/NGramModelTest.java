/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.ngram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.StringList;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for {@link opennlp.tools.ngram.NGramModel}
 */
public class NGramModelTest {

  @Test
  public void testZeroGetCount() throws Exception {
    NGramModel ngramModel = new NGramModel();
    int count = ngramModel.getCount(new StringList(""));
    assertEquals(0, count);
    assertEquals(0, ngramModel.size());
  }

  @Test
  public void testZeroGetCount2() throws Exception {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "bro", "wn"));
    int count = ngramModel.getCount(new StringList("fox"));
    assertEquals(0, count);
    assertEquals(1, ngramModel.size());
  }

  @Test
  public void testAdd() throws Exception {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "bro", "wn"));
    int count = ngramModel.getCount(new StringList("the"));
    assertEquals(0, count);
    assertEquals(1, ngramModel.size());
  }

  @Test
  public void testAdd1() throws Exception {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "bro", "wn"));
    int count = ngramModel.getCount(new StringList("the", "bro", "wn"));
    assertEquals(1, count);
    assertEquals(1, ngramModel.size());
  }

  @Test
  public void testAdd2() throws Exception {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "bro", "wn"), 2, 3);
    int count = ngramModel.getCount(new StringList("the", "bro", "wn"));
    assertEquals(1, count);
    assertEquals(3, ngramModel.size());
  }

  @Test
  public void testAdd3() throws Exception {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "brown", "fox"), 2, 3);
    int count = ngramModel.getCount(new StringList("the", "brown", "fox"));
    assertEquals(1, count);
    count = ngramModel.getCount(new StringList("the", "brown"));
    assertEquals(1, count);
    count = ngramModel.getCount(new StringList("brown", "fox"));
    assertEquals(1, count);
    assertEquals(3, ngramModel.size());
  }

  @Test
  public void testRemove() throws Exception {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "bro", "wn");
    ngramModel.add(tokens);
    ngramModel.remove(tokens);
    assertEquals(0, ngramModel.size());
  }

  @Test
  public void testContains() throws Exception {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "bro", "wn");
    ngramModel.add(tokens);
    assertFalse(ngramModel.contains(new StringList("the")));
  }

  @Test
  public void testContains2() throws Exception {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "bro", "wn");
    ngramModel.add(tokens, 1, 3);
    assertTrue(ngramModel.contains(new StringList("the")));
  }

  @Test
  public void testNumberOfGrams() throws Exception {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "bro", "wn");
    ngramModel.add(tokens, 1, 3);
    assertEquals(6, ngramModel.numberOfGrams());
  }

  @Test
  public void testCutoff1() throws Exception {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "brown", "fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    ngramModel.cutoff(2, 4);
    assertEquals(0, ngramModel.size());
  }

  @Test
  public void testCutoff2() throws Exception {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "brown", "fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    ngramModel.cutoff(1, 3);
    assertEquals(9, ngramModel.size());
  }

  @Test
  public void testToDictionary() throws Exception {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "brown", "fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    tokens = new StringList("the", "brown", "Fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    Dictionary dictionary = ngramModel.toDictionary();
    assertNotNull(dictionary);
    assertEquals(9, dictionary.size());
    assertEquals(1, dictionary.getMinTokenCount());
    assertEquals(3, dictionary.getMaxTokenCount());
  }

  @Test
  public void testToDictionary1() throws Exception {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "brown", "fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    tokens = new StringList("the", "brown", "Fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    Dictionary dictionary = ngramModel.toDictionary(true);
    assertNotNull(dictionary);
    assertEquals(14, dictionary.size());
    assertEquals(1, dictionary.getMinTokenCount());
    assertEquals(3, dictionary.getMaxTokenCount());
  }

  @Ignore
  @Test
  public void testSerialize() throws Exception {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "brown", "fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    tokens = new StringList("the", "brown", "Fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ngramModel.serialize(out);
    assertNotNull(out);
    InputStream nGramModelStream = getClass().getResourceAsStream("/opennlp/tools/ngram/ngram-model.xml");
    String modelString = IOUtils.toString(nGramModelStream);
    // remove AL header
    int start = modelString.indexOf("<!--");
    int end = modelString.indexOf("-->");
    String asfHeaderString = modelString.substring(start, end + 3);
    modelString = modelString.replace(asfHeaderString, "");
    String outputString = out.toString(Charset.forName("UTF-8").name());
    assertEquals(modelString.replaceAll("\n", "").replaceAll("\r", "").replaceAll("\t", "").replaceAll(" ", ""),
            outputString.replaceAll("\n", "").replaceAll("\r", "").replaceAll("\t", "").replaceAll(" ", ""));
  }
}