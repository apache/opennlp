/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import java.io.InputStream;
import java.io.InputStreamReader;

import junit.framework.TestCase;
import opennlp.maxent.PlainTextByLineDataStream;

/**
 * Tests for the {@link SentenceDetectorME} class.
 */
public class SentenceDetectorMETest extends TestCase {

  public void testSentenceDetector() {

    InputStream in = getClass().getResourceAsStream(
        "/opennlp/tools/sentdetect/Sentences.txt");

    SentenceModel sentdetectModel = SentenceDetectorME.train(
        "en", new SentenceSampleStream(new PlainTextByLineDataStream(
        new InputStreamReader(in))), true, null);

    SentenceDetector sentDetect = new SentenceDetectorME(sentdetectModel);

    String sampleSentences = "First test meal. Second test sentence.";

    sentDetect.sentPosDetect(sampleSentences);

    // TODO: check result
  }
}