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
import java.nio.charset.Charset;

import org.junit.Test;

import opennlp.tools.postag.POSSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ConllXPOSSampleStreamTest {

  @Test
  public void testParsingSample() throws IOException {

    InputStreamFactory in = new ResourceAsStreamFactory(ConllXPOSSampleStreamTest.class,
        "/opennlp/tools/formats/conllx.sample");

    ObjectStream<POSSample> sampleStream = new ConllXPOSSampleStream(in,Charset.forName("UTF-8"));

    POSSample a = sampleStream.read();

    String aSentence[] = a.getSentence();
    String aTags[] = a.getTags();

    assertEquals(22, aSentence.length);
    assertEquals(22, aTags.length);

    assertEquals("To", aSentence[0]);
    assertEquals("AC", aTags[0]);

    assertEquals("kendte", aSentence[1]);
    assertEquals("AN", aTags[1]);

    assertEquals("russiske", aSentence[2]);
    assertEquals("AN", aTags[2]);

    assertEquals("historikere", aSentence[3]);
    assertEquals("NC", aTags[3]);

    assertEquals("Andronik", aSentence[4]);
    assertEquals("NP", aTags[4]);

    assertEquals("Andronik", aSentence[5]);
    assertEquals("NP", aTags[5]);

    assertEquals("og", aSentence[6]);
    assertEquals("CC", aTags[6]);

    assertEquals("Igor", aSentence[7]);
    assertEquals("NP", aTags[7]);

    assertEquals("Klamkin", aSentence[8]);
    assertEquals("NP", aTags[8]);

    assertEquals("tror", aSentence[9]);
    assertEquals("VA", aTags[9]);

    assertEquals("ikke", aSentence[10]);
    assertEquals("RG", aTags[10]);

    assertEquals(",", aSentence[11]);
    assertEquals("XP", aTags[11]);

    assertEquals("at", aSentence[12]);
    assertEquals("CS", aTags[12]);

    assertEquals("Rusland", aSentence[13]);
    assertEquals("NP", aTags[13]);

    assertEquals("kan", aSentence[14]);
    assertEquals("VA", aTags[14]);

    assertEquals("udvikles", aSentence[15]);
    assertEquals("VA", aTags[15]);

    assertEquals("uden", aSentence[16]);
    assertEquals("SP", aTags[16]);

    assertEquals("en", aSentence[17]);
    assertEquals("PI", aTags[17]);

    assertEquals("\"", aSentence[18]);
    assertEquals("XP", aTags[18]);

    assertEquals("jernnæve", aSentence[19]);
    assertEquals("NC", aTags[19]);

    assertEquals("\"", aSentence[20]);
    assertEquals("XP", aTags[20]);

    assertEquals(".", aSentence[21]);
    assertEquals("XP", aTags[21]);

    POSSample b = sampleStream.read();

    String bSentence[] = b.getSentence();
    String bTags[] = b.getTags();

    assertEquals(12, bSentence.length);
    assertEquals(12, bTags.length);

    assertEquals("De", bSentence[0]);
    assertEquals("PP", bTags[0]);

    assertEquals("hævder", bSentence[1]);
    assertEquals("VA", bTags[1]);

    assertEquals(",", bSentence[2]);
    assertEquals("XP", bTags[2]);

    assertEquals("at", bSentence[3]);
    assertEquals("CS", bTags[3]);

    assertEquals("Ruslands", bSentence[4]);
    assertEquals("NP", bTags[4]);

    assertEquals("vej", bSentence[5]);
    assertEquals("NC", bTags[5]);

    assertEquals("til", bSentence[6]);
    assertEquals("SP", bTags[6]);

    assertEquals("demokrati", bSentence[7]);
    assertEquals("NC", bTags[7]);

    assertEquals("går", bSentence[8]);
    assertEquals("VA", bTags[8]);

    assertEquals("gennem", bSentence[9]);
    assertEquals("SP", bTags[9]);

    assertEquals("diktatur", bSentence[10]);
    assertEquals("NC", bTags[10]);

    assertEquals(".", bSentence[11]);
    assertEquals("XP", bTags[11]);

    assertNull(sampleStream.read());
  }
}
