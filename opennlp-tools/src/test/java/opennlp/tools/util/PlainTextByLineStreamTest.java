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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link PlainTextByLineStream} class.
 */
public class PlainTextByLineStreamTest {

  static final String testString = "line1" +
          '\n' +
          "line2" +
          '\n' +
          "line3" +
          "\r\n" +
          "line4" +
          '\n';

  @Test
  public void testLineSegmentation() throws IOException {
    ObjectStream<String> stream =
            new PlainTextByLineStream(new MockInputStreamFactory(testString), StandardCharsets.UTF_8);

    Assert.assertEquals("line1", stream.read());
    Assert.assertEquals("line2", stream.read());
    Assert.assertEquals("line3", stream.read());
    Assert.assertEquals("line4", stream.read());
    Assert.assertNull(stream.read());

    stream.close();
  }

  @Test
  public void testReset() throws IOException {
    ObjectStream<String> stream =
            new PlainTextByLineStream(new MockInputStreamFactory(testString), StandardCharsets.UTF_8);

    Assert.assertEquals("line1", stream.read());
    Assert.assertEquals("line2", stream.read());
    Assert.assertEquals("line3", stream.read());
    stream.reset();

    Assert.assertEquals("line1", stream.read());
    Assert.assertEquals("line2", stream.read());
    Assert.assertEquals("line3", stream.read());
    Assert.assertEquals("line4", stream.read());
    Assert.assertNull(stream.read());

    stream.close();
  }
}
