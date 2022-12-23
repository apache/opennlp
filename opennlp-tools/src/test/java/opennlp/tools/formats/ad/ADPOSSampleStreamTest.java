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

import opennlp.tools.postag.POSSample;
import opennlp.tools.util.PlainTextByLineStream;

public class ADPOSSampleStreamTest extends AbstractADSampleStreamTest<POSSample> {

  @BeforeEach
  void setup() throws IOException {
    super.setup();
  }

  @Test
  void testSimple() throws IOException {
    // add one sentence with expandME = includeFeats = false
    try (ADPOSSampleStream stream = new ADPOSSampleStream(new PlainTextByLineStream(in,
            StandardCharsets.UTF_8), false, false)) {
      POSSample sample = stream.read();

      Assertions.assertEquals(23, sample.getSentence().length);

      Assertions.assertEquals("Inicia", sample.getSentence()[0]);
      Assertions.assertEquals("v-fin", sample.getTags()[0]);

      Assertions.assertEquals("em", sample.getSentence()[1]);
      Assertions.assertEquals("prp", sample.getTags()[1]);

      Assertions.assertEquals("o", sample.getSentence()[2]);
      Assertions.assertEquals("art", sample.getTags()[2]);

      Assertions.assertEquals("Porto_Poesia", sample.getSentence()[9]);
      Assertions.assertEquals("prop", sample.getTags()[9]);
    }
  }

  @Test
  void testExpandME() throws IOException {
    // add one sentence with expandME = true
    try (ADPOSSampleStream stream = new ADPOSSampleStream(new PlainTextByLineStream(in,
            StandardCharsets.UTF_8), true, false)) {

      POSSample sample = stream.read();

      Assertions.assertEquals(27, sample.getSentence().length);

      Assertions.assertEquals("Inicia", sample.getSentence()[0]);
      Assertions.assertEquals("v-fin", sample.getTags()[0]);

      Assertions.assertEquals("em", sample.getSentence()[1]);
      Assertions.assertEquals("prp", sample.getTags()[1]);

      Assertions.assertEquals("o", sample.getSentence()[2]);
      Assertions.assertEquals("art", sample.getTags()[2]);

      Assertions.assertEquals("Porto", sample.getSentence()[9]);
      Assertions.assertEquals("B-prop", sample.getTags()[9]);

      Assertions.assertEquals("Poesia", sample.getSentence()[10]);
      Assertions.assertEquals("I-prop", sample.getTags()[10]);
    }
  }

  @Test
  void testIncludeFeats() throws IOException {
    // add one sentence with includeFeats = true
    try (ADPOSSampleStream stream = new ADPOSSampleStream(new PlainTextByLineStream(in,
            StandardCharsets.UTF_8), false, true)) {

      POSSample sample = stream.read();

      Assertions.assertEquals(23, sample.getSentence().length);

      Assertions.assertEquals("Inicia", sample.getSentence()[0]);
      Assertions.assertEquals("v-fin=PR=3S=IND=VFIN", sample.getTags()[0]);

      Assertions.assertEquals("em", sample.getSentence()[1]);
      Assertions.assertEquals("prp", sample.getTags()[1]);

      Assertions.assertEquals("o", sample.getSentence()[2]);
      Assertions.assertEquals("art=DET=M=S", sample.getTags()[2]);

      Assertions.assertEquals("Porto_Poesia", sample.getSentence()[9]);
      Assertions.assertEquals("prop=M=S", sample.getTags()[9]);
    }
  }

}
