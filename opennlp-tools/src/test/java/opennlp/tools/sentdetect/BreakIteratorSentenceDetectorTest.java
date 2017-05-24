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
 * Tests for the {@link BreakIteratorSentenceDetector} class.
 */
public class BreakIteratorSentenceDetectorTest {

  final String sentences = "Lorem ipsum dolor sit amet, consectetur adipiscing"
          + " elit, sed do eiusmod tempor incididunt ut labore et dolore magna"
          + " aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco"
          + " laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure"
          + " dolor in reprehenderit in voluptate velit esse cillum dolore eu"
          + " fugiat nulla pariatur. Excepteur sint occaecat cupidatat non"
          + " proident, sunt in culpa qui officia deserunt mollit anim id est"
          + " laborum.";

  @Test
  public void sentenceDetectTest() {

    BreakIteratorSentenceDetector sd = new BreakIteratorSentenceDetector();

    String[] results = sd.sentDetect(sentences);

    Assert.assertEquals(4, results.length);

    String[] tokens = new String[]
        {"Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
        + "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. ",
        "Ut enim ad minim veniam, quis nostrud exercitation ullamco "
        + "laboris nisi ut aliquip ex ea commodo consequat. ", 
        "Duis aute irure dolor in reprehenderit in voluptate velit esse "
        + "cillum dolore eu fugiat nulla pariatur. ",
        "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui "
        + "officia deserunt mollit anim id est laborum."};
    
    Assert.assertArrayEquals(tokens, results);
    
  }

}
