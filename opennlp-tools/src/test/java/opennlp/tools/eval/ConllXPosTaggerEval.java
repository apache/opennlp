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
import opennlp.tools.formats.ConllXPOSSampleStream;
import opennlp.tools.postag.POSEvaluator;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

/**
 * Evaluates the POS Tagger on the CONLL-X data. The CONLL-X data includes training and evaluation data for
 * Danish, Dutch, Portuguese and Swedish.
 * <p>
 * The following files are needed in the data directory to run this test:
 * conllx/data/danish/ddt/train/danish_ddt_train.conll<br>
 * conllx/data/danish/ddt/test/danish_ddt_test.conll<br>
 * conllx/data/dutch/alpino/train/dutch_alpino_train.conll<br>
 * conllx/data/dutch/alpino/test/dutch_alpino_test.conll<br>
 * conllx/data/portuguese/bosque/treebank/portuguese_bosque_train.conll<br>
 * conllx/data/portuguese/bosque/test/portuguese_bosque_test.conll<br>
 * conllx/data/swedish/talbanken05/train/swedish_talbanken05_train.conll<br>
 * conllx/data/swedish/talbanken05/test/swedish_talbanken05_test.conll<br>
 * <p>
 * The structure follows the structure of the CONLL-X data distribution. There is
 * one package for each language, and an extra package containing the tests for all
 * languages.
 */
public class ConllXPosTaggerEval extends AbstractEvalTest {

  private POSModel train(File trainFile, String lang,
                                TrainingParameters params) throws IOException {

    ObjectStream<POSSample> samples =
        new ConllXPOSSampleStream(new MarkableFileInputStreamFactory(trainFile), StandardCharsets.UTF_8);

    return POSTaggerME.train(lang, samples, params, new POSTaggerFactory());
  }

  private void eval(POSModel model, File testData,
                           double expectedAccuracy) throws IOException {

    ObjectStream<POSSample> samples = new ConllXPOSSampleStream(
        new MarkableFileInputStreamFactory(testData), StandardCharsets.UTF_8);

    POSEvaluator evaluator = new POSEvaluator(new POSTaggerME(model));
    evaluator.evaluate(samples);

    Assert.assertEquals(expectedAccuracy, evaluator.getWordAccuracy(), 0.0001);
  }

  @BeforeClass
  public static void verifyTrainingData() throws Exception {
    
    verifyTrainingData(new ConllXPOSSampleStream(
        new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
          "conllx/data/danish/ddt/train/danish_ddt_train.conll")), StandardCharsets.UTF_8), 
        new BigInteger("30795670444498617202001550516753630016"));
    
    verifyTrainingData(new ConllXPOSSampleStream(
        new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
          "conllx/data/danish/ddt/test/danish_ddt_test.conll")), StandardCharsets.UTF_8), 
            new BigInteger("314104267846430512372780024568104131337"));
    
    verifyTrainingData(new ConllXPOSSampleStream(
        new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
          "conllx/data/dutch/alpino/train/dutch_alpino_train.conll")), StandardCharsets.UTF_8), 
            new BigInteger("109328245573060521952850454797286933887"));

    verifyTrainingData(new ConllXPOSSampleStream(
        new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
          "conllx/data/dutch/alpino/test/dutch_alpino_test.conll")), StandardCharsets.UTF_8), 
            new BigInteger("132343141132816640849897155456916243039"));

    verifyTrainingData(new ConllXPOSSampleStream(
        new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
          "conllx/data/portuguese/bosque/treebank/portuguese_bosque_train.conll")), StandardCharsets.UTF_8), 
            new BigInteger("9504382474772307801979515927230835901"));

    verifyTrainingData(new ConllXPOSSampleStream(
        new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
          "conllx/data/swedish/talbanken05/train/swedish_talbanken05_train.conll")), StandardCharsets.UTF_8), 
            new BigInteger("175256039869578311901318972681191182910"));

    verifyTrainingData(new ConllXPOSSampleStream(
        new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
          "conllx/data/swedish/talbanken05/test/swedish_talbanken05_test.conll")), StandardCharsets.UTF_8), 
            new BigInteger("128378790384268106811747599235147991544"));
    
  }

  @Test
  public void evalDanishMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    POSModel maxentModel = train(new File(getOpennlpDataDir(),
        "conllx/data/danish/ddt/train/danish_ddt_train.conll"), "dan", params);

    eval(maxentModel, new File(getOpennlpDataDir(),
        "conllx/data/danish/ddt/test/danish_ddt_test.conll"), 0.9504442925495558d);
  }

  @Test
  public void evalDanishMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    POSModel maxentModel = train(new File(getOpennlpDataDir(),
        "conllx/data/danish/ddt/train/danish_ddt_train.conll"), "dan", params);

    eval(maxentModel, new File(getOpennlpDataDir(),
        "conllx/data/danish/ddt/test/danish_ddt_test.conll"), 0.9564251537935748d);
  }

  @Test
  public void evalDutchMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    POSModel maxentModel = train(new File(getOpennlpDataDir(),
        "conllx/data/dutch/alpino/train/dutch_alpino_train.conll"), "nld", params);

    eval(maxentModel, new File(getOpennlpDataDir(),
        "conllx/data/dutch/alpino/test/dutch_alpino_test.conll"), 0.9213965980304387d);
  }

  @Test
  @Category(HighMemoryUsage.class)
  public void evalDutchMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    POSModel maxentModel = train(new File(getOpennlpDataDir(),
        "conllx/data/dutch/alpino/train/dutch_alpino_train.conll"), "nld", params);

    eval(maxentModel, new File(getOpennlpDataDir(),
        "conllx/data/dutch/alpino/test/dutch_alpino_test.conll"), 0.9282005371530886d);
  }

  @Test
  public void evalPortugueseMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    POSModel maxentModel = train(new File(getOpennlpDataDir(),
        "conllx/data/portuguese/bosque/treebank/portuguese_bosque_train.conll"), "por", params);

    eval(maxentModel, new File(getOpennlpDataDir(),
        "conllx/data/portuguese/bosque/test/portuguese_bosque_test.conll"), 0.9671041418101244d);
  }

  @Test
  public void evalPortugueseMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    POSModel maxentModel = train(new File(getOpennlpDataDir(),
        "conllx/data/portuguese/bosque/treebank/portuguese_bosque_train.conll"), "por", params);

    eval(maxentModel, new File(getOpennlpDataDir(),
        "conllx/data/portuguese/bosque/test/portuguese_bosque_test.conll"), 0.9662519175046872d);
  }

  @Test
  public void evalSwedishMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    POSModel maxentModel = train(new File(getOpennlpDataDir(),
        "conllx/data/swedish/talbanken05/train/swedish_talbanken05_train.conll"), "swe", params);

    eval(maxentModel, new File(getOpennlpDataDir(),
        "conllx/data/swedish/talbanken05/test/swedish_talbanken05_test.conll"), 0.9248585572842999d);
  }

  @Test
  public void evalSwedishMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    POSModel maxentModel = train(new File(getOpennlpDataDir(),
        "conllx/data/swedish/talbanken05/train/swedish_talbanken05_train.conll"), "swe", params);

    eval(maxentModel, new File(getOpennlpDataDir(),
        "conllx/data/swedish/talbanken05/test/swedish_talbanken05_test.conll"), 0.9347595473833098d);
  }
}
