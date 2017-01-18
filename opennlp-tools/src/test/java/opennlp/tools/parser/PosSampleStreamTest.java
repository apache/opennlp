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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

public class PosSampleStreamTest {

  @Test
  public void testConvertParseToPosSample() throws IOException {

    ObjectStream<POSSample> posSampleStream = new PosSampleStream(new ParseSampleStream(
        ObjectStreamUtils.createObjectStream(ParseTest.PARSE_STRING)));

    POSSample sample = posSampleStream.read();

    Assert.assertEquals("PRP", sample.getTags()[0]);
    Assert.assertEquals("She", sample.getSentence()[0]);
    Assert.assertEquals("VBD", sample.getTags()[1]);
    Assert.assertEquals("was", sample.getSentence()[1]);
    Assert.assertEquals("RB", sample.getTags()[2]);
    Assert.assertEquals("just", sample.getSentence()[2]);
    Assert.assertEquals("DT", sample.getTags()[3]);
    Assert.assertEquals("another", sample.getSentence()[3]);
    Assert.assertEquals("NN", sample.getTags()[4]);
    Assert.assertEquals("freighter", sample.getSentence()[4]);
    Assert.assertEquals("IN", sample.getTags()[5]);
    Assert.assertEquals("from", sample.getSentence()[5]);
    Assert.assertEquals("DT", sample.getTags()[6]);
    Assert.assertEquals("the", sample.getSentence()[6]);
    Assert.assertEquals("NNPS", sample.getTags()[7]);
    Assert.assertEquals("States", sample.getSentence()[7]);
    Assert.assertEquals(",", sample.getTags()[8]);
    Assert.assertEquals(",", sample.getSentence()[8]);
    Assert.assertEquals("CC", sample.getTags()[9]);
    Assert.assertEquals("and", sample.getSentence()[9]);
    Assert.assertEquals("PRP", sample.getTags()[10]);
    Assert.assertEquals("she", sample.getSentence()[10]);
    Assert.assertEquals("VBD", sample.getTags()[11]);
    Assert.assertEquals("seemed", sample.getSentence()[11]);
    Assert.assertEquals("RB", sample.getTags()[12]);
    Assert.assertEquals("as", sample.getSentence()[12]);
    Assert.assertEquals("JJ", sample.getTags()[13]);
    Assert.assertEquals("commonplace", sample.getSentence()[13]);
    Assert.assertEquals("IN", sample.getTags()[14]);
    Assert.assertEquals("as", sample.getSentence()[14]);
    Assert.assertEquals("PRP$", sample.getTags()[15]);
    Assert.assertEquals("her", sample.getSentence()[15]);
    Assert.assertEquals("NN", sample.getTags()[16]);
    Assert.assertEquals("name", sample.getSentence()[16]);
    Assert.assertEquals(".", sample.getTags()[17]);
    Assert.assertEquals(".", sample.getSentence()[17]);

    Assert.assertNull(posSampleStream.read());
  }
}
