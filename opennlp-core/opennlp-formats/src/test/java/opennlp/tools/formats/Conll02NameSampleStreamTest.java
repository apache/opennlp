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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.Conll02NameSampleStream.LANGUAGE;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * Note:
 * Sample training data must be UTF-8 encoded and uncompressed!
 */
public class Conll02NameSampleStreamTest extends AbstractSampleStreamTest {

  @Test
  void testParsingSpanishSample() throws IOException {

    try (ObjectStream<NameSample> sampleStream = openData(LANGUAGE.SPA, "conll2002-es.sample")) {
      NameSample personName = sampleStream.read();

      Assertions.assertNotNull(personName);
      Assertions.assertEquals(5, personName.getSentence().length);
      Assertions.assertEquals(1, personName.getNames().length);
      Assertions.assertTrue(personName.isClearAdaptiveDataSet());

      Span nameSpan = personName.getNames()[0];
      Assertions.assertEquals(0, nameSpan.getStart());
      Assertions.assertEquals(4, nameSpan.getEnd());
      Assertions.assertTrue(personName.isClearAdaptiveDataSet());

      Assertions.assertEquals(0, sampleStream.read().getNames().length);

      Assertions.assertNull(sampleStream.read());
    }
  }

  @Test
  void testParsingDutchSample() throws IOException {
    try (ObjectStream<NameSample> sampleStream = openData(LANGUAGE.NLD, "conll2002-nl.sample")) {
      NameSample personName = sampleStream.read();

      Assertions.assertEquals(0, personName.getNames().length);
      Assertions.assertTrue(personName.isClearAdaptiveDataSet());

      personName = sampleStream.read();

      Assertions.assertFalse(personName.isClearAdaptiveDataSet());
      Assertions.assertNull(sampleStream.read());
    }
  }

  @Test
  void testReset() throws IOException {
    try (ObjectStream<NameSample> sampleStream = openData(LANGUAGE.NLD, "conll2002-nl.sample")) {
      NameSample sample = sampleStream.read();
      sampleStream.reset();

      Assertions.assertEquals(sample, sampleStream.read());
    }
  }

  private ObjectStream<NameSample> openData(LANGUAGE lang, String name) throws IOException {
    return new Conll02NameSampleStream(
            lang, getFactory(name), Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);
  }

}
