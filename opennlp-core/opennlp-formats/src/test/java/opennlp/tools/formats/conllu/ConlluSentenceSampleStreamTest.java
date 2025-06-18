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

package opennlp.tools.formats.conllu;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class ConlluSentenceSampleStreamTest extends AbstractConlluSampleStreamTest<SentenceSample> {

  @Test
  void testParseTwoSentences() throws IOException {
    ConlluStream cStream = getStream("de-ud-train-sample.conllu");
    Assertions.assertNotNull(cStream);

    try (ObjectStream<SentenceSample> stream = new ConlluSentenceSampleStream(cStream, 1)) {

      SentenceSample sample1 = stream.read();

      Assertions.assertEquals("Fachlich kompetent, sehr gute Beratung und ein freundliches Team.",
          sample1.getDocument());

      Assertions.assertEquals(new Span(0, 65), sample1.getSentences()[0]);

      SentenceSample sample2 = stream.read();

      Assertions.assertEquals("Beiden Zahnärzten verdanke ich einen neuen Biss und dadurch " +
          "endlich keine Rückenschmerzen mehr.", sample2.getDocument());
      Assertions.assertEquals(new Span(0, 95), sample2.getSentences()[0]);

      Assertions.assertNull(stream.read(), "Stream must be exhausted");
    }

    cStream = getStream("de-ud-train-sample.conllu");
    Assertions.assertNotNull(cStream);
    try (ObjectStream<SentenceSample> stream =
             new ConlluSentenceSampleStream(cStream, 3)) {
      SentenceSample sample = stream.read();

      Assertions.assertEquals("Fachlich kompetent, sehr gute Beratung und ein freundliches Team."
              + " Beiden Zahnärzten verdanke ich einen neuen Biss und dadurch endlich keine "
              + "Rückenschmerzen mehr.",
          sample.getDocument());

      Assertions.assertNull(stream.read(), "Stream must be exhausted");
    }
  }
}
