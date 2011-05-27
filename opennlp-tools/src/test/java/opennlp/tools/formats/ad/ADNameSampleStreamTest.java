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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.formats.ad.ADNameSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

import org.junit.Before;
import org.junit.Test;

public class ADNameSampleStreamTest {

  List<NameSample> samples = new ArrayList<NameSample>();

  @Test
  public void testSimpleCount() throws IOException {
    assertEquals(4, samples.size());
  }
  
  @Test
  public void testCheckMergedContractions() throws IOException {
    
    assertEquals("no", samples.get(0).getSentence()[1]);
    assertEquals("no", samples.get(0).getSentence()[11]);
    assertEquals("Com", samples.get(1).getSentence()[0]);
    assertEquals("relação", samples.get(1).getSentence()[1]);
    assertEquals("à", samples.get(1).getSentence()[2]);
    assertEquals("mais", samples.get(2).getSentence()[4]);
    assertEquals("de", samples.get(2).getSentence()[5]);
    assertEquals("da", samples.get(2).getSentence()[8]);
    assertEquals("num", samples.get(3).getSentence()[26]);
    
  }
  
  @Test
  public void testSize() throws IOException {
    assertEquals(25, samples.get(0).getSentence().length);
    assertEquals(12, samples.get(1).getSentence().length);
    assertEquals(59, samples.get(2).getSentence().length);
    assertEquals(33, samples.get(3).getSentence().length);
  }
  
  @Test
  public void testNames() throws IOException {

    assertEquals(new Span(4, 7, "time"), samples.get(0).getNames()[0]);
    assertEquals(new Span(8, 10, "place"), samples.get(0).getNames()[1]);
    assertEquals(new Span(12, 14, "place"), samples.get(0).getNames()[2]);
    assertEquals(new Span(15, 17, "person"), samples.get(0).getNames()[3]);
    assertEquals(new Span(18, 19, "numeric"), samples.get(0).getNames()[4]);
    assertEquals(new Span(20, 22, "place"), samples.get(0).getNames()[5]);
    assertEquals(new Span(23, 24, "place"), samples.get(0).getNames()[6]);
    
    assertEquals(new Span(22, 24, "person"), samples.get(2).getNames()[0]);//    22..24
    assertEquals(new Span(25, 27, "person"), samples.get(2).getNames()[1]);//    25..27
    assertEquals(new Span(28, 30, "person"), samples.get(2).getNames()[2]);//    28..30
    assertEquals(new Span(31, 34, "person"), samples.get(2).getNames()[3]);//    31..34
    assertEquals(new Span(35, 37, "person"), samples.get(2).getNames()[4]);//    35..37
    assertEquals(new Span(38, 40, "person"), samples.get(2).getNames()[5]);//    38..40
    assertEquals(new Span(41, 43, "person"), samples.get(2).getNames()[6]);//    41..43
    assertEquals(new Span(44, 46, "person"), samples.get(2).getNames()[7]);//    44..46
    assertEquals(new Span(47, 49, "person"), samples.get(2).getNames()[8]);//    47..49
    assertEquals(new Span(50, 52, "person"), samples.get(2).getNames()[9]);//    50..52
    assertEquals(new Span(53, 55, "person"), samples.get(2).getNames()[10]);//    53..55
    
    assertEquals(new Span(0, 1, "place"), samples.get(3).getNames()[0]);//    0..1
    assertEquals(new Span(6, 7, "event"), samples.get(3).getNames()[1]);//    6..7
    assertEquals(new Span(15, 16, "organization"), samples.get(3).getNames()[2]);//    15..16
    assertEquals(new Span(18, 19, "event"), samples.get(3).getNames()[3]);//    18..19
    assertEquals(new Span(27, 28, "event"), samples.get(3).getNames()[4]);//    27..28
    assertEquals(new Span(29, 30, "event"), samples.get(3).getNames()[5]);//    29..30
  }

  @Before
  public void setup() throws IOException {
    InputStream in = ADParagraphStreamTest.class
        .getResourceAsStream("/opennlp/tools/formats/ad.sample");

    ADNameSampleStream stream = new ADNameSampleStream(
        new PlainTextByLineStream(in, "UTF-8"));

    NameSample sample = stream.read();

    while (sample != null) {
      samples.add(sample);
      sample = stream.read();
    }
  }

}
