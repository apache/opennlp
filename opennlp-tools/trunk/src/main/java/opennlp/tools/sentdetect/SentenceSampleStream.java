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

import java.io.IOException;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.Span;

/**
 * This class is a stream filter which reads a sentence by line samples from
 * a <code>Reader</code> and converts them into {@link SentenceSample} objects.
 */
public class SentenceSampleStream implements ObjectStream<SentenceSample> {

  private ObjectStream<String> sentences;

  public SentenceSampleStream(ObjectStream<String> sentences) throws IOException {

    if (sentences == null)
      throw new IllegalArgumentException("sentences must not be null!");

    this.sentences = sentences;
  }

  public SentenceSample read() throws ObjectStreamException {
    String sentence = sentences.read();
    if (sentence != null) {
      return new SentenceSample(sentence, new Span(0, sentence.length()));
    }
    else {
      return null;
    }
  }
  
  public void reset() throws ObjectStreamException {
    sentences.reset();
  }
}
