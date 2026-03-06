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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.GermEval2014NameSampleStream;
import opennlp.tools.formats.GermEval2014NameSampleStream.NerLayer;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderEvaluator;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

/**
 * Evaluates the name finder against the GermEval 2014 NER corpus (German).
 * <p>
 * Download the data files from the GermEval 2014 shared task
 * <a href="https://sites.google.com/site/germeval2014ner/data">site</a>
 * and place them into this directory: {@code $OPENNLP_DATA_DIR/germeval2014/}.
 * <p>
 * Expected files:
 * <ul>
 *   <li>{@code NER-de-train.tsv} - Training data</li>
 *   <li>{@code NER-de-test.tsv} - Test data</li>
 * </ul>
 */
public class GermEval2014NameFinderEval extends AbstractEvalTest {

  private static final int ALL_TYPES =
      GermEval2014NameSampleStream.GENERATE_PERSON_ENTITIES
          | GermEval2014NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
          | GermEval2014NameSampleStream.GENERATE_LOCATION_ENTITIES
          | GermEval2014NameSampleStream.GENERATE_MISC_ENTITIES;

  private static File trainingFile;
  private static File testFile;

  private TokenNameFinderModel train(final File trainFile, final TrainingParameters params,
                                     final int types) throws IOException {

    final ObjectStream<NameSample> samples = new GermEval2014NameSampleStream(
        new MarkableFileInputStreamFactory(trainFile), types, NerLayer.OUTER);

    return NameFinderME.train("deu", null, samples, params, new TokenNameFinderFactory());
  }

  private void eval(final TokenNameFinderModel model, final File testData,
                    final int types, final double expectedFMeasure) throws IOException {

    final ObjectStream<NameSample> samples = new GermEval2014NameSampleStream(
        new MarkableFileInputStreamFactory(testData), types, NerLayer.OUTER);

    final TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(new NameFinderME(model));
    evaluator.evaluate(samples);

    Assertions.assertEquals(expectedFMeasure, evaluator.getFMeasure().getFMeasure(), ACCURACY_DELTA);
  }

  @BeforeAll
  static void verifyTrainingData() throws Exception {

    trainingFile = new File(getOpennlpDataDir(), "germeval2014/NER-de-train.tsv");
    testFile = new File(getOpennlpDataDir(), "germeval2014/NER-de-test.tsv");

    verifyTrainingData(new GermEval2014NameSampleStream(
            new MarkableFileInputStreamFactory(trainingFile),
            ALL_TYPES, NerLayer.OUTER),
        new BigInteger("175386258960384643455328517118707394452"));
    verifyTrainingData(new GermEval2014NameSampleStream(
            new MarkableFileInputStreamFactory(testFile),
            ALL_TYPES, NerLayer.OUTER),
        new BigInteger("112232325598196372951673841456976805014"));
  }

  // -- Person entity evaluation --

  @Test
  void evalPersonPerceptron() throws IOException {
    final TrainingParameters params = createPerceptronParams();

    final TokenNameFinderModel model = train(trainingFile, params,
        GermEval2014NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(model, testFile,
        GermEval2014NameSampleStream.GENERATE_PERSON_ENTITIES, 0.6086631814787155d);
  }

  @Test
  void evalPersonMaxentGis() throws IOException {
    final TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    final TokenNameFinderModel model = train(trainingFile, params,
        GermEval2014NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(model, testFile,
        GermEval2014NameSampleStream.GENERATE_PERSON_ENTITIES, 0.5204518893650175d);
  }

  // -- Organization entity evaluation --

  @Test
  void evalOrganizationPerceptron() throws IOException {
    final TrainingParameters params = createPerceptronParams();

    final TokenNameFinderModel model = train(trainingFile, params,
        GermEval2014NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(model, testFile,
        GermEval2014NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5588235294117646d);
  }

  @Test
  void evalOrganizationMaxentGis() throws IOException {
    final TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    final TokenNameFinderModel model = train(trainingFile, params,
        GermEval2014NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(model, testFile,
        GermEval2014NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.4594180704441041d);
  }

  // -- Location entity evaluation --

  @Test
  void evalLocationPerceptron() throws IOException {
    final TrainingParameters params = createPerceptronParams();

    final TokenNameFinderModel model = train(trainingFile, params,
        GermEval2014NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(model, testFile,
        GermEval2014NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6705613411226822d);
  }

  @Test
  void evalLocationMaxentGis() throws IOException {
    final TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    final TokenNameFinderModel model = train(trainingFile, params,
        GermEval2014NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(model, testFile,
        GermEval2014NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.5537280701754386d);
  }

  // -- Misc (OTH) entity evaluation --

  @Test
  void evalMiscPerceptron() throws IOException {
    final TrainingParameters params = createPerceptronParams();

    final TokenNameFinderModel model = train(trainingFile, params,
        GermEval2014NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(model, testFile,
        GermEval2014NameSampleStream.GENERATE_MISC_ENTITIES, 0.4482142857142857d);
  }

  @Test
  void evalMiscMaxentGis() throws IOException {
    final TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    final TokenNameFinderModel model = train(trainingFile, params,
        GermEval2014NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(model, testFile,
        GermEval2014NameSampleStream.GENERATE_MISC_ENTITIES, 0.3932267168391345d);
  }

  // -- Combined (all types) evaluation --

  @Test
  void evalCombinedPerceptron() throws IOException {
    final TrainingParameters params = createPerceptronParams();

    final TokenNameFinderModel model = train(trainingFile, params, ALL_TYPES);

    eval(model, testFile, ALL_TYPES, 0.6016631636662707d);
  }

  @Test
  void evalCombinedMaxentGis() throws IOException {
    final TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    final TokenNameFinderModel model = train(trainingFile, params, ALL_TYPES);

    eval(model, testFile, ALL_TYPES, 0.5229054890631449d);
  }
}
