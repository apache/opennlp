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

package opennlp.tools.chunker;

import static junit.framework.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

import opennlp.tools.cmdline.chunker.ChunkerDetailedFMeasureListener;
import opennlp.tools.util.PlainTextByLineStream;

import org.junit.Test;

public class ChunkerDetailedFMeasureListenerTest {

  @Test
  public void testEvaluator() throws IOException {
    InputStream inPredicted = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/chunker/output.txt");
    InputStream inExpected = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/chunker/output.txt");
    
    InputStream detailedOutputStream = getClass().getClassLoader().getResourceAsStream(
    "opennlp/tools/chunker/detailedOutput.txt");

    String encoding = "UTF-8";

    try {
      DummyChunkSampleStream predictedSample = new DummyChunkSampleStream(
          new PlainTextByLineStream(
              new InputStreamReader(inPredicted, encoding)), true);

      DummyChunkSampleStream expectedSample = new DummyChunkSampleStream(
          new PlainTextByLineStream(new InputStreamReader(inExpected)), false);

      Chunker dummyChunker = new DummyChunker(predictedSample);

      ChunkerDetailedFMeasureListener listener = new ChunkerDetailedFMeasureListener();
      ChunkerEvaluator evaluator = new ChunkerEvaluator(dummyChunker, listener);

      evaluator.evaluate(expectedSample);

      StringBuilder expected = new StringBuilder();
      BufferedReader reader = new BufferedReader(new InputStreamReader(detailedOutputStream, encoding));
      String line = reader.readLine();
      
      while(line != null ) {
        expected.append(line);
        expected.append("\n");
        line = reader.readLine();
      }
      assertEquals(expected.toString().trim(), listener.createReport(Locale.ENGLISH).trim());
    } finally {
      inPredicted.close();
      inExpected.close();
      detailedOutputStream.close();
    }
  }
}
