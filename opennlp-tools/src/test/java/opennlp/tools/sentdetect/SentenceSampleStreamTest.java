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

package opennlp.tools.sentdetect;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;

public class SentenceSampleStreamTest {

  @Test
  public void testSentenceSampleStreamMissingEOS() throws IOException {

    InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/sentdetect/SentencesMissingEOS.txt");

    SentenceSampleStream sampleStream = new SentenceSampleStream(new PlainTextByLineStream(in,
        StandardCharsets.UTF_8), new char[]{'.','!','?'}, '*');

    List<SentenceSample> entries = new ArrayList();

    SentenceSample sample = sampleStream.read();
    while (sample != null) {
      System.out.println(sample);

      entries.add(sample);

      sample = sampleStream.read();
    }

    int end = entries.get(0).getSentences()[0].getEnd();
    Assert.assertEquals("!", entries.get(0).getDocument().substring(end - 1, end));

    end = entries.get(0).getSentences()[1].getEnd();
    Assert.assertEquals("*", entries.get(0).getDocument().substring(end - 1, end));

    end = entries.get(0).getSentences()[2].getEnd();
    Assert.assertEquals(".", entries.get(0).getDocument().substring(end - 1, end));


    end = entries.get(1).getSentences()[0].getEnd();
    Assert.assertEquals("*", entries.get(1).getDocument().substring(end - 1, end));

    end = entries.get(1).getSentences()[1].getEnd();
    Assert.assertEquals(".", entries.get(1).getDocument().substring(end - 1, end));

    end = entries.get(1).getSentences()[2].getEnd();
    Assert.assertEquals("*", entries.get(1).getDocument().substring(end - 1, end));

    end = entries.get(1).getSentences()[3].getEnd();
    Assert.assertEquals(".", entries.get(1).getDocument().substring(end - 1, end));

  }
}
