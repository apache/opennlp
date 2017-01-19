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

import org.junit.Test;

import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LeipzigDoccatSampleStreamTest {

  @Test
  public void testParsingSample() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/formats/leipzig-en.sample");

    ObjectStream<DocumentSample> sampleStream =
        new LeipzigDoccatSampleStream("en", 2, in);

    DocumentSample doc1 = sampleStream.read();
    assertEquals("en", doc1.getCategory());

    DocumentSample doc2 = sampleStream.read();
    assertEquals("en", doc2.getCategory());

    DocumentSample doc3 = sampleStream.read();
    assertEquals("en", doc3.getCategory());

    DocumentSample doc4 = sampleStream.read();
    assertEquals("en", doc4.getCategory());

    assertNull(sampleStream.read());

    sampleStream.close();
  }
}
