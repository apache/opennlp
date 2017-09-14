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

import opennlp.tools.langdetect.LanguageSample;

/**
 * Tests for the {@link LeipzigLanguageSampleStream} class.
 */
public class LeipzigLanguageSampleStreamTest {

  @Test
  public void testReadSentenceFiles() {
    String testDataPath = LeipzigLanguageSampleStreamTest.class
            .getClassLoader().getResource("opennlp/tools/formats/leipzig/samples").getPath();
    int samplesPerLanguage = 2;
    int sentencesPerSample = 1;
    try {

      LeipzigLanguageSampleStream stream = new LeipzigLanguageSampleStream(new File(testDataPath),
              sentencesPerSample, samplesPerLanguage);
      int count = 0;
      LanguageSample sample = null;
      while ((sample = stream.read()) != null) {
        count++;
        System.out.println(sample.getContext());
      }
      Assert.assertEquals(4, count);

    } catch (IOException e) {
      Assert.fail();
    }
  }

}
