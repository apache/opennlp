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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class ADNameSampleStreamTest {

  private List<NameSample> samples = new ArrayList<>();

  @Test
  public void testSimpleCount() throws IOException {
    Assert.assertEquals(ADParagraphStreamTest.NUM_SENTENCES, samples.size());
  }

  @Test
  public void testCheckMergedContractions() throws IOException {

    Assert.assertEquals("no", samples.get(0).getSentence()[1]);
    Assert.assertEquals("no", samples.get(0).getSentence()[11]);
    Assert.assertEquals("Com", samples.get(1).getSentence()[0]);
    Assert.assertEquals("relação", samples.get(1).getSentence()[1]);
    Assert.assertEquals("à", samples.get(1).getSentence()[2]);
    Assert.assertEquals("mais", samples.get(2).getSentence()[4]);
    Assert.assertEquals("de", samples.get(2).getSentence()[5]);
    Assert.assertEquals("da", samples.get(2).getSentence()[8]);
    Assert.assertEquals("num", samples.get(3).getSentence()[26]);

  }

  @Test
  public void testSize() throws IOException {
    Assert.assertEquals(25, samples.get(0).getSentence().length);
    Assert.assertEquals(12, samples.get(1).getSentence().length);
    Assert.assertEquals(59, samples.get(2).getSentence().length);
    Assert.assertEquals(33, samples.get(3).getSentence().length);
  }

  @Test
  public void testNames() throws IOException {

    Assert.assertEquals(new Span(4, 7, "time"), samples.get(0).getNames()[0]);
    Assert.assertEquals(new Span(8, 10, "place"), samples.get(0).getNames()[1]);
    Assert.assertEquals(new Span(12, 14, "place"), samples.get(0).getNames()[2]);
    Assert.assertEquals(new Span(15, 17, "person"), samples.get(0).getNames()[3]);
    Assert.assertEquals(new Span(18, 19, "numeric"), samples.get(0).getNames()[4]);
    Assert.assertEquals(new Span(20, 22, "place"), samples.get(0).getNames()[5]);
    Assert.assertEquals(new Span(23, 24, "place"), samples.get(0).getNames()[6]);

    Assert.assertEquals(new Span(22, 24, "person"), samples.get(2).getNames()[0]);//    22..24
    Assert.assertEquals(new Span(25, 27, "person"), samples.get(2).getNames()[1]);//    25..27
    Assert.assertEquals(new Span(28, 30, "person"), samples.get(2).getNames()[2]);//    28..30
    Assert.assertEquals(new Span(31, 34, "person"), samples.get(2).getNames()[3]);//    31..34
    Assert.assertEquals(new Span(35, 37, "person"), samples.get(2).getNames()[4]);//    35..37
    Assert.assertEquals(new Span(38, 40, "person"), samples.get(2).getNames()[5]);//    38..40
    Assert.assertEquals(new Span(41, 43, "person"), samples.get(2).getNames()[6]);//    41..43
    Assert.assertEquals(new Span(44, 46, "person"), samples.get(2).getNames()[7]);//    44..46
    Assert.assertEquals(new Span(47, 49, "person"), samples.get(2).getNames()[8]);//    47..49
    Assert.assertEquals(new Span(50, 52, "person"), samples.get(2).getNames()[9]);//    50..52
    Assert.assertEquals(new Span(53, 55, "person"), samples.get(2).getNames()[10]);//    53..55

    Assert.assertEquals(new Span(0, 1, "place"), samples.get(3).getNames()[0]);//    0..1
    Assert.assertEquals(new Span(6, 7, "event"), samples.get(3).getNames()[1]);//    6..7
    Assert.assertEquals(new Span(15, 16, "organization"), samples.get(3).getNames()[2]);//    15..16
    Assert.assertEquals(new Span(18, 19, "event"), samples.get(3).getNames()[3]);//    18..19
    Assert.assertEquals(new Span(27, 28, "event"), samples.get(3).getNames()[4]);//    27..28
    Assert.assertEquals(new Span(29, 30, "event"), samples.get(3).getNames()[5]);//    29..30

    Assert.assertEquals(new Span(1, 6, "time"), samples.get(4).getNames()[0]);//    0..1
    Assert.assertEquals(new Span(0, 3, "person"), samples.get(5).getNames()[0]);//    0..1
  }

  @Test
  public void testSmallSentence() throws IOException {
    Assert.assertEquals(2, samples.get(6).getSentence().length);
  }

  @Test
  public void testMissingRightContraction() throws IOException {
    Assert.assertEquals(new Span(0, 1, "person"), samples.get(7).getNames()[0]);
    Assert.assertEquals(new Span(3, 4, "person"), samples.get(7).getNames()[1]);
    Assert.assertEquals(new Span(5, 6, "person"), samples.get(7).getNames()[2]);
  }

  @Before
  public void setup() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(ADParagraphStreamTest.class,
        "/opennlp/tools/formats/ad.sample");

    ADNameSampleStream stream = new ADNameSampleStream(
        new PlainTextByLineStream(in, "UTF-8"), true);

    NameSample sample = stream.read();

    while (sample != null) {
      samples.add(sample);
      sample = stream.read();
    }
  }

}
