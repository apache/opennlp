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

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.formats.ad.ADChunkSampleStream;
import opennlp.tools.util.PlainTextByLineStream;

import org.junit.Before;
import org.junit.Test;

public class ADChunkSampleStreamTest {

  List<ChunkSample> samples = new ArrayList<ChunkSample>();

  @Test
  public void testSimpleCount() throws IOException {
    assertEquals(4, samples.size());
  }

  @Test
  public void testChunks() throws IOException {

    assertEquals("Inicia", samples.get(0).getSentence()[0]);
    assertEquals("v-fin", samples.get(0).getTags()[0]);
    assertEquals("B-NP", samples.get(0).getPreds()[2]);

    assertEquals("em", samples.get(0).getSentence()[1]);
    assertEquals("prp", samples.get(0).getTags()[1]);
    assertEquals("B-PP", samples.get(0).getPreds()[1]);

    assertEquals("o", samples.get(0).getSentence()[2]);
    assertEquals("art", samples.get(0).getTags()[2]);
    assertEquals("B-NP", samples.get(0).getPreds()[2]);

    assertEquals("próximo", samples.get(0).getSentence()[3]);
    assertEquals("adj", samples.get(0).getTags()[3]);
    assertEquals("I-NP", samples.get(0).getPreds()[3]);
    
    assertEquals("Casas", samples.get(3).getSentence()[0]);
    assertEquals("n", samples.get(3).getTags()[0]);
    assertEquals("B-NP", samples.get(3).getPreds()[0]);

  }

  @Before
  public void setup() throws IOException {
    InputStream in = ADParagraphStreamTest.class
	.getResourceAsStream("/opennlp/tools/formats/ad.sample");

    ADChunkSampleStream stream = new ADChunkSampleStream(
	new PlainTextByLineStream(in, "UTF-8"));

    ChunkSample sample = stream.read();

    while (sample != null) {
      samples.add(sample);
      sample = stream.read();
    }
  }

}
