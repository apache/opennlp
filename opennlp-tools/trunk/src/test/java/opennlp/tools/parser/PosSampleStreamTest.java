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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

import org.junit.Test;

public class PosSampleStreamTest {
  
  @Test
  public void testConvertParseToPosSample() throws IOException {
    
    ObjectStream<POSSample> posSampleStream = new PosSampleStream(new ParseSampleStream(
        ObjectStreamUtils.createObjectStream(ParseTest.PARSE_STRING)));
    
    POSSample sample = posSampleStream.read();
    
    assertEquals("PRP", sample.getTags()[0]);
    assertEquals("She", sample.getSentence()[0]);
    assertEquals("VBD", sample.getTags()[1]);
    assertEquals("was", sample.getSentence()[1]);
    assertEquals("RB", sample.getTags()[2]);
    assertEquals("just", sample.getSentence()[2]);
    assertEquals("DT", sample.getTags()[3]);
    assertEquals("another", sample.getSentence()[3]);
    assertEquals("NN", sample.getTags()[4]);
    assertEquals("freighter", sample.getSentence()[4]);
    assertEquals("IN", sample.getTags()[5]);
    assertEquals("from", sample.getSentence()[5]);
    assertEquals("DT", sample.getTags()[6]);
    assertEquals("the", sample.getSentence()[6]);
    assertEquals("NNPS", sample.getTags()[7]);
    assertEquals("States", sample.getSentence()[7]);
    assertEquals(",", sample.getTags()[8]);
    assertEquals(",", sample.getSentence()[8]);
    assertEquals("CC", sample.getTags()[9]);
    assertEquals("and", sample.getSentence()[9]);
    assertEquals("PRP", sample.getTags()[10]);
    assertEquals("she", sample.getSentence()[10]);
    assertEquals("VBD", sample.getTags()[11]);
    assertEquals("seemed", sample.getSentence()[11]);
    assertEquals("RB", sample.getTags()[12]);
    assertEquals("as", sample.getSentence()[12]);
    assertEquals("JJ", sample.getTags()[13]);
    assertEquals("commonplace", sample.getSentence()[13]);
    assertEquals("IN", sample.getTags()[14]);
    assertEquals("as", sample.getSentence()[14]);
    assertEquals("PRP$", sample.getTags()[15]);
    assertEquals("her", sample.getSentence()[15]);
    assertEquals("NN", sample.getTags()[16]);
    assertEquals("name", sample.getSentence()[16]);
    assertEquals(".", sample.getTags()[17]);
    assertEquals(".", sample.getSentence()[17]);
    
    assertNull(posSampleStream.read());
  }
}
