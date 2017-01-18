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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.formats.Conll02NameSampleStream.LANGUAGE;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 *
 * Note:
 * Sample training data must be UTF-8 encoded and uncompressed!
 */
public class Conll02NameSampleStreamTest {

  private static ObjectStream<NameSample> openData(LANGUAGE lang, String name) throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(Conll02NameSampleStreamTest.class,
        "/opennlp/tools/formats/" + name);

    return new Conll02NameSampleStream(lang, in, Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);
  }

  @Test
  public void testParsingSpanishSample() throws IOException {

    ObjectStream<NameSample> sampleStream = openData(LANGUAGE.ES, "conll2002-es.sample");

    NameSample personName = sampleStream.read();

    Assert.assertNotNull(personName);

    Assert.assertEquals(5, personName.getSentence().length);
    Assert.assertEquals(1, personName.getNames().length);
    Assert.assertEquals(true, personName.isClearAdaptiveDataSet());

    Span nameSpan = personName.getNames()[0];
    Assert.assertEquals(0, nameSpan.getStart());
    Assert.assertEquals(4, nameSpan.getEnd());
    Assert.assertEquals(true, personName.isClearAdaptiveDataSet());

    Assert.assertEquals(0, sampleStream.read().getNames().length);

    Assert.assertNull(sampleStream.read());
  }

  @Test
  public void testParsingDutchSample() throws IOException {
    ObjectStream<NameSample> sampleStream = openData(LANGUAGE.NL, "conll2002-nl.sample");

    NameSample personName = sampleStream.read();

    Assert.assertEquals(0, personName.getNames().length);
    Assert.assertTrue(personName.isClearAdaptiveDataSet());

    personName = sampleStream.read();

    Assert.assertFalse(personName.isClearAdaptiveDataSet());

    Assert.assertNull(sampleStream.read());
  }

  @Test
  public void testReset() throws IOException {
    ObjectStream<NameSample> sampleStream = openData(LANGUAGE.NL, "conll2002-nl.sample");

    NameSample sample = sampleStream.read();

    sampleStream.reset();

    Assert.assertEquals(sample, sampleStream.read());
  }
}
