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

package opennlp.tools.cmdline.namefind;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * Counts tokens, sentences and names by type
 */
public class NameSampleCountersStream
    extends FilterObjectStream<NameSample, NameSample> {

  private int sentenceCount;
  private int tokenCount;

  private Map<String, Integer> nameCounters = new HashMap<>();

  protected NameSampleCountersStream(ObjectStream<NameSample> samples) {
    super(samples);
  }

  @Override
  public NameSample read() throws IOException {

    NameSample sample = samples.read();

    if (sample != null) {
      sentenceCount++;
      tokenCount += sample.getSentence().length;

      for (Span nameSpan : sample.getNames()) {
        Integer nameCounter = nameCounters.get(nameSpan.getType());

        if (nameCounter == null) {
          nameCounter = 0;
        }

        nameCounters.put(nameSpan.getType(), nameCounter + 1);
      }
    }

    return sample;
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    super.reset();

    sentenceCount = 0;
    tokenCount = 0;
    nameCounters = new HashMap<>();
  }

  public int getSentenceCount() {
    return sentenceCount;
  }

  public int getTokenCount() {
    return tokenCount;
  }

  public Map<String, Integer> getNameCounters() {
    return Collections.unmodifiableMap(nameCounters);
  }

  public void printSummary() {
    System.out.println("Training data summary:");
    System.out.println("#Sentences: " + getSentenceCount());
    System.out.println("#Tokens: " + getTokenCount());

    int totalNames = 0;
    for (Map.Entry<String, Integer> counter : getNameCounters().entrySet()) {
      System.out.println("#" + counter.getKey() + " entities: " + counter.getValue());
      totalNames += counter.getValue();
    }
  }
}
