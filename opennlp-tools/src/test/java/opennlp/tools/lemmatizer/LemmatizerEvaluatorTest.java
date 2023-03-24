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

package opennlp.tools.lemmatizer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.lemmatizer.LemmaEvaluationErrorListener;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Tests for {@link LemmatizerEvaluator}.
 *
 * @see opennlp.tools.chunker.ChunkerEvaluator
 */
public class LemmatizerEvaluatorTest {

  private static final double DELTA = 1.0E-9d;

  /**
   * Checks the evaluator results against the results got using the conlleval,
   * available at <a href="https://www.cnts.ua.ac.be/conll2000/chunking/output.html">
   *   https://www.cnts.ua.ac.be/conll2000/chunking/output.html</a> but
   * containing lemmas instead of chunks.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  @Test
  void testEvaluator() throws IOException {
    String inPredicted = "opennlp/tools/lemmatizer/output.txt";
    String inExpected = "opennlp/tools/lemmatizer/output.txt";

    String encoding = StandardCharsets.UTF_8.name();

    DummyLemmaSampleStream predictedSample = new DummyLemmaSampleStream(
        new PlainTextByLineStream(
            new MockInputStreamFactory(new File(inPredicted)), encoding), true);

    DummyLemmaSampleStream expectedSample = new DummyLemmaSampleStream(
        new PlainTextByLineStream(
            new MockInputStreamFactory(new File(inExpected)), encoding), false);

    Lemmatizer dummyLemmatizer = new DummyLemmatizer(predictedSample);

    OutputStream stream = new ByteArrayOutputStream();
    LemmatizerEvaluationMonitor listener = new LemmaEvaluationErrorListener(stream);
    LemmatizerEvaluator evaluator = new LemmatizerEvaluator(dummyLemmatizer, listener);

    evaluator.evaluate(expectedSample);

    Assertions.assertEquals(0.9877049180327869, evaluator.getWordAccuracy(), DELTA);
    Assertions.assertNotSame(0, stream.toString().length());

  }

}
