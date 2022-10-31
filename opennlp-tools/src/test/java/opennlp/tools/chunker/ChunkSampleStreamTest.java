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

package opennlp.tools.chunker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class ChunkSampleStreamTest {

  @Test
  void testReadingEvents() throws IOException {

    String sample = "word11 tag11 pred11" +
        '\n' +
        "word12 tag12 pred12" +
        '\n' +
        "word13 tag13 pred13" +
        '\n' +
        '\n' +
        "word21 tag21 pred21" +
        '\n' +
        "word22 tag22 pred22" +
        '\n' +
        "word23 tag23 pred23" +
        '\n';

    // First sample sentence

    // Start next sample sentence

    // Second sample sentence

    ObjectStream<String> stringStream = new PlainTextByLineStream(
        new MockInputStreamFactory(sample), StandardCharsets.UTF_8);

    ObjectStream<ChunkSample> chunkStream = new ChunkSampleStream(stringStream);

    // read first sample
    ChunkSample firstSample = chunkStream.read();
    Assertions.assertEquals("word11", firstSample.getSentence()[0]);
    Assertions.assertEquals("tag11", firstSample.getTags()[0]);
    Assertions.assertEquals("pred11", firstSample.getPreds()[0]);
    Assertions.assertEquals("word12", firstSample.getSentence()[1]);
    Assertions.assertEquals("tag12", firstSample.getTags()[1]);
    Assertions.assertEquals("pred12", firstSample.getPreds()[1]);
    Assertions.assertEquals("word13", firstSample.getSentence()[2]);
    Assertions.assertEquals("tag13", firstSample.getTags()[2]);
    Assertions.assertEquals("pred13", firstSample.getPreds()[2]);


    // read second sample
    ChunkSample secondSample = chunkStream.read();
    Assertions.assertEquals("word21", secondSample.getSentence()[0]);
    Assertions.assertEquals("tag21", secondSample.getTags()[0]);
    Assertions.assertEquals("pred21", secondSample.getPreds()[0]);
    Assertions.assertEquals("word22", secondSample.getSentence()[1]);
    Assertions.assertEquals("tag22", secondSample.getTags()[1]);
    Assertions.assertEquals("pred22", secondSample.getPreds()[1]);
    Assertions.assertEquals("word23", secondSample.getSentence()[2]);
    Assertions.assertEquals("tag23", secondSample.getTags()[2]);
    Assertions.assertEquals("pred23", secondSample.getPreds()[2]);

    Assertions.assertNull(chunkStream.read());

    chunkStream.close();
  }
}
