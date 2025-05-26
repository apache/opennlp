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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringList;

/**
 * Tests for {@link opennlp.tools.ngram.NGramModel}
 */
public class NGramModelTest {

  @Test
  void testZeroGetCount() {
    NGramModel ngramModel = new NGramModel();
    int count = ngramModel.getCount(new StringList(""));
    Assertions.assertEquals(0, count);
    Assertions.assertEquals(0, ngramModel.size());
  }

  @Test
  void testZeroGetCount2() {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "bro", "wn"));
    int count = ngramModel.getCount(new StringList("fox"));
    Assertions.assertEquals(0, count);
    Assertions.assertEquals(1, ngramModel.size());
  }

  @Test
  void testAdd() {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "bro", "wn"));
    int count = ngramModel.getCount(new StringList("the"));
    Assertions.assertEquals(0, count);
    Assertions.assertEquals(1, ngramModel.size());
  }

  @Test
  void testAdd1() {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "bro", "wn"));
    int count = ngramModel.getCount(new StringList("the", "bro", "wn"));
    Assertions.assertEquals(1, count);
    Assertions.assertEquals(1, ngramModel.size());
  }

  @Test
  void testAdd2() {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "bro", "wn"), 2, 3);
    int count = ngramModel.getCount(new StringList("the", "bro", "wn"));
    Assertions.assertEquals(1, count);
    Assertions.assertEquals(3, ngramModel.size());
  }

  @Test
  void testAdd3() {
    NGramModel ngramModel = new NGramModel();
    ngramModel.add(new StringList("the", "brown", "fox"), 2, 3);
    int count = ngramModel.getCount(new StringList("the", "brown", "fox"));
    Assertions.assertEquals(1, count);
    count = ngramModel.getCount(new StringList("the", "brown"));
    Assertions.assertEquals(1, count);
    count = ngramModel.getCount(new StringList("brown", "fox"));
    Assertions.assertEquals(1, count);
    Assertions.assertEquals(3, ngramModel.size());
  }

  @Test
  void testRemove() {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "bro", "wn");
    ngramModel.add(tokens);
    ngramModel.remove(tokens);
    Assertions.assertEquals(0, ngramModel.size());
  }

  @Test
  void testContains() {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "bro", "wn");
    ngramModel.add(tokens);
    Assertions.assertFalse(ngramModel.contains(new StringList("the")));
  }

  @Test
  void testContains2() {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "bro", "wn");
    ngramModel.add(tokens, 1, 3);
    Assertions.assertTrue(ngramModel.contains(new StringList("the")));
  }

  @Test
  void testNumberOfGrams() {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "bro", "wn");
    ngramModel.add(tokens, 1, 3);
    Assertions.assertEquals(6, ngramModel.numberOfGrams());
  }

  @Test
  void testCutoff1() {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "brown", "fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    ngramModel.cutoff(2, 4);
    Assertions.assertEquals(0, ngramModel.size());
  }

  @Test
  void testCutoff2() {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "brown", "fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    ngramModel.cutoff(1, 3);
    Assertions.assertEquals(9, ngramModel.size());
  }

  @Test
  void testToDictionary() {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "brown", "fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    tokens = new StringList("the", "brown", "Fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    Dictionary dictionary = ngramModel.toDictionary();
    Assertions.assertNotNull(dictionary);
    Assertions.assertEquals(9, dictionary.size());
    Assertions.assertEquals(1, dictionary.getMinTokenCount());
    Assertions.assertEquals(3, dictionary.getMaxTokenCount());
  }

  @Test
  void testToDictionary1() {
    NGramModel ngramModel = new NGramModel();
    StringList tokens = new StringList("the", "brown", "fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    tokens = new StringList("the", "brown", "Fox", "jumped");
    ngramModel.add(tokens, 1, 3);
    Dictionary dictionary = ngramModel.toDictionary(true);
    Assertions.assertNotNull(dictionary);
    Assertions.assertEquals(14, dictionary.size());
    Assertions.assertEquals(1, dictionary.getMinTokenCount());
    Assertions.assertEquals(3, dictionary.getMaxTokenCount());
  }

  @Test
  void testInvalidFormat() {
    Assertions.assertThrows(InvalidFormatException.class, () -> {
      InputStream stream = new ByteArrayInputStream("inputstring".getBytes(StandardCharsets.UTF_8));
      NGramModel ngramModel = new NGramModel(stream);
      stream.close();
      ngramModel.toDictionary(true);
    });
  }

  @Test
  void testFromFile() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream("/opennlp/tools/ngram/ngram-model.xml")) {
      NGramModel ngramModel = new NGramModel(stream);
      Dictionary dictionary = ngramModel.toDictionary(true);
      Assertions.assertNotNull(dictionary);
      Assertions.assertEquals(14, dictionary.size());
      Assertions.assertEquals(3, dictionary.getMaxTokenCount());
      Assertions.assertEquals(1, dictionary.getMinTokenCount());
    }
  }

  @Test
  void testSerialize() throws Exception {

    try (InputStream stream = getClass().getResourceAsStream("/opennlp/tools/ngram/ngram-model.xml");
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      
      NGramModel ngramModel1 = new NGramModel(stream);
      Dictionary dictionary = ngramModel1.toDictionary(true);
      Assertions.assertNotNull(dictionary);
      Assertions.assertEquals(14, dictionary.size());
      Assertions.assertEquals(3, dictionary.getMaxTokenCount());
      Assertions.assertEquals(1, dictionary.getMinTokenCount());
      ngramModel1.serialize(baos);

      final String serialized = baos.toString(Charset.defaultCharset());
      InputStream inputStream = new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8));

      NGramModel ngramModel2 = new NGramModel(inputStream);

      Assertions.assertEquals(ngramModel2.numberOfGrams(), ngramModel2.numberOfGrams());
      Assertions.assertEquals(ngramModel2.size(), ngramModel2.size());

      dictionary = ngramModel2.toDictionary(true);

      Assertions.assertNotNull(dictionary);
      Assertions.assertEquals(14, dictionary.size());
      Assertions.assertEquals(3, dictionary.getMaxTokenCount());
      Assertions.assertEquals(1, dictionary.getMinTokenCount());
    }
  }

  @Test
  void testFromInvalidFileMissingCount() {
    Assertions.assertThrows(InvalidFormatException.class, () -> {
      try (InputStream stream = getClass().getResourceAsStream(
              "/opennlp/tools/ngram/ngram-model-no-count.xml")) {
        NGramModel ngramModel = new NGramModel(stream);
        ngramModel.toDictionary(true);
      }
    });
  }

  @Test
  void testFromInvalidFileNotANumber() {
    Assertions.assertThrows(InvalidFormatException.class, () -> {
      try (InputStream stream = getClass().getResourceAsStream(
          "/opennlp/tools/ngram/ngram-model-not-a-number.xml")) {
        NGramModel ngramModel = new NGramModel(stream);
        ngramModel.toDictionary(true);
      }
    });
  }

}
