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

package opennlp.tools.formats.leipzig;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.InvalidFormatException;

/**
 * Tests for the {@link LeipzigLanguageSampleStream} class.
 */
public class LeipzigLanguageSampleStreamTest {

  private static String testDataPath = LeipzigLanguageSampleStreamTest.class
          .getClassLoader().getResource("opennlp/tools/formats/leipzig/samples").getPath();

  @Test
  public void testReadSentenceFiles() {

    int samplesPerLanguage = 2;
    int sentencesPerSample = 1;
    try {
      LeipzigLanguageSampleStream stream = new LeipzigLanguageSampleStream(new File(testDataPath),
              sentencesPerSample, samplesPerLanguage);
      int count = 0;
      while (stream.read() != null)
        count++;

      Assert.assertEquals(4, count);

    } catch (IOException e) {
      Assert.fail();
    }
  }

  @Test(expected = InvalidFormatException.class)
  public void testNotEnoughSentences() throws IOException {
    int samplesPerLanguage = 2;
    int sentencesPerSample = 2;

    LeipzigLanguageSampleStream stream =
            new LeipzigLanguageSampleStream(new File(testDataPath),
              sentencesPerSample, samplesPerLanguage);
    while (stream.read() != null);

  }

}
