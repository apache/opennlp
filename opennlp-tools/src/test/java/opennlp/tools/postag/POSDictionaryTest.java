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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.util.InvalidFormatException;

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

    POSDictionary serializedDictionary = null;
    try (InputStream in = new ByteArrayInputStream(out.toByteArray())) {
      serializedDictionary = POSDictionary.create(in);
    }

    return serializedDictionary;
  }

  @Test
  public void testSerialization() throws IOException, InvalidFormatException {
    POSDictionary dictionary = new POSDictionary();

    dictionary.addTags("a", "1", "2", "3");
    dictionary.addTags("b", "4", "5", "6");
    dictionary.addTags("c", "7", "8", "9");
    dictionary.addTags("Always", "RB","NNP");

    assertTrue(dictionary.equals(serializeDeserializeDict(dictionary)));
  }

  @Test
  public void testLoadingDictionaryWithoutCaseAttribute() throws IOException {
    POSDictionary dict = loadDictionary("TagDictionaryWithoutCaseAttribute.xml");

    assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    assertNull(dict.getTags("Mckinsey"));
  }

  @Test
  public void testCaseSensitiveDictionary() throws IOException {
    POSDictionary dict = loadDictionary("TagDictionaryCaseSensitive.xml");

    assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    assertNull(dict.getTags("Mckinsey"));

    dict = serializeDeserializeDict(dict);

    assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    assertNull(dict.getTags("Mckinsey"));
  }

  @Test
  public void testCaseInsensitiveDictionary() throws IOException {
    POSDictionary dict = loadDictionary("TagDictionaryCaseInsensitive.xml");

    assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    assertArrayEquals(new String[]{"NNP"}, dict.getTags("Mckinsey"));
    assertArrayEquals(new String[]{"NNP"}, dict.getTags("MCKINSEY"));
    assertArrayEquals(new String[]{"NNP"}, dict.getTags("mckinsey"));

    dict = serializeDeserializeDict(dict);

    assertArrayEquals(new String[]{"NNP"}, dict.getTags("McKinsey"));
    assertArrayEquals(new String[]{"NNP"}, dict.getTags("Mckinsey"));
  }

  @Test
  public void testToString() throws IOException {
    POSDictionary dict = loadDictionary("TagDictionaryCaseInsensitive.xml");
    assertEquals("POSDictionary{size=1, caseSensitive=false}", dict.toString());
    dict = loadDictionary("TagDictionaryCaseSensitive.xml");
    assertEquals("POSDictionary{size=1, caseSensitive=true}", dict.toString());
  }

  @Test
  public void testEqualsAndHashCode() throws IOException {
    POSDictionary dictA = loadDictionary("TagDictionaryCaseInsensitive.xml");
    POSDictionary dictB = loadDictionary("TagDictionaryCaseInsensitive.xml");

    Assert.assertEquals(dictA, dictB);
    Assert.assertEquals(dictA.hashCode(), dictB.hashCode());
  }
}
