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

package opennlp.tools.formats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;

public class ConllXPOSSampleStreamTest extends AbstractSampleStreamTest {

  @Test
  void testParsingSample() throws IOException {

    try (ObjectStream<POSSample> sampleStream = new ConllXPOSSampleStream(
            getFactory("conllx.sample"), StandardCharsets.UTF_8)) {
      POSSample a = sampleStream.read();

      String[] aSentence = a.getSentence();
      String[] aTags = a.getTags();

      Assertions.assertEquals(22, aSentence.length);
      Assertions.assertEquals(22, aTags.length);

      Assertions.assertEquals("To", aSentence[0]);
      Assertions.assertEquals("AC", aTags[0]);

      Assertions.assertEquals("kendte", aSentence[1]);
      Assertions.assertEquals("AN", aTags[1]);

      Assertions.assertEquals("russiske", aSentence[2]);
      Assertions.assertEquals("AN", aTags[2]);

      Assertions.assertEquals("historikere", aSentence[3]);
      Assertions.assertEquals("NC", aTags[3]);

      Assertions.assertEquals("Andronik", aSentence[4]);
      Assertions.assertEquals("NP", aTags[4]);

      Assertions.assertEquals("Andronik", aSentence[5]);
      Assertions.assertEquals("NP", aTags[5]);

      Assertions.assertEquals("og", aSentence[6]);
      Assertions.assertEquals("CC", aTags[6]);

      Assertions.assertEquals("Igor", aSentence[7]);
      Assertions.assertEquals("NP", aTags[7]);

      Assertions.assertEquals("Klamkin", aSentence[8]);
      Assertions.assertEquals("NP", aTags[8]);

      Assertions.assertEquals("tror", aSentence[9]);
      Assertions.assertEquals("VA", aTags[9]);

      Assertions.assertEquals("ikke", aSentence[10]);
      Assertions.assertEquals("RG", aTags[10]);

      Assertions.assertEquals(",", aSentence[11]);
      Assertions.assertEquals("XP", aTags[11]);

      Assertions.assertEquals("at", aSentence[12]);
      Assertions.assertEquals("CS", aTags[12]);

      Assertions.assertEquals("Rusland", aSentence[13]);
      Assertions.assertEquals("NP", aTags[13]);

      Assertions.assertEquals("kan", aSentence[14]);
      Assertions.assertEquals("VA", aTags[14]);

      Assertions.assertEquals("udvikles", aSentence[15]);
      Assertions.assertEquals("VA", aTags[15]);

      Assertions.assertEquals("uden", aSentence[16]);
      Assertions.assertEquals("SP", aTags[16]);

      Assertions.assertEquals("en", aSentence[17]);
      Assertions.assertEquals("PI", aTags[17]);

      Assertions.assertEquals("\"", aSentence[18]);
      Assertions.assertEquals("XP", aTags[18]);

      Assertions.assertEquals("jernnæve", aSentence[19]);
      Assertions.assertEquals("NC", aTags[19]);

      Assertions.assertEquals("\"", aSentence[20]);
      Assertions.assertEquals("XP", aTags[20]);

      Assertions.assertEquals(".", aSentence[21]);
      Assertions.assertEquals("XP", aTags[21]);

      POSSample b = sampleStream.read();

      String[] bSentence = b.getSentence();
      String[] bTags = b.getTags();

      Assertions.assertEquals(12, bSentence.length);
      Assertions.assertEquals(12, bTags.length);

      Assertions.assertEquals("De", bSentence[0]);
      Assertions.assertEquals("PP", bTags[0]);

      Assertions.assertEquals("hævder", bSentence[1]);
      Assertions.assertEquals("VA", bTags[1]);

      Assertions.assertEquals(",", bSentence[2]);
      Assertions.assertEquals("XP", bTags[2]);

      Assertions.assertEquals("at", bSentence[3]);
      Assertions.assertEquals("CS", bTags[3]);

      Assertions.assertEquals("Ruslands", bSentence[4]);
      Assertions.assertEquals("NP", bTags[4]);

      Assertions.assertEquals("vej", bSentence[5]);
      Assertions.assertEquals("NC", bTags[5]);

      Assertions.assertEquals("til", bSentence[6]);
      Assertions.assertEquals("SP", bTags[6]);

      Assertions.assertEquals("demokrati", bSentence[7]);
      Assertions.assertEquals("NC", bTags[7]);

      Assertions.assertEquals("går", bSentence[8]);
      Assertions.assertEquals("VA", bTags[8]);

      Assertions.assertEquals("gennem", bSentence[9]);
      Assertions.assertEquals("SP", bTags[9]);

      Assertions.assertEquals("diktatur", bSentence[10]);
      Assertions.assertEquals("NC", bTags[10]);

      Assertions.assertEquals(".", bSentence[11]);
      Assertions.assertEquals("XP", bTags[11]);

      Assertions.assertNull(sampleStream.read());
    }
  }
}
