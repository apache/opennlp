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

package opennlp.tools.util;

import org.junit.Test;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

/**
 * Tests for the {@link PlainTextByLineStream} class.
 */
public class PlainTextByLineStreamTest {

  @Test
  public void testLineSegmentation() throws IOException {
    String testString = "line1" +
        '\n' +
        "line2" +
        '\n' +
        "line3" +
        "\r\n" +
        "line4" +
        '\n';

    ObjectStream<String> stream =
        new PlainTextByLineStream(new MockInputStreamFactory(testString), UTF_8);

    assertEquals("line1", stream.read());
    assertEquals("line2", stream.read());
    assertEquals("line3", stream.read());
    assertEquals("line4", stream.read());
    
    stream.close();
  }
}
