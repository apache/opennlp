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

package opennlp.tools.eval;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.ThreadSafePOSTaggerME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.ThreadSafeSentenceDetectorME;
import opennlp.tools.tokenize.ThreadSafeTokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

/**
 * Test the reentrant tools implementations are really thread safe by running concurrently.
 * Replace the thread-safe versions with the non-safe versions to see this test case fail.
 *
 * @see ThreadSafe
 */
public class MultiThreadedToolsEval extends AbstractEvalTest {

  @Test
  public void runMEToolsMultiThreaded() throws IOException, InterruptedException {

    File dataDir = getOpennlpDataDir();
    File sModelFile = new File(dataDir, "models-sf/en-sent.bin");
    File tModelFile = new File(dataDir, "models-sf/en-token.bin");
    File pModelFile = new File(dataDir, "models-sf/en-pos-maxent.bin");
    SentenceModel sModel = new SentenceModel(sModelFile);
    TokenizerModel tModel = new TokenizerModel(tModelFile);
    POSModel pModel = new POSModel(pModelFile);

    try (ThreadSafeSentenceDetectorME sentencer = new ThreadSafeSentenceDetectorME(sModel);
         ThreadSafeTokenizerME tokenizer = new ThreadSafeTokenizerME(tModel);
         ThreadSafePOSTaggerME tagger = new ThreadSafePOSTaggerME(pModel)) {

      final String text = "All human beings are born free and equal in dignity and rights. They " +
              "are endowed with reason and conscience and should act towards one another in a " +
              "spirit of brotherhood.";

      // Run numThreads threads, each processing the sample text numRunsPerThread times.
      final int numThreads = 8;
      final int numRunsPerThread = 1000;
      Thread[] threads = new Thread[numThreads];

      for (int i = 0; i < 8; i++) {
        threads[i] = new Thread(() -> {
          for (int j = 0; j < numRunsPerThread; j++) {
            Span[] sentences = sentencer.sentPosDetect(text);
            for (Span span : sentences) {
              String sentence = text.substring(span.getStart(), span.getEnd());
              Span[] tokens = tokenizer.tokenizePos(sentence);
              String[] tokenStrings = new String[tokens.length];
              for (int k = 0; k < tokens.length; k++) {
                tokenStrings[k] = sentence.substring(tokens[k].getStart(),
                        tokens[k].getEnd());
              }
              String[] tags = tagger.tag(tokenStrings);
            }
          }
        });
        threads[i].start();
      }
      for (Thread t : threads) {
        t.join();
      }
    }
  }

}
