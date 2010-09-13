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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;

/**
 * Tests for the {@link PlainTextByLineStream} class.
 */
public class PlainTextByLineStreamTest {

  @Test
  public void testLineSegmentation() throws IOException {
    StringBuilder testString = new StringBuilder();
    testString.append("line1");
    testString.append('\n');
    testString.append("line2");
    testString.append('\n');
    testString.append("line3");
    testString.append("\r\n");
    testString.append("line4");
    testString.append('\n');
    
    ObjectStream<String> stream = 
        new PlainTextByLineStream(new StringReader(testString.toString()));
    
    assertEquals("line1", stream.read());
    assertEquals("line2", stream.read());
    assertEquals("line3", stream.read());
    assertEquals("line4", stream.read());
  }
}
