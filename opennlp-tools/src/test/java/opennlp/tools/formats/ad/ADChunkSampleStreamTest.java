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
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.util.PlainTextByLineStream;

public class ADChunkSampleStreamTest extends AbstractADSampleStreamTest<ChunkSample> {

  @BeforeEach
  void setup() throws IOException {
    super.setup();
    try (ADChunkSampleStream stream = new ADChunkSampleStream(
            new PlainTextByLineStream(in, StandardCharsets.UTF_8))) {
      ChunkSample sample;
      while ((sample = stream.read()) != null) {
        samples.add(sample);
      }
      Assertions.assertFalse(samples.isEmpty());
    }
  }
  
  @Test
  void testSimpleCount() {
    Assertions.assertEquals(NUM_SENTENCES, samples.size());
  }

  @Test
  void testChunks() {

    Assertions.assertEquals("Inicia", samples.get(0).getSentence()[0]);
    Assertions.assertEquals("v-fin", samples.get(0).getTags()[0]);
    Assertions.assertEquals("B-VP", samples.get(0).getPreds()[0]);

    Assertions.assertEquals("em", samples.get(0).getSentence()[1]);
    Assertions.assertEquals("prp", samples.get(0).getTags()[1]);
    Assertions.assertEquals("B-PP", samples.get(0).getPreds()[1]);

    Assertions.assertEquals("o", samples.get(0).getSentence()[2]);
    Assertions.assertEquals("art", samples.get(0).getTags()[2]);
    Assertions.assertEquals("B-NP", samples.get(0).getPreds()[2]);

    Assertions.assertEquals("próximo", samples.get(0).getSentence()[3]);
    Assertions.assertEquals("adj", samples.get(0).getTags()[3]);
    Assertions.assertEquals("I-NP", samples.get(0).getPreds()[3]);

    Assertions.assertEquals("Casas", samples.get(3).getSentence()[0]);
    Assertions.assertEquals("n", samples.get(3).getTags()[0]);
    Assertions.assertEquals("B-NP", samples.get(3).getPreds()[0]);
  }

}
