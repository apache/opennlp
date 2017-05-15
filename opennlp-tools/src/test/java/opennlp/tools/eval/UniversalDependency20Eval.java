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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import opennlp.tools.formats.conllu.ConlluLemmaSampleStream;
import opennlp.tools.formats.conllu.ConlluStream;
import opennlp.tools.formats.conllu.ConlluTagset;
import opennlp.tools.lemmatizer.LemmaSample;
import opennlp.tools.lemmatizer.LemmatizerEvaluator;
import opennlp.tools.lemmatizer.LemmatizerFactory;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

public class UniversalDependency20Eval {

  private static File SPA_ANCORA_TRAIN =
      new File(EvalUtil.getOpennlpDataDir(),"ud20/UD_Spanish-AnCora/es_ancora-ud-train.conllu");
  private static File SPA_ANCORA_DEV =
      new File(EvalUtil.getOpennlpDataDir(),"ud20/UD_Spanish-AnCora/es_ancora-ud-dev.conllu");

  @BeforeClass
  public static void ensureTestDataIsCorrect() throws IOException {
    SourceForgeModelEval.ensureTestDataIsCorrect();

    EvalUtil.verifyFileChecksum(SPA_ANCORA_TRAIN.toPath(),
        new BigInteger("224942804200733453179524127037951530195"));
    EvalUtil.verifyFileChecksum(SPA_ANCORA_DEV.toPath(),
        new BigInteger("280996187464384493180190898172297941708"));
  }

  private static double trainAndEval(String lang, File trainFile, TrainingParameters params,
                                     File evalFile) throws IOException {
    ConlluTagset tagset = ConlluTagset.X;

    ObjectStream<LemmaSample> trainSamples = new ConlluLemmaSampleStream(new ConlluStream(
        new MarkableFileInputStreamFactory(trainFile)), tagset);

    LemmatizerModel model = LemmatizerME.train(lang, trainSamples, params, new LemmatizerFactory());
    LemmatizerEvaluator evaluator = new LemmatizerEvaluator(new LemmatizerME(model));

    evaluator.evaluate(new ConlluLemmaSampleStream(new ConlluStream(
        new MarkableFileInputStreamFactory(evalFile)), tagset));

    return evaluator.getWordAccuracy();
  }

  @Test
  public void trainAndEvalSpanishAncora() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put("Threads", "4");

    double wordAccuracy = trainAndEval("spa", SPA_ANCORA_TRAIN,
        params, SPA_ANCORA_DEV);

    Assert.assertEquals(0.9046675934566091d, wordAccuracy, EvalUtil.ACCURACY_DELTA);
  }
}
