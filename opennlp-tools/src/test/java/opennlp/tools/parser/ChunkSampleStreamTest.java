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

package opennlp.tools.parser;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

public class ChunkSampleStreamTest {

  @Test
  void testConvertParseToPosSample() throws IOException {
    try (ObjectStream<ChunkSample> chunkSampleStream = new ChunkSampleStream(new ParseSampleStream(
        ObjectStreamUtils.createObjectStream(ParseTest.PARSE_STRING)))) {

      ChunkSample sample = chunkSampleStream.read();

      Assertions.assertEquals("She", sample.getSentence()[0]);
      Assertions.assertEquals("PRP", sample.getTags()[0]);
      Assertions.assertEquals("S-NP", sample.getPreds()[0]);
      Assertions.assertEquals("was", sample.getSentence()[1]);
      Assertions.assertEquals("VBD", sample.getTags()[1]);
      Assertions.assertEquals("O", sample.getPreds()[1]);
      Assertions.assertEquals("just", sample.getSentence()[2]);
      Assertions.assertEquals("RB", sample.getTags()[2]);
      Assertions.assertEquals("S-ADVP", sample.getPreds()[2]);
      Assertions.assertEquals("another", sample.getSentence()[3]);
      Assertions.assertEquals("DT", sample.getTags()[3]);
      Assertions.assertEquals("S-NP", sample.getPreds()[3]);
      Assertions.assertEquals("freighter", sample.getSentence()[4]);
      Assertions.assertEquals("NN", sample.getTags()[4]);
      Assertions.assertEquals("C-NP", sample.getPreds()[4]);
      Assertions.assertEquals("from", sample.getSentence()[5]);
      Assertions.assertEquals("IN", sample.getTags()[5]);
      Assertions.assertEquals("O", sample.getPreds()[5]);
      Assertions.assertEquals("the", sample.getSentence()[6]);
      Assertions.assertEquals("DT", sample.getTags()[6]);
      Assertions.assertEquals("S-NP", sample.getPreds()[6]);
      Assertions.assertEquals("States", sample.getSentence()[7]);
      Assertions.assertEquals("NNPS", sample.getTags()[7]);
      Assertions.assertEquals("C-NP", sample.getPreds()[7]);
      Assertions.assertEquals(",", sample.getSentence()[8]);
      Assertions.assertEquals(",", sample.getTags()[8]);
      Assertions.assertEquals("O", sample.getPreds()[8]);
      Assertions.assertEquals("and", sample.getSentence()[9]);
      Assertions.assertEquals("CC", sample.getTags()[9]);
      Assertions.assertEquals("O", sample.getPreds()[9]);
      Assertions.assertEquals("she", sample.getSentence()[10]);
      Assertions.assertEquals("PRP", sample.getTags()[10]);
      Assertions.assertEquals("S-NP", sample.getPreds()[10]);
      Assertions.assertEquals("seemed", sample.getSentence()[11]);
      Assertions.assertEquals("VBD", sample.getTags()[11]);
      Assertions.assertEquals("O", sample.getPreds()[11]);
      Assertions.assertEquals("as", sample.getSentence()[12]);
      Assertions.assertEquals("RB", sample.getTags()[12]);
      Assertions.assertEquals("S-ADJP", sample.getPreds()[12]);
      Assertions.assertEquals("commonplace", sample.getSentence()[13]);
      Assertions.assertEquals("JJ", sample.getTags()[13]);
      Assertions.assertEquals("C-ADJP", sample.getPreds()[13]);
      Assertions.assertEquals("as", sample.getSentence()[14]);
      Assertions.assertEquals("IN", sample.getTags()[14]);
      Assertions.assertEquals("O", sample.getPreds()[14]);
      Assertions.assertEquals("her", sample.getSentence()[15]);
      Assertions.assertEquals("PRP$", sample.getTags()[15]);
      Assertions.assertEquals("S-NP", sample.getPreds()[15]);
      Assertions.assertEquals("name", sample.getSentence()[16]);
      Assertions.assertEquals("NN", sample.getTags()[16]);
      Assertions.assertEquals("C-NP", sample.getPreds()[16]);
      Assertions.assertEquals(".", sample.getSentence()[17]);
      Assertions.assertEquals(".", sample.getTags()[17]);
      Assertions.assertEquals("O", sample.getPreds()[17]);

      Assertions.assertNull(chunkSampleStream.read());
    }
  }
}
