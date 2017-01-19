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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkSampleStream;
import opennlp.tools.chunker.ChunkerEvaluator;
import opennlp.tools.chunker.ChunkerFactory;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

/**
 * Evaluates the chunker against the English CONLL2000 corpus.
 * <p>
 * Download the train and eval gz files from the CONLL2000 shared task
 * <a href="http://www.cnts.ua.ac.be/conll2000/chunking/"> site </a>
 * and decompress them into this directory: $OPENNLP_DATA_DIR/conll00.
 */
public class Conll00ChunkerEval {

  private static ChunkerModel train(File trainFile, TrainingParameters params)
      throws IOException {

    ObjectStream<ChunkSample> samples = new ChunkSampleStream(
        new PlainTextByLineStream(
            new MarkableFileInputStreamFactory(trainFile), "UTF-8"));

    return ChunkerME.train("en", samples, params, new ChunkerFactory());
  }

  private static void eval(ChunkerModel model, File testData,
                           double expectedFMeasure) throws IOException {

    ObjectStream<ChunkSample> samples = new ChunkSampleStream(
        new PlainTextByLineStream(new MarkableFileInputStreamFactory(testData), "UTF-8"));

    ChunkerEvaluator evaluator = new ChunkerEvaluator(new ChunkerME(model));
    evaluator.evaluate(samples);
    Assert.assertEquals(expectedFMeasure,
        evaluator.getFMeasure().getFMeasure(), 0.0001);
  }

  @Test
  public void evalEnglishPerceptron() throws IOException {
    ChunkerModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll00/train.txt"), EvalUtil.createPerceptronParams());

    eval(maxentModel,
        new File(EvalUtil.getOpennlpDataDir(), "conll00/test.txt"),
        0.9295018353434714d);
  }

  @Test
  public void evalEnglishMaxentGis() throws IOException {
    ChunkerModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll00/train.txt"), ModelUtil.createDefaultTrainingParameters());

    eval(maxentModel,
        new File(EvalUtil.getOpennlpDataDir(), "conll00/test.txt"),
        0.9239687473746113d);
  }

  // Note: Don't try to run this on your MacBook
  @Test
  public void evalEnglishMaxentQn() throws IOException {
    TrainingParameters params = EvalUtil.createMaxentQnParams();
    params.put("Threads", "4");
    ChunkerModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll00/train.txt"), params);

    eval(maxentModel,
        new File(EvalUtil.getOpennlpDataDir(), "conll00/test.txt"),
        0.9302599230947028d);
  }
}
