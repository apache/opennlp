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

package opennlp.uima.normalizer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.StringList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringDictionaryTest {

  // SUT
  private StringDictionary dictionary;

  @Test
  void testInitEmptyDictionary() {
    dictionary = new StringDictionary();
    Iterator<StringList> it = dictionary.iterator();
    assertFalse(it.hasNext());
  }

  @Test
  void testPutAndGetEntry() {
    // prepare
    dictionary = new StringDictionary();
    StringList sl = new StringList("foo", "bar");
    // test
    dictionary.put(sl, "foo bar");
    Iterator<StringList> it = dictionary.iterator();
    assertTrue(it.hasNext());
    assertEquals("foo bar", dictionary.get(sl));
  }

  @Test
  void testSerialization() throws IOException {
    // prepare
    dictionary = new StringDictionary();
    StringList sl = new StringList("foo", "bar");
    dictionary.put(sl, "foo bar");
    byte[] serialized;
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      dictionary.serialize(baos);
      baos.flush();
      serialized = baos.toByteArray();
    }
    try (ByteArrayInputStream bais = new ByteArrayInputStream(serialized)) {
      StringDictionary read = new StringDictionary(bais);
      // test
      Iterator<StringList> it = read.iterator();
      assertTrue(it.hasNext());
      assertEquals("foo bar", read.get(sl));
    }

  }
}
