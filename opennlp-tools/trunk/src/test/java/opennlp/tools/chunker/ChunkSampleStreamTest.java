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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.StringReader;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

import org.junit.Test;

public class ChunkSampleStreamTest{

  @Test
  public void testReadingEvents() throws IOException {
    
    StringBuilder sample = new StringBuilder();
    
    // First sample sentence
    sample.append("word11 tag11 pred11");
    sample.append('\n');
    sample.append("word12 tag12 pred12");
    sample.append('\n');
    sample.append("word13 tag13 pred13");
    sample.append('\n');
    
    // Start next sample sentence
    sample.append('\n');
    
    // Second sample sentence
    sample.append("word21 tag21 pred21");
    sample.append('\n');
    sample.append("word22 tag22 pred22");
    sample.append('\n');
    sample.append("word23 tag23 pred23");
    sample.append('\n');
    
    ObjectStream<String> stringStream = new PlainTextByLineStream(new StringReader(sample.toString()));
    
    ObjectStream<ChunkSample> chunkStream = new ChunkSampleStream(stringStream);
    
    // read first sample
    ChunkSample firstSample = chunkStream.read();
    assertEquals("word11", firstSample.getSentence()[0]);
    assertEquals("tag11", firstSample.getTags()[0]);
    assertEquals("pred11", firstSample.getPreds()[0]);
    assertEquals("word12", firstSample.getSentence()[1]);
    assertEquals("tag12", firstSample.getTags()[1]);
    assertEquals("pred12", firstSample.getPreds()[1]);
    assertEquals("word13", firstSample.getSentence()[2]);
    assertEquals("tag13", firstSample.getTags()[2]);
    assertEquals("pred13", firstSample.getPreds()[2]);
    
    
    // read second sample
    ChunkSample secondSample = chunkStream.read();
    assertEquals("word21", secondSample.getSentence()[0]);
    assertEquals("tag21", secondSample.getTags()[0]);
    assertEquals("pred21", secondSample.getPreds()[0]);
    assertEquals("word22", secondSample.getSentence()[1]);
    assertEquals("tag22", secondSample.getTags()[1]);
    assertEquals("pred22", secondSample.getPreds()[1]);
    assertEquals("word23", secondSample.getSentence()[2]);
    assertEquals("tag23", secondSample.getTags()[2]);
    assertEquals("pred23", secondSample.getPreds()[2]);
    
    assertNull(chunkStream.read());
  }
}
