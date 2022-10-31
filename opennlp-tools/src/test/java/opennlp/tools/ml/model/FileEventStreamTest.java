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

package opennlp.tools.ml.model;

import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FileEventStreamTest {

  private static final String EVENTS =
      "other wc=ic w&c=he,ic n1wc=lc n1w&c=belongs,lc n2wc=lc\n" +
          "other wc=lc w&c=belongs,lc p1wc=ic p1w&c=he,ic n1wc=lc\n" +
          "other wc=lc w&c=to,lc p1wc=lc p1w&c=belongs,lc p2wc=ic\n" +
          "org-start wc=ic w&c=apache,ic p1wc=lc p1w&c=to,lc\n" +
          "org-cont wc=ic w&c=software,ic p1wc=ic p1w&c=apache,ic\n" +
          "org-cont wc=ic w&c=foundation,ic p1wc=ic p1w&c=software,ic\n" +
          "other wc=other w&c=.,other p1wc=ic\n";

  @Test
  void testSimpleReading() throws IOException {
    try (FileEventStream feStream = new FileEventStream(new StringReader(EVENTS))) {
      Assertions.assertEquals("other [wc=ic w&c=he,ic n1wc=lc n1w&c=belongs,lc n2wc=lc]",
          feStream.read().toString());
      Assertions.assertEquals("other [wc=lc w&c=belongs,lc p1wc=ic p1w&c=he,ic n1wc=lc]",
          feStream.read().toString());
      Assertions.assertEquals("other [wc=lc w&c=to,lc p1wc=lc p1w&c=belongs,lc p2wc=ic]",
          feStream.read().toString());
      Assertions.assertEquals("org-start [wc=ic w&c=apache,ic p1wc=lc p1w&c=to,lc]",
          feStream.read().toString());
      Assertions.assertEquals("org-cont [wc=ic w&c=software,ic p1wc=ic p1w&c=apache,ic]",
          feStream.read().toString());
      Assertions.assertEquals("org-cont [wc=ic w&c=foundation,ic p1wc=ic p1w&c=software,ic]",
          feStream.read().toString());
      Assertions.assertEquals("other [wc=other w&c=.,other p1wc=ic]",
          feStream.read().toString());
      Assertions.assertNull(feStream.read());
    }
  }

  @Test
  void testReset() throws IOException {
    try (FileEventStream feStream = new FileEventStream(new StringReader(EVENTS))) {
      feStream.reset();
      Assertions.fail("UnsupportedOperationException should be thrown");
    } catch (UnsupportedOperationException expected) {
    }
  }
}
