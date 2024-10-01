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

import opennlp.tools.util.Span;

/**
 * A thread-safe version of SentenceDetectorME. Using it is completely transparent. You can use it in
 * a single-threaded context as well, it only incurs a minimal overhead.
 * <p>
 * Note, however, that this implementation uses a ThreadLocal. Although the implementation is
 * lightweight as the model is not duplicated, if you have many long-running threads, you may run
 * into memory issues. Be careful when you use this in a JEE application, for example.
 */
public class SentenceDetectorME_TS implements SentenceDetector {

  private SentenceModel model;

  private ThreadLocal<SentenceDetectorME> sentenceDetectorThreadLocal =
      new ThreadLocal<>();

  public SentenceDetectorME_TS(SentenceModel model) {
    super();
    this.model = model;
  }

  // If a thread-local version exists, return it. Otherwise create, then return.
  private SentenceDetectorME getSD() {
    SentenceDetectorME sd = sentenceDetectorThreadLocal.get();
    if (sd == null) {
      sd = new SentenceDetectorME(model);
      sentenceDetectorThreadLocal.set(sd);
    }
    return sd;
  }

  @Override
  public String[] sentDetect(String s) {
    return getSD().sentDetect(s);
  }

  @Override
  public Span[] sentPosDetect(String s) {
    return getSD().sentPosDetect(s);
  }

  public double[] getSentenceProbabilities() {
    return getSD().getSentenceProbabilities();
  }
}
