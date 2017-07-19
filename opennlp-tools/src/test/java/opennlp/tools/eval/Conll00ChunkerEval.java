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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import opennlp.tools.HighMemoryUsage;
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
public class Conll00ChunkerEval extends AbstractEvalTest {

  private static File TEST_DATA_FILE; 
  private static File TRAIN_DATA_FILE;
  
  private static ChunkerModel train(File trainFile, TrainingParameters params)
      throws IOException {

    ObjectStream<ChunkSample> samples = new ChunkSampleStream(
        new PlainTextByLineStream(
            new MarkableFileInputStreamFactory(trainFile), StandardCharsets.UTF_8));

    return ChunkerME.train("eng", samples, params, new ChunkerFactory());
  }

  private static void eval(ChunkerModel model, File testData,
                           double expectedFMeasure) throws IOException {

    ObjectStream<ChunkSample> samples = new ChunkSampleStream(
        new PlainTextByLineStream(new MarkableFileInputStreamFactory(testData), StandardCharsets.UTF_8));

    ChunkerEvaluator evaluator = new ChunkerEvaluator(new ChunkerME(model));
    evaluator.evaluate(samples);
    Assert.assertEquals(expectedFMeasure,
        evaluator.getFMeasure().getFMeasure(), 0.0001);
  }
  
  @BeforeClass
  public static void verifyTrainingData() throws Exception {
    
    TEST_DATA_FILE = new File(getOpennlpDataDir(), "conll00/test.txt");
    TRAIN_DATA_FILE = new File(getOpennlpDataDir(), "conll00/train.txt");

    verifyTrainingData(new ChunkSampleStream(
            new PlainTextByLineStream(new MarkableFileInputStreamFactory(TEST_DATA_FILE),
                    StandardCharsets.UTF_8)),
        new BigInteger("84610235226433393380477662908529306002"));

    verifyTrainingData(new ChunkSampleStream(
            new PlainTextByLineStream(new MarkableFileInputStreamFactory(TEST_DATA_FILE),
                    StandardCharsets.UTF_8)),
        new BigInteger("84610235226433393380477662908529306002"));    

  }

  @Test
  public void evalEnglishPerceptron() throws IOException {
    ChunkerModel maxentModel = train(TRAIN_DATA_FILE, createPerceptronParams());

    eval(maxentModel, TEST_DATA_FILE, 0.9295018353434714d);
  }

  @Test
  public void evalEnglishMaxentGis() throws IOException {
    ChunkerModel maxentModel = train(TRAIN_DATA_FILE, ModelUtil.createDefaultTrainingParameters());

    eval(maxentModel, TEST_DATA_FILE, 0.9239687473746113d);
  }

  // Note: Don't try to run this on your MacBook
  @Test
  @Category(HighMemoryUsage.class)
  public void evalEnglishMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();
    params.put("Threads", 4);
    ChunkerModel maxentModel = train(TRAIN_DATA_FILE, params);

    eval(maxentModel, TEST_DATA_FILE, 0.9302599230947028d);
  }
}
