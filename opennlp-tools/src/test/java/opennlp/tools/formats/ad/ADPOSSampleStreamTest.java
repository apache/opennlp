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

import opennlp.tools.postag.POSSample;
import opennlp.tools.util.PlainTextByLineStream;

import org.junit.Test;

public class ADPOSSampleStreamTest {

  @Test
  public void testSimple() throws IOException {
    // add one sentence with expandME = includeFeats = false
    ADPOSSampleStream stream = new ADPOSSampleStream(
        new PlainTextByLineStream(
            ADParagraphStreamTest.class
                .getResourceAsStream("/opennlp/tools/formats/ad.sample"),
            "UTF-8"), false, false);

    POSSample sample = stream.read();
    
    assertEquals(23, sample.getSentence().length);
    
    assertEquals("Inicia", sample.getSentence()[0]);
    assertEquals("v-fin", sample.getTags()[0]);
    
    assertEquals("em", sample.getSentence()[1]);
    assertEquals("prp", sample.getTags()[1]);
    
    assertEquals("o", sample.getSentence()[2]);
    assertEquals("art", sample.getTags()[2]);
    
    assertEquals("Porto_Poesia", sample.getSentence()[9]);
    assertEquals("prop", sample.getTags()[9]);
  }
  
  @Test
  public void testExpandME() throws IOException {
    // add one sentence with expandME = true
    ADPOSSampleStream stream = new ADPOSSampleStream(
        new PlainTextByLineStream(
            ADParagraphStreamTest.class
                .getResourceAsStream("/opennlp/tools/formats/ad.sample"),
            "UTF-8"), true, false);

    POSSample sample = stream.read();
    
    assertEquals(27, sample.getSentence().length);
    
    assertEquals("Inicia", sample.getSentence()[0]);
    assertEquals("v-fin", sample.getTags()[0]);
    
    assertEquals("em", sample.getSentence()[1]);
    assertEquals("prp", sample.getTags()[1]);
    
    assertEquals("o", sample.getSentence()[2]);
    assertEquals("art", sample.getTags()[2]);
    
    assertEquals("Porto", sample.getSentence()[9]);
    assertEquals("B-prop", sample.getTags()[9]);
    
    assertEquals("Poesia", sample.getSentence()[10]);
    assertEquals("I-prop", sample.getTags()[10]);
  }
  
  @Test
  public void testIncludeFeats() throws IOException {
    // add one sentence with includeFeats = true
    ADPOSSampleStream stream = new ADPOSSampleStream(
        new PlainTextByLineStream(
            ADParagraphStreamTest.class
                .getResourceAsStream("/opennlp/tools/formats/ad.sample"),
            "UTF-8"), false, true);

    POSSample sample = stream.read();
    
    assertEquals(23, sample.getSentence().length);
    
    assertEquals("Inicia", sample.getSentence()[0]);
    assertEquals("v-fin=PR=3S=IND=VFIN", sample.getTags()[0]);
    
    assertEquals("em", sample.getSentence()[1]);
    assertEquals("prp", sample.getTags()[1]);
    
    assertEquals("o", sample.getSentence()[2]);
    assertEquals("art=DET=M=S", sample.getTags()[2]);
    
    assertEquals("Porto_Poesia", sample.getSentence()[9]);
    assertEquals("prop=M=S", sample.getTags()[9]);
  }

}
