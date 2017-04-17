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

import org.junit.Assert;
import org.junit.Test;

public class RealValueFileEventStreamTest {

  private static final String EVENTS =
      "other wc=ic=1 w&c=he,ic=2 n1wc=lc=3 n1w&c=belongs,lc=4 n2wc=lc=5\n" +
      "other wc=lc=1 w&c=belongs,lc=2 p1wc=ic=3 p1w&c=he,ic=4 n1wc=lc=5\n" +
      "other wc=lc=1 w&c=to,lc=2 p1wc=lc=3 p1w&c=belongs,lc=4 p2wc=ic=5\n" +
      "org-start wc=ic=1 w&c=apache,ic=2 p1wc=lc=3 p1w&c=to,lc=4\n" +
      "org-cont wc=ic=1 w&c=software,ic=2 p1wc=ic=3 p1w&c=apache,ic=4\n" +
      "org-cont wc=ic=1 w&c=foundation,ic=2 p1wc=ic=3 p1w&c=software,ic=4\n" +
      "other wc=other=1 w&c=.,other=2 p1wc=ic=3\n";

  @Test
  public void testSimpleReading() throws IOException {
    FileEventStream feStream = new FileEventStream(new StringReader(EVENTS));

    Assert.assertEquals("other [wc=ic=1 w&c=he,ic=2 n1wc=lc=3 n1w&c=belongs,lc=4 n2wc=lc=5]",
            feStream.read().toString());
    Assert.assertEquals("other [wc=lc=1 w&c=belongs,lc=2 p1wc=ic=3 p1w&c=he,ic=4 n1wc=lc=5]",
            feStream.read().toString());
    Assert.assertEquals("other [wc=lc=1 w&c=to,lc=2 p1wc=lc=3 p1w&c=belongs,lc=4 p2wc=ic=5]",
            feStream.read().toString());
    Assert.assertEquals("org-start [wc=ic=1 w&c=apache,ic=2 p1wc=lc=3 p1w&c=to,lc=4]",
            feStream.read().toString());
    Assert.assertEquals("org-cont [wc=ic=1 w&c=software,ic=2 p1wc=ic=3 p1w&c=apache,ic=4]",
            feStream.read().toString());
    Assert.assertEquals("org-cont [wc=ic=1 w&c=foundation,ic=2 p1wc=ic=3 p1w&c=software,ic=4]",
            feStream.read().toString());
    Assert.assertEquals("other [wc=other=1 w&c=.,other=2 p1wc=ic=3]",
            feStream.read().toString());
    Assert.assertNull(feStream.read());
  }

  @Test
  public void testReset() throws IOException {
    FileEventStream feStream = new FileEventStream(new StringReader(EVENTS));

    try {
      feStream.reset();
      Assert.fail("UnsupportedOperationException should be thrown");
    }
    catch (UnsupportedOperationException expected) {
    }
  }
}
