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

import opennlp.tools.lemmatizer.LemmaSample;
import opennlp.tools.util.ObjectStream;

public class ConlluLemmaSampleStreamTest extends AbstractConlluSampleStreamTest<LemmaSample> {


  @Test
  void testParseSpanishS300() throws IOException {
    ConlluStream cStream = getStream("es-ud-sample.conllu");
    Assertions.assertNotNull(cStream);
    
    try (ObjectStream<LemmaSample> stream = new ConlluLemmaSampleStream(cStream, ConlluTagset.U)) {

      LemmaSample predicted = stream.read();
      Assertions.assertEquals("digám+tú+él", predicted.getLemmas()[0]);
      Assertions.assertEquals("la", predicted.getTokens()[3]);
      Assertions.assertEquals("el", predicted.getLemmas()[3]);
    }
  }
}
