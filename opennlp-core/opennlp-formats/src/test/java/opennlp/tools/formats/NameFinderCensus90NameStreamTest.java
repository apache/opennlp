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

package opennlp.tools.formats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringList;

public class NameFinderCensus90NameStreamTest extends AbstractSampleStreamTest {

  @Test
  void testParsingEnglishSample() throws IOException {

    try (ObjectStream<StringList> sampleStream = openData()) {
      StringList personName = sampleStream.read();

      // verify the first 5 taken from the Surname data
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Smith", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Johnson", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Williams", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Jones", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Brown", personName.getToken(0));

      // verify the next 5 taken from the female names
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Mary", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Patricia", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Linda", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Barbara", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Elizabeth", personName.getToken(0));

      // verify the last 5 taken from the male names
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("James", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("John", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Robert", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("Michael", personName.getToken(0));
      personName = sampleStream.read();
      Assertions.assertNotNull(personName);
      Assertions.assertEquals("William", personName.getToken(0));

      // verify the end of the file.
      personName = sampleStream.read();
      Assertions.assertNull(personName);
    }
  }

  private ObjectStream<StringList> openData() throws IOException {
    return new NameFinderCensus90NameStream(getFactory("census90.sample"), StandardCharsets.UTF_8);
  }

}
