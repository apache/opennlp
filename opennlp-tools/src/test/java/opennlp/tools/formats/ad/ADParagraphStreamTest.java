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
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;

public class ADParagraphStreamTest {

  public static final int NUM_SENTENCES = 8;

  @Test
  public void testSimpleReading() throws IOException {
    int count = 0;

    ADSentenceStream stream = openData();

    ADSentenceStream.Sentence paragraph = stream.read();
    paragraph.getRoot();
    while (paragraph != null) {
      count++;
      paragraph = stream.read();
      // paragraph.getRoot();
    }

    Assert.assertEquals(ADParagraphStreamTest.NUM_SENTENCES, count);
  }

  @Test
  public void testLeadingWithContraction() throws IOException {
    int count = 0;

    ADSentenceStream stream = openData();

    ADSentenceStream.Sentence paragraph = stream.read();
    while (paragraph != null) {

      count++;
      paragraph = stream.read();
    }

    Assert.assertEquals(ADParagraphStreamTest.NUM_SENTENCES, count);
  }

  private static ADSentenceStream openData() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(ADParagraphStreamTest.class,
        "/opennlp/tools/formats/ad.sample");

    return new ADSentenceStream(new PlainTextByLineStream(in, "UTF-8"));
  }
}
