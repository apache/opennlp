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

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;

public class ADChunkSampleStreamTest {

  private List<ChunkSample> samples = new ArrayList<>();

  @Test
  public void testSimpleCount() throws IOException {
    Assert.assertEquals(ADParagraphStreamTest.NUM_SENTENCES, samples.size());
  }

  @Test
  public void testChunks() throws IOException {

    Assert.assertEquals("Inicia", samples.get(0).getSentence()[0]);
    Assert.assertEquals("v-fin", samples.get(0).getTags()[0]);
    Assert.assertEquals("B-VP", samples.get(0).getPreds()[0]);

    Assert.assertEquals("em", samples.get(0).getSentence()[1]);
    Assert.assertEquals("prp", samples.get(0).getTags()[1]);
    Assert.assertEquals("B-PP", samples.get(0).getPreds()[1]);

    Assert.assertEquals("o", samples.get(0).getSentence()[2]);
    Assert.assertEquals("art", samples.get(0).getTags()[2]);
    Assert.assertEquals("B-NP", samples.get(0).getPreds()[2]);

    Assert.assertEquals("próximo", samples.get(0).getSentence()[3]);
    Assert.assertEquals("adj", samples.get(0).getTags()[3]);
    Assert.assertEquals("I-NP", samples.get(0).getPreds()[3]);

    Assert.assertEquals("Casas", samples.get(3).getSentence()[0]);
    Assert.assertEquals("n", samples.get(3).getTags()[0]);
    Assert.assertEquals("B-NP", samples.get(3).getPreds()[0]);
  }

  @Before
  public void setup() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        ADParagraphStreamTest.class, "/opennlp/tools/formats/ad.sample");

    ADChunkSampleStream stream = new ADChunkSampleStream(
        new PlainTextByLineStream(in, "UTF-8"));

    ChunkSample sample = stream.read();

    while (sample != null) {
      samples.add(sample);
      sample = stream.read();
    }
  }

}
