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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.cmdline.chunker.ChunkerDetailedFMeasureListener;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;

public class ChunkerDetailedFMeasureListenerTest {

  @Test
  public void testEvaluator() throws IOException {

    ResourceAsStreamFactory inPredicted = new ResourceAsStreamFactory(
        getClass(), "/opennlp/tools/chunker/output.txt");
    ResourceAsStreamFactory inExpected = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/chunker/output.txt");
    ResourceAsStreamFactory detailedOutputStream = new ResourceAsStreamFactory(
        getClass(), "/opennlp/tools/chunker/detailedOutput.txt");

    DummyChunkSampleStream predictedSample = new DummyChunkSampleStream(
        new PlainTextByLineStream(inPredicted, StandardCharsets.UTF_8), true);

    DummyChunkSampleStream expectedSample = new DummyChunkSampleStream(
        new PlainTextByLineStream(inExpected, StandardCharsets.UTF_8), false);

    Chunker dummyChunker = new DummyChunker(predictedSample);

    ChunkerDetailedFMeasureListener listener = new ChunkerDetailedFMeasureListener();
    ChunkerEvaluator evaluator = new ChunkerEvaluator(dummyChunker, listener);

    evaluator.evaluate(expectedSample);

    StringBuilder expected = new StringBuilder();
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(detailedOutputStream.createInputStream(), StandardCharsets.UTF_8));
    String line = reader.readLine();

    while (line != null) {
      expected.append(line);
      expected.append("\n");
      line = reader.readLine();
    }

    Assert.assertEquals(expected.toString().trim(), listener.createReport(Locale.ENGLISH).trim());
  }
}
