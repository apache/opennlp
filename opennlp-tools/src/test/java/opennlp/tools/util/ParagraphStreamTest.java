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

import org.junit.Assert;
import org.junit.Test;

public class ParagraphStreamTest {

  @Test
  public void testSimpleReading() throws IOException {
    String line1 = "1";
    String line2 = "2";
    String line3 = "";
    String line4 = "4";
    String line5 = "5";
    String line6 = "";

    ParagraphStream paraStream = new ParagraphStream(
        ObjectStreamUtils.createObjectStream(line1, line2, line3, line4, line5));

    Assert.assertEquals("1\n2\n", paraStream.read());
    Assert.assertEquals("4\n5\n", paraStream.read());

    paraStream = new ParagraphStream(
        ObjectStreamUtils.createObjectStream(line1, line2, line3, line4, line5, line6));

    Assert.assertEquals("1\n2\n", paraStream.read());
    Assert.assertEquals("4\n5\n", paraStream.read());
  }
}
