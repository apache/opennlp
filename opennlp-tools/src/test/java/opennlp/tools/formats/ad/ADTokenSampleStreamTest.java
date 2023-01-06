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

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.ObjectStream;

public class ADTokenSampleStreamTest extends AbstractADSampleStreamTest<TokenSample> {

  @Test
  void testSimpleCount() {
    Assertions.assertEquals(NUM_SENTENCES, samples.size());
  }

  @Test
  void testSentences() {
    Assertions.assertTrue(samples.get(5).getText().contains("ofereceu-me"));
  }

  @BeforeEach
  void setup() throws IOException {
    super.setup();
    
    ADTokenSampleStreamFactory<ADTokenSampleStreamFactory.Parameters> factory =
            new ADTokenSampleStreamFactory<>(ADTokenSampleStreamFactory.Parameters.class);

    File data = new File(getResource("ad.sample").getFile());
    Assertions.assertNotNull(data);
    File dict = new File(getResourceWithoutPrefix("opennlp/tools/tokenize/latin-detokenizer.xml").getFile());
    Assertions.assertNotNull(dict);

    String[] args = {"-data", data.getCanonicalPath(), "-encoding", "UTF-8",
        "-lang", "por", "-detokenizer", dict.getCanonicalPath()};
    try (ObjectStream<TokenSample> tokenSampleStream = factory.create(args)) {
      TokenSample sample = tokenSampleStream.read();

      while (sample != null) {
        samples.add(sample);
        sample = tokenSampleStream.read();
      }
    }
  }

}
