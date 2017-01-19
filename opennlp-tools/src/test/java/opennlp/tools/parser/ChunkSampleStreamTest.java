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

import org.junit.Test;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ChunkSampleStreamTest {

  @Test
  public void testConvertParseToPosSample() throws IOException {
    ObjectStream<ChunkSample> chunkSampleStream = new ChunkSampleStream(new ParseSampleStream(
        ObjectStreamUtils.createObjectStream(ParseTest.PARSE_STRING)));

    ChunkSample sample = chunkSampleStream.read();

    assertEquals("She", sample.getSentence()[0]);
    assertEquals("PRP", sample.getTags()[0]);
    assertEquals("S-NP", sample.getPreds()[0]);
    assertEquals("was", sample.getSentence()[1]);
    assertEquals("VBD", sample.getTags()[1]);
    assertEquals("O", sample.getPreds()[1]);
    assertEquals("just", sample.getSentence()[2]);
    assertEquals("RB", sample.getTags()[2]);
    assertEquals("S-ADVP", sample.getPreds()[2]);
    assertEquals("another", sample.getSentence()[3]);
    assertEquals("DT", sample.getTags()[3]);
    assertEquals("S-NP", sample.getPreds()[3]);
    assertEquals("freighter", sample.getSentence()[4]);
    assertEquals("NN", sample.getTags()[4]);
    assertEquals("C-NP", sample.getPreds()[4]);
    assertEquals("from", sample.getSentence()[5]);
    assertEquals("IN", sample.getTags()[5]);
    assertEquals("O", sample.getPreds()[5]);
    assertEquals("the", sample.getSentence()[6]);
    assertEquals("DT", sample.getTags()[6]);
    assertEquals("S-NP", sample.getPreds()[6]);
    assertEquals("States", sample.getSentence()[7]);
    assertEquals("NNPS", sample.getTags()[7]);
    assertEquals("C-NP", sample.getPreds()[7]);
    assertEquals(",", sample.getSentence()[8]);
    assertEquals(",", sample.getTags()[8]);
    assertEquals("O", sample.getPreds()[8]);
    assertEquals("and", sample.getSentence()[9]);
    assertEquals("CC", sample.getTags()[9]);
    assertEquals("O", sample.getPreds()[9]);
    assertEquals("she", sample.getSentence()[10]);
    assertEquals("PRP", sample.getTags()[10]);
    assertEquals("S-NP", sample.getPreds()[10]);
    assertEquals("seemed", sample.getSentence()[11]);
    assertEquals("VBD", sample.getTags()[11]);
    assertEquals("O", sample.getPreds()[11]);
    assertEquals("as", sample.getSentence()[12]);
    assertEquals("RB", sample.getTags()[12]);
    assertEquals("S-ADJP", sample.getPreds()[12]);
    assertEquals("commonplace", sample.getSentence()[13]);
    assertEquals("JJ", sample.getTags()[13]);
    assertEquals("C-ADJP", sample.getPreds()[13]);
    assertEquals("as", sample.getSentence()[14]);
    assertEquals("IN", sample.getTags()[14]);
    assertEquals("O", sample.getPreds()[14]);
    assertEquals("her", sample.getSentence()[15]);
    assertEquals("PRP$", sample.getTags()[15]);
    assertEquals("S-NP", sample.getPreds()[15]);
    assertEquals("name", sample.getSentence()[16]);
    assertEquals("NN", sample.getTags()[16]);
    assertEquals("C-NP", sample.getPreds()[16]);
    assertEquals(".", sample.getSentence()[17]);
    assertEquals(".", sample.getTags()[17]);
    assertEquals("O", sample.getPreds()[17]);

    assertNull(chunkSampleStream.read());
  }
}
