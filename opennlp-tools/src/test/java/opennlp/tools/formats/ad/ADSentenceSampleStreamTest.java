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

package opennlp.tools.formats.ad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

import org.junit.Before;
import org.junit.Test;

public class ADSentenceSampleStreamTest {

  List<SentenceSample> samples = new ArrayList<SentenceSample>();

  @Test
  public void testSimpleCount() throws IOException {
    assertEquals(5, samples.size());
  }

  @Test
  public void testSentences() throws IOException {
    
    assertNotNull(samples.get(0).getDocument());
    assertEquals(3, samples.get(0).getSentences().length);
    assertEquals(new Span(0, 119), samples.get(0).getSentences()[0]);
    assertEquals(new Span(120, 180), samples.get(0).getSentences()[1]);
  }

  @Before
  public void setup() throws IOException {
    InputStream in = ADSentenceSampleStreamTest.class
        .getResourceAsStream("/opennlp/tools/formats/ad.sample");

    ADSentenceSampleStream stream = new ADSentenceSampleStream(
        new PlainTextByLineStream(in, "UTF-8"), true);

    SentenceSample sample = stream.read();

    while (sample != null) {
      System.out.println(sample.getDocument());
      System.out.println("<fim>");
      samples.add(sample);
      sample = stream.read();
    }
  }

}
