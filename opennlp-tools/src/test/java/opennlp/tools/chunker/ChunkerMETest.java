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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

/**
 * This is the test class for {@link NameFinderME}.
 * <p>
 * A proper testing and evaluation of the name finder is only possible with a
 * large corpus which contains a huge amount of test sentences.
 * <p>
 * The scope of this test is to make sure that the name finder code can be
 * executed. This test can not detect mistakes which lead to incorrect feature
 * generation or other mistakes which decrease the tagging performance of the
 * name finder.
 * <p>
 * In this test the {@link NameFinderME} is trained with a small amount of
 * training sentences and then the computed model is used to predict sentences
 * from the training sentences.
 */

public class ChunkerMETest {

  private Chunker chunker;

  private static final String[] toks1 = {"Rockwell", "said", "the", "agreement", "calls", "for",
      "it", "to", "supply", "200", "additional", "so-called", "shipsets",
      "for", "the", "planes", "."};

  private static final String[] tags1 = {"NNP", "VBD", "DT", "NN", "VBZ", "IN", "PRP", "TO", "VB",
      "CD", "JJ", "JJ", "NNS", "IN", "DT", "NNS", "."};

  private static final String[] expect1 = {"B-NP", "B-VP", "B-NP", "I-NP", "B-VP", "B-SBAR",
      "B-NP", "B-VP", "I-VP", "B-NP", "I-NP", "I-NP", "I-NP", "B-PP", "B-NP",
      "I-NP", "O"};

  @Test
  void downloadNonExistentModel() {

    Assertions.assertThrows(IOException.class, () -> {

      ChunkerME chunker = new ChunkerME("en");

      String[] preds = chunker.chunk(toks1, tags1);

      Assertions.assertArrayEquals(expect1, preds);
    });


  }

  @BeforeEach
  void startup() throws IOException {
    // train the chunker

    ResourceAsStreamFactory in = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/chunker/test.txt");

    ObjectStream<ChunkSample> sampleStream = new ChunkSampleStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 70);
    params.put(TrainingParameters.CUTOFF_PARAM, 1);

    ChunkerModel chunkerModel = ChunkerME.train("eng", sampleStream, params, new ChunkerFactory());

    this.chunker = new ChunkerME(chunkerModel);
  }

  @Test
  void testChunkAsArray() {

    String[] preds = chunker.chunk(toks1, tags1);

    Assertions.assertArrayEquals(expect1, preds);
  }

  @Test
  void testChunkAsSpan() {
    Span[] preds = chunker.chunkAsSpans(toks1, tags1);

    Assertions.assertEquals(10, preds.length);
    Assertions.assertEquals(new Span(0, 1, "NP"), preds[0]);
    Assertions.assertEquals(new Span(1, 2, "VP"), preds[1]);
    Assertions.assertEquals(new Span(2, 4, "NP"), preds[2]);
    Assertions.assertEquals(new Span(4, 5, "VP"), preds[3]);
    Assertions.assertEquals(new Span(5, 6, "SBAR"), preds[4]);
    Assertions.assertEquals(new Span(6, 7, "NP"), preds[5]);
    Assertions.assertEquals(new Span(7, 9, "VP"), preds[6]);
    Assertions.assertEquals(new Span(9, 13, "NP"), preds[7]);
    Assertions.assertEquals(new Span(13, 14, "PP"), preds[8]);
    Assertions.assertEquals(new Span(14, 16, "NP"), preds[9]);

  }

  @Test
  void testTokenProbArray() {
    Sequence[] preds = chunker.topKSequences(toks1, tags1);

    Assertions.assertTrue(preds.length > 0);
    Assertions.assertEquals(expect1.length, preds[0].getProbs().length);
    Assertions.assertEquals(Arrays.asList(expect1), preds[0].getOutcomes());
    Assertions.assertNotSame(Arrays.asList(expect1), preds[1].getOutcomes());
  }

  @Test
  void testTokenProbMinScore() {
    Sequence[] preds = chunker.topKSequences(toks1, tags1, -5.55);

    Assertions.assertEquals(4, preds.length);
    Assertions.assertEquals(expect1.length, preds[0].getProbs().length);
    Assertions.assertEquals(Arrays.asList(expect1), preds[0].getOutcomes());
    Assertions.assertNotSame(Arrays.asList(expect1), preds[1].getOutcomes());
  }

  @Test
  void testInsufficientData() {

    Assertions.assertThrows(InsufficientTrainingDataException.class, () -> {

      ResourceAsStreamFactory in = new ResourceAsStreamFactory(getClass(),
          "/opennlp/tools/chunker/test-insufficient.txt");

      ObjectStream<ChunkSample> sampleStream = new ChunkSampleStream(
          new PlainTextByLineStream(in, StandardCharsets.UTF_8));

      TrainingParameters params = new TrainingParameters();
      params.put(TrainingParameters.ITERATIONS_PARAM, 70);
      params.put(TrainingParameters.CUTOFF_PARAM, 1);

      ChunkerME.train("eng", sampleStream, params, new ChunkerFactory());

    });


  }

}
