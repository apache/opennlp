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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * This is the test class for {@link LemmatizerME}.
 * <p>
 * A proper testing and evaluation of the name finder is only possible with a
 * large corpus which contains a huge amount of test sentences.
 * <p>
 * The scope of this test is to make sure that the name finder code can be
 * executed. This test can not detect mistakes which lead to incorrect feature
 * generation or other mistakes which decrease the tagging performance of the
 * name finder.
 * <p>
 * In this test the {@link LemmatizerME} is trained with a small amount of
 * training sentences and then the computed model is used to predict sentences
 * from the training sentences.
 */

public class LemmatizerMETest {

  private LemmatizerME lemmatizer;

  private static final String[] tokens = {"Rockwell", "said", "the", "agreement", "calls", "for",
      "it", "to", "supply", "200", "additional", "so-called", "shipsets", "for",
      "the", "planes", "."};

  private static final String[] postags = {"NNP", "VBD", "DT", "NN", "VBZ", "IN", "PRP", "TO", "VB",
      "CD", "JJ", "JJ", "NNS", "IN", "DT", "NNS", "."};

  private static final String[] expect = {"rockwell", "say", "the", "agreement", "call", "for",
      "it", "to", "supply", "200", "additional", "so-called", "shipset", "for",
      "the", "plane", "."};

  @BeforeEach
  void startup() throws IOException {
    // train the lemmatizer

    ObjectStream<LemmaSample> sampleStream = new LemmaSampleStream(
        new PlainTextByLineStream(new MockInputStreamFactory(
            new File("opennlp/tools/lemmatizer/trial.old.tsv")), StandardCharsets.UTF_8));

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 100);
    params.put(TrainingParameters.CUTOFF_PARAM, 5);

    LemmatizerModel lemmatizerModel = LemmatizerME.train("eng", sampleStream,
        params, new LemmatizerFactory());

    this.lemmatizer = new LemmatizerME(lemmatizerModel);
  }

  @Test
  void testLemmasAsArray() {

    String[] lemmas = lemmatizer.lemmatize(tokens, postags);

    Assertions.assertArrayEquals(expect, lemmas);
  }

  @Test
  void testInsufficientData() {

    Assertions.assertThrows(InsufficientTrainingDataException.class, () -> {

      ObjectStream<LemmaSample> sampleStream = new LemmaSampleStream(
          new PlainTextByLineStream(new MockInputStreamFactory(
              new File("opennlp/tools/lemmatizer/trial.old-insufficient.tsv")), StandardCharsets.UTF_8));

      TrainingParameters params = new TrainingParameters();
      params.put(TrainingParameters.ITERATIONS_PARAM, 100);
      params.put(TrainingParameters.CUTOFF_PARAM, 5);

      LemmatizerME.train("eng", sampleStream, params, new LemmatizerFactory());

    });


  }

}
