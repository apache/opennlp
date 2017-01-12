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

package opennlp.tools.sentdetect;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link NewlineSentenceDetector} class.
 */
public class NewlineSentenceDetectorTest {


  private static void testSentenceValues(String sentences) {
    NewlineSentenceDetector sd = new NewlineSentenceDetector();

    String results[] = sd.sentDetect(sentences);

    Assert.assertEquals(3, results.length);
    Assert.assertEquals("one.", results[0]);
    Assert.assertEquals("two.", results[1]);
    Assert.assertEquals("three.", results[2]);
  }

  @Test
  public void testNewlineCr() {
    testSentenceValues("one.\rtwo. \r\r three.\r");
  }

  @Test
  public void testNewlineLf() {
    testSentenceValues("one.\ntwo. \n\n three.\n");
  }

  @Test
  public void testNewlineCrLf() {
    testSentenceValues("one.\r\ntwo. \r\n\r\n three.\r\n");
  }
}
