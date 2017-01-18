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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.PlainTextByLineStream;

public class ADPOSSampleStreamTest {

  @Test
  public void testSimple() throws IOException {
    // add one sentence with expandME = includeFeats = false
    ADPOSSampleStream stream = new ADPOSSampleStream(
        new PlainTextByLineStream(new ResourceAsStreamFactory(
            ADParagraphStreamTest.class, "/opennlp/tools/formats/ad.sample"),
            "UTF-8"), false, false);

    POSSample sample = stream.read();

    Assert.assertEquals(23, sample.getSentence().length);

    Assert.assertEquals("Inicia", sample.getSentence()[0]);
    Assert.assertEquals("v-fin", sample.getTags()[0]);

    Assert.assertEquals("em", sample.getSentence()[1]);
    Assert.assertEquals("prp", sample.getTags()[1]);

    Assert.assertEquals("o", sample.getSentence()[2]);
    Assert.assertEquals("art", sample.getTags()[2]);

    Assert.assertEquals("Porto_Poesia", sample.getSentence()[9]);
    Assert.assertEquals("prop", sample.getTags()[9]);
  }

  @Test
  public void testExpandME() throws IOException {
    // add one sentence with expandME = true
    ADPOSSampleStream stream = new ADPOSSampleStream(
        new PlainTextByLineStream(new ResourceAsStreamFactory(
            ADParagraphStreamTest.class, "/opennlp/tools/formats/ad.sample"),
            "UTF-8"), true, false);

    POSSample sample = stream.read();

    Assert.assertEquals(27, sample.getSentence().length);

    Assert.assertEquals("Inicia", sample.getSentence()[0]);
    Assert.assertEquals("v-fin", sample.getTags()[0]);

    Assert.assertEquals("em", sample.getSentence()[1]);
    Assert.assertEquals("prp", sample.getTags()[1]);

    Assert.assertEquals("o", sample.getSentence()[2]);
    Assert.assertEquals("art", sample.getTags()[2]);

    Assert.assertEquals("Porto", sample.getSentence()[9]);
    Assert.assertEquals("B-prop", sample.getTags()[9]);

    Assert.assertEquals("Poesia", sample.getSentence()[10]);
    Assert.assertEquals("I-prop", sample.getTags()[10]);
  }

  @Test
  public void testIncludeFeats() throws IOException {
    // add one sentence with includeFeats = true
    ADPOSSampleStream stream = new ADPOSSampleStream(
        new PlainTextByLineStream(new ResourceAsStreamFactory(
            ADParagraphStreamTest.class, "/opennlp/tools/formats/ad.sample"),
            "UTF-8"), false, true);

    POSSample sample = stream.read();

    Assert.assertEquals(23, sample.getSentence().length);

    Assert.assertEquals("Inicia", sample.getSentence()[0]);
    Assert.assertEquals("v-fin=PR=3S=IND=VFIN", sample.getTags()[0]);

    Assert.assertEquals("em", sample.getSentence()[1]);
    Assert.assertEquals("prp", sample.getTags()[1]);

    Assert.assertEquals("o", sample.getSentence()[2]);
    Assert.assertEquals("art=DET=M=S", sample.getTags()[2]);

    Assert.assertEquals("Porto_Poesia", sample.getSentence()[9]);
    Assert.assertEquals("prop=M=S", sample.getTags()[9]);
  }

}
