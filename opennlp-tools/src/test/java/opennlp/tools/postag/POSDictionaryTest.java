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

package opennlp.tools.postag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link POSDictionary} class.
 */
public class POSDictionaryTest {

  private static POSDictionary loadDictionary(String name) throws IOException {
    return POSDictionary.create(POSDictionaryTest.class.getResourceAsStream(name));
  }

  private static POSDictionary serializeDeserializeDict(POSDictionary dict) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    try {
      dict.serialize(out);
    }
    finally {
      out.close();
    }

    POSDictionary serializedDictionary;
    try (InputStream in = new ByteArrayInputStream(out.toByteArray())) {
      serializedDictionary = POSDictionary.create(in);
    }

    return serializedDictionary;
  }

  @Test
  public void testSerialization() throws IOException {
    POSDictionary dictionary = new POSDictionary();

    dictionary.put("a", "1", "2", "3");
    dictionary.put("b", "4", "5", "6");
    dictionary.put("c", "7", "8", "9");
    dictionary.put("Always", "RB","NNP");

    Assert.assertTrue(dictionary.equals(serializeDeserializeDict(dictionary)));
  }

  @Test
  public void testLoadingDictionaryWithoutCaseAttribute() throws IOException {
    POSDictionary dict = loadDictionary("TagDictionaryWithoutCaseAttribute.xml");

    Assert.assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    Assert.assertNull(dict.getTags("Mckinsey"));
  }

  @Test
  public void testCaseSensitiveDictionary() throws IOException {
    POSDictionary dict = loadDictionary("TagDictionaryCaseSensitive.xml");

    Assert.assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    Assert.assertNull(dict.getTags("Mckinsey"));

    dict = serializeDeserializeDict(dict);

    Assert.assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    Assert.assertNull(dict.getTags("Mckinsey"));
  }

  @Test
  public void testCaseInsensitiveDictionary() throws IOException {
    POSDictionary dict = loadDictionary("TagDictionaryCaseInsensitive.xml");

    Assert.assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    Assert.assertArrayEquals(new String[]{"NNP"}, dict.getTags("Mckinsey"));
    Assert.assertArrayEquals(new String[]{"NNP"}, dict.getTags("MCKINSEY"));
    Assert.assertArrayEquals(new String[]{"NNP"}, dict.getTags("mckinsey"));

    dict = serializeDeserializeDict(dict);

    Assert.assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    Assert.assertArrayEquals(new String[]{"NNP"}, dict.getTags("Mckinsey"));
  }

  @Test
  public void testToString() throws IOException {
    POSDictionary dict = loadDictionary("TagDictionaryCaseInsensitive.xml");
    Assert.assertEquals("POSDictionary{size=1, caseSensitive=false}", dict.toString());
    dict = loadDictionary("TagDictionaryCaseSensitive.xml");
    Assert.assertEquals("POSDictionary{size=1, caseSensitive=true}", dict.toString());
  }

  @Test
  public void testEqualsAndHashCode() throws IOException {
    POSDictionary dictA = loadDictionary("TagDictionaryCaseInsensitive.xml");
    POSDictionary dictB = loadDictionary("TagDictionaryCaseInsensitive.xml");

    Assert.assertEquals(dictA, dictB);
    Assert.assertEquals(dictA.hashCode(), dictB.hashCode());
  }
}
