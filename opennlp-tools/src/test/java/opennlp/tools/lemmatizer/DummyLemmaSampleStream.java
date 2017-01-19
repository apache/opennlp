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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * This dummy lemma sample stream reads a file containing forms, postags, gold
 * lemmas, and predicted lemmas. It can be used together with DummyLemmatizer
 * simulate a lemmatizer.
 */
public class DummyLemmaSampleStream
    extends FilterObjectStream<String, LemmaSample> {

  private boolean mIsPredicted;
  private int count = 0;

  // the predicted flag sets if the stream will contain the expected or the
  // predicted tags.
  public DummyLemmaSampleStream(ObjectStream<String> samples,
      boolean isPredicted) {
    super(samples);
    mIsPredicted = isPredicted;
  }

  public LemmaSample read() throws IOException {

    List<String> toks = new ArrayList<>();
    List<String> posTags = new ArrayList<>();
    List<String> goldLemmas = new ArrayList<>();
    List<String> predictedLemmas = new ArrayList<>();

    for (String line = samples.read(); line != null
        && !line.equals(""); line = samples.read()) {
      String[] parts = line.split("\t");
      if (parts.length != 4) {
        System.err.println("Skipping corrupt line " + count + ": " + line);
      } else {
        toks.add(parts[0]);
        posTags.add(parts[1]);
        goldLemmas.add(parts[2]);
        predictedLemmas.add(parts[3]);
      }
      count++;
    }

    if (toks.size() > 0) {
      if (mIsPredicted) {
        return new LemmaSample(toks.toArray(new String[toks.size()]),
            posTags.toArray(new String[posTags.size()]),
            predictedLemmas.toArray(new String[predictedLemmas.size()]));
      } else
        return new LemmaSample(toks.toArray(new String[toks.size()]),
            posTags.toArray(new String[posTags.size()]),
            goldLemmas.toArray(new String[goldLemmas.size()]));
    } else {
      return null;
    }

  }

}
