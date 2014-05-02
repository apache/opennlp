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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import opennlp.tools.formats.EvalitaNameSampleStream.LANGUAGE;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

import org.junit.Test;

/**
 *
 * Note:
 * Sample training data must be UTF-8 encoded and uncompressed!
 */
public class EvalitaNameSampleStreamTest {

  private static ObjectStream<NameSample> openData(LANGUAGE lang, String name) throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(EvalitaNameSampleStreamTest.class,
        "/opennlp/tools/formats/" + name);

    return new EvalitaNameSampleStream(lang, in, EvalitaNameSampleStream.GENERATE_PERSON_ENTITIES);
  }

  @Test
  public void testParsingItalianSample() throws IOException {

    ObjectStream<NameSample> sampleStream = openData(LANGUAGE.IT, "evalita-ner-it.sample");

    NameSample personName = sampleStream.read();

    assertNotNull(personName);

    assertEquals(11, personName.getSentence().length);
    assertEquals(1, personName.getNames().length);
    assertEquals(true, personName.isClearAdaptiveDataSet());

    Span nameSpan = personName.getNames()[0];
    assertEquals(8, nameSpan.getStart());
    assertEquals(10, nameSpan.getEnd());
    assertEquals(true, personName.isClearAdaptiveDataSet());

    assertEquals(0, sampleStream.read().getNames().length);

    assertNull(sampleStream.read());
  }

  @Test
  public void testReset() throws IOException {
    ObjectStream<NameSample> sampleStream = openData(LANGUAGE.IT, "evalita-ner-it.sample");

    NameSample sample = sampleStream.read();

    sampleStream.reset();

    assertEquals(sample, sampleStream.read());
  }
}
