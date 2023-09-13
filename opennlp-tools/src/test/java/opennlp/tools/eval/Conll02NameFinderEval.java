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

import opennlp.tools.formats.Conll02NameSampleStream;
import opennlp.tools.formats.Conll02NameSampleStream.LANGUAGE;
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
 * Evaluates the name finder against the Dutch and Spanish CONLL2002 corpus.
 * <p>
 * Download the tarball from the CONLL2002 shared task
 * <a href="https://www.cnts.ua.ac.be/conll2002/ner/"> site </a>
 * and decompress it into this directory: $OPENNLP_DATA_DIR/conll2002.
 * Also decompress the training files.
 * <p>
 * TODO:
 * - Files are provided in gzipped. It would be better if they would not be unpacked by the user.
 * - Double check the encoding which is used to open the files. Currently that is UTF-8.
 * - Make the Conll02 reader compatible. Currently it doesn't work with spanish data without pos tags.
 */
public class Conll02NameFinderEval extends AbstractEvalTest {

  private static File dutchTrainingFile;
  private static File dutchTestAFile;
  private static File dutchTestBFile;
  private static File spanishTrainingFile;
  private static File spanishTestAFile;
  private static File spanishTestBFile;


  private TokenNameFinderModel train(File trainFile, LANGUAGE lang,
                                     TrainingParameters params, int types) throws IOException {

    ObjectStream<NameSample> samples = new Conll02NameSampleStream(
        lang, new MarkableFileInputStreamFactory(trainFile), types);

    return NameFinderME.train(lang.toString().toLowerCase(), null, samples,
        params, new TokenNameFinderFactory());
  }

  private void eval(TokenNameFinderModel model, File testData, LANGUAGE lang,
                    int types, double expectedFMeasure) throws IOException {

    ObjectStream<NameSample> samples = new Conll02NameSampleStream(
        lang, new MarkableFileInputStreamFactory(testData), types);

    TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(new NameFinderME(model));
    evaluator.evaluate(samples);

    Assertions.assertEquals(expectedFMeasure, evaluator.getFMeasure().getFMeasure(), 0.0001);
  }

  @BeforeAll
  static void verifyTrainingData() throws Exception {

    dutchTrainingFile = new File(getOpennlpDataDir(), "conll02/ner/data/ned.train");
    dutchTestAFile = new File(getOpennlpDataDir(), "conll02/ner/data/ned.testa");
    dutchTestBFile = new File(getOpennlpDataDir(), "conll02/ner/data/ned.testb");
    spanishTrainingFile = new File(getOpennlpDataDir(), "conll02/ner/data/esp.train");
    spanishTestAFile = new File(getOpennlpDataDir(), "conll02/ner/data/esp.testa");
    spanishTestBFile = new File(getOpennlpDataDir(), "conll02/ner/data/esp.testb");

    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.NLD, new MarkableFileInputStreamFactory(dutchTrainingFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("244586345524636491735310529744396558541"));
    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.NLD, new MarkableFileInputStreamFactory(dutchTestAFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("246627484906192029575077493716150690762"));
    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.NLD, new MarkableFileInputStreamFactory(dutchTestBFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("160341129860513958203421820548607024932"));

    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.SPA, new MarkableFileInputStreamFactory(spanishTrainingFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("77622870982561669762102345960336466598"));
    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.SPA, new MarkableFileInputStreamFactory(spanishTestAFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("143641744095673112729040601949871621359"));
    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.SPA, new MarkableFileInputStreamFactory(spanishTestBFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("91310271319593094243555094042436157393"));

  }

  @Test
  void evalDutchPersonPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.6590308370044053d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7396672034353193d);
  }

  @Test
  void evalDutchPersonMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.5691489361702128d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7148891235480465d);
  }

  @Test
  void evalDutchPersonMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.6356311548791406d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7458533975387909d);
  }

  @Test
  void evalDutchOrganizationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6289549376797698d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6498245614035087d);
  }

  @Test
  void evalDutchOrganizationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5197969543147207d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5716342692584593d);
  }

  @Test
  void evalDutchOrganizationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5851703406813628d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6089466089466089d);
  }

  @Test
  void evalDutchLocationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7887005649717513d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7943859649122806d);
  }

  @Test
  void evalDutchLocationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.564673157162726d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6835443037974683d);
  }

  @Test
  void evalDutchLocationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6711229946524064d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.738993710691824d);
  }

  @Test
  void evalDutchMiscPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.6676691729323307d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.6711345141215893d);
  }

  @Test
  void evalDutchMiscMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5831157528285466d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5755079626578803d);
  }

  @Test
  void evalDutchMiscMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5965858041329739d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5876993166287016d);
  }

  @Test
  void evalDutchCombinedPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        combinedType);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD, combinedType, 0.7170923379174853d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD, combinedType, 0.7442767950052028d);
  }

  @Test
  void evalDutchCombinedMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        combinedType);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD, combinedType, 0.6687585801137477d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD, combinedType, 0.699353169469599d);
  }

  @Test
  void evalDutchCombinedMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        combinedType);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD, combinedType, 0.7084501401682018d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD, combinedType, 0.7370923015977816d);
  }

  @Test
  void evalSpanishPersonPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8177509694097371d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8438928804702809d);
  }

  @Test
  void evalSpanishPersonMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.684263959390863d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8142532221379833d);
  }


  @Test
  void evalSpanishPersonMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7399014778325124d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8282977155490052d);
  }

  @Test
  void evalSpanishOrganizationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7406759906759908d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7764471057884231d);
  }

  @Test
  void evalSpanishOrganizationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6988771691051379d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7638680659670164d);
  }

  @Test
  void evalSpanishOrganizationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6921985815602836d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7797548438117834d);
  }

  @Test
  void evalSpanishLocationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7156983930778738d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6361474435196195d);
  }

  @Test
  void evalSpanishLocationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7376263970196913d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6746987951807228d);
  }

  @Test
  void evalSpanishLocationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7463726059199071d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6772727272727272d);
  }

  @Test
  void evalSpanishMiscPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5020352781546812d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5703564727954972d);
  }

  @Test
  void evalSpanishMiscMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.4176829268292683d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.461839530332681d);
  }

  @Test
  void evalSpanishMiscMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.478395061728395d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5153374233128835d);
  }

  @Test
  void evalSpanishCombinedPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        combinedType);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA, combinedType, 0.7383003492433061d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA, combinedType, 0.748207507380852d);
  }

  @Test
  void evalSpanishCombinedMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        combinedType);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA, combinedType, 0.7060201452330757d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA, combinedType, 0.7549668874172185d);
  }

  @Test
  void evalSpanishCombinedMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        combinedType);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA, combinedType, 0.7204819277108434d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA, combinedType, 0.7514002585092633d);
  }
}
