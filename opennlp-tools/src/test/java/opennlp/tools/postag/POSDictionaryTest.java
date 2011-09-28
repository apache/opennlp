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

package opennlp.tools.postag;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.util.InvalidFormatException;

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

    InputStream in = new ByteArrayInputStream(out.toByteArray());

    POSDictionary serializedDictionary = null;
    try {
      serializedDictionary = POSDictionary.create(in);
    }
    finally {
        in.close();
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
}