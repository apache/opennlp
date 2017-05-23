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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;

public class ConlluTokenSampleStreamTest {

  @Test
  public void testParseTwoSentences() throws IOException {
    InputStreamFactory streamFactory =
        new ResourceAsStreamFactory(ConlluStreamTest.class, "de-ud-train-sample.conllu");

    try (ObjectStream<TokenSample> stream = new ConlluTokenSampleStream(new ConlluStream(streamFactory))) {

      TokenSample expected1 = TokenSample.parse(
          "Fachlich kompetent" + TokenSample.DEFAULT_SEPARATOR_CHARS
          + ", sehr gute Beratung und ein freundliches Team" + TokenSample.DEFAULT_SEPARATOR_CHARS
          + ".", TokenSample.DEFAULT_SEPARATOR_CHARS);
      Assert.assertEquals(expected1, stream.read());

      TokenSample expected2 = TokenSample.parse("Beiden Zahnärzten verdanke ich einen " +
          "neuen Biss und dadurch endlich keine Rückenschmerzen mehr"
          + TokenSample.DEFAULT_SEPARATOR_CHARS + ".", TokenSample.DEFAULT_SEPARATOR_CHARS);
      Assert.assertEquals(expected2, stream.read());

      Assert.assertNull("Stream must be exhausted", stream.read());
    }
  }
}
