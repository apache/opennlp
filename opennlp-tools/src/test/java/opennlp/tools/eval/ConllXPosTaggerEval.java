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
import java.nio.charset.Charset;

import org.junit.Assert;
import org.junit.Test;

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
public class ConllXPosTaggerEval {

  private static POSModel train(File trainFile, String lang,
                                TrainingParameters params) throws IOException {

    ObjectStream<POSSample> samples =
        new ConllXPOSSampleStream(new MarkableFileInputStreamFactory(trainFile), Charset.forName("UTF-8"));

    return POSTaggerME.train(lang, samples, params, new POSTaggerFactory());
  }

  private static void eval(POSModel model, File testData,
                           double expectedAccuracy) throws IOException {

    ObjectStream<POSSample> samples = new ConllXPOSSampleStream(
        new MarkableFileInputStreamFactory(testData), Charset.forName("UTF-8"));

    POSEvaluator evaluator = new POSEvaluator(new POSTaggerME(model));
    evaluator.evaluate(samples);

    Assert.assertEquals(expectedAccuracy, evaluator.getWordAccuracy(), 0.0001);
  }

  @Test
  public void evalDanishMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    POSModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/danish/ddt/train/danish_ddt_train.conll"), "da", params);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/danish/ddt/test/danish_ddt_test.conll"), 0.9512987012987013d);
  }

  @Test
  public void evalDanishMaxentQn() throws IOException {
    TrainingParameters params = EvalUtil.createMaxentQnParams();

    POSModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/danish/ddt/train/danish_ddt_train.conll"), "da", params);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/danish/ddt/test/danish_ddt_test.conll"), 0.9456596035543404d);
  }

  @Test
  public void evalDutchMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    POSModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/dutch/alpino/train/dutch_alpino_train.conll"), "nl", params);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/dutch/alpino/test/dutch_alpino_test.conll"), 0.9174574753804834d);
  }

  @Test
  public void evalDutchMaxentQn() throws IOException {
    TrainingParameters params = EvalUtil.createMaxentQnParams();

    POSModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/dutch/alpino/train/dutch_alpino_train.conll"), "nl", params);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/dutch/alpino/test/dutch_alpino_test.conll"), 0.9025962399283796d);
  }

  @Test
  public void evalPortugueseMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    POSModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/portuguese/bosque/treebank/portuguese_bosque_train.conll"), "pt", params);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/portuguese/bosque/test/portuguese_bosque_test.conll"), 0.9659110277825124d);
  }

  @Test
  public void evalPortugueseMaxentQn() throws IOException {
    TrainingParameters params = EvalUtil.createMaxentQnParams();

    POSModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/portuguese/bosque/treebank/portuguese_bosque_train.conll"), "pt", params);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/portuguese/bosque/test/portuguese_bosque_test.conll"), 0.9676154763933867d);
  }

  @Test
  public void evalSwedishMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    POSModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/swedish/talbanken05/train/swedish_talbanken05_train.conll"), "se", params);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/swedish/talbanken05/test/swedish_talbanken05_test.conll"), 0.9275106082036775d);
  }

  @Test
  public void evalSwedishMaxentQn() throws IOException {
    TrainingParameters params = EvalUtil.createMaxentQnParams();

    POSModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/swedish/talbanken05/train/swedish_talbanken05_train.conll"), "se", params);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conllx/data/swedish/talbanken05/test/swedish_talbanken05_test.conll"), 0.9245049504950495d);
  }
}
