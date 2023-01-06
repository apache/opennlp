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

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.PlainTextByLineStream;

public class ADParagraphStreamTest extends AbstractADSampleStreamTest<SentenceSample> {

  private ADSentenceStream stream;

  @BeforeEach
  void setup() throws IOException {
    super.setup();
    stream = new ADSentenceStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
    Assertions.assertNotNull(stream);
  }

  @Test
  void testSimpleReading() throws IOException {
    int count = 0;

    ADSentenceStream.Sentence paragraph = stream.read();
    paragraph.getRoot();
    while (paragraph != null) {
      count++;
      paragraph = stream.read();
      // paragraph.getRoot();
    }

    Assertions.assertEquals(ADParagraphStreamTest.NUM_SENTENCES, count);
  }

  @Test
  void testLeadingWithContraction() throws IOException {
    int count = 0;

    ADSentenceStream.Sentence paragraph = stream.read();
    while (paragraph != null) {

      count++;
      paragraph = stream.read();
    }

    Assertions.assertEquals(ADParagraphStreamTest.NUM_SENTENCES, count);
  }
}
