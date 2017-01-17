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

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME_TS;
import opennlp.tools.sentdetect.SentenceDetectorME_TS;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME_TS;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * Test the the reentrant tools implementations are really thread safe by running the concurrently.
 * Replace the thread-safe versions with the non-safe versions to see this test case fail.
 */
public class MultiThreadedToolsEval {

  @Test
  public void runMEToolsMultiThreaded() throws IOException, InterruptedException {

    File sModelFile = new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-sent.bin");
    SentenceModel sModel = new SentenceModel(sModelFile);
    SentenceDetectorME_TS sentencer = new SentenceDetectorME_TS(sModel);

    File tModelFile = new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-token.bin");
    TokenizerModel tModel = new TokenizerModel(tModelFile);
    TokenizerME_TS tokenizer = new TokenizerME_TS(tModel);

    File pModelFile = new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-pos-maxent.bin");
    POSModel pModel = new POSModel(pModelFile);
    POSTaggerME_TS tagger = new POSTaggerME_TS(pModel);

    final String text = "All human beings are born free and equal in dignity and rights. They " +
        "are endowed with reason and conscience and should act towards one another in a " +
        "spirit of brotherhood.";

    // Run numThreads threads, each processing the sample text numRunsPerThread times.
    final int numThreads = 8;
    final int numRunsPerThread = 1000;
    Thread[] threads = new Thread[numThreads];

    for (int i = 0; i < 8; i++) {
      threads[i] = new Thread(new Runnable() {
        @Override
        public void run() {
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
        }
      });
      threads[i].start();
    }
    for (Thread t : threads) {
      t.join();
    }

  }

}
