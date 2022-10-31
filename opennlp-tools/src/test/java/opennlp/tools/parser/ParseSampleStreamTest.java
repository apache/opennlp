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

package opennlp.tools.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class ParseSampleStreamTest {

  private static ObjectStream<Parse> createParseSampleStream() throws IOException {

    InputStreamFactory in = new ResourceAsStreamFactory(
        ParseSampleStreamTest.class, "/opennlp/tools/parser/test.parse");

    return new ParseSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  @Test
  public void testReadTestStream() throws IOException {
    ObjectStream<Parse> parseStream = createParseSampleStream();
    Assert.assertNotNull(parseStream.read());
    Assert.assertNotNull(parseStream.read());
    Assert.assertNotNull(parseStream.read());
    Assert.assertNotNull(parseStream.read());
    Assert.assertNull(parseStream.read());
  }
}
