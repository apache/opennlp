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
        new BigInteger("109687424525847313767541246922170457976"));
    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.NLD, new MarkableFileInputStreamFactory(dutchTestAFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("12942966701628852910737840182656846323"));
    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.NLD, new MarkableFileInputStreamFactory(dutchTestBFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("223206987942490952427646331013509976957"));

    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.SPA, new MarkableFileInputStreamFactory(spanishTrainingFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("226089384066775461905386060946810714487"));
    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.SPA, new MarkableFileInputStreamFactory(spanishTestAFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("313879596837181728494732341737647284762"));
    verifyTrainingData(new Conll02NameSampleStream(
            LANGUAGE.SPA, new MarkableFileInputStreamFactory(spanishTestBFile),
            Conll02NameSampleStream.GENERATE_PERSON_ENTITIES),
        new BigInteger("24037715705115461166858183817622459974"));

  }

  @Test
  void evalDutchPersonPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.6238361266294227d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.744312026002167d);
  }

  @Test
  void evalDutchPersonMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.5696539485359361d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7127771911298839d);
  }

  @Test
  void evalDutchPersonMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.6363636363636364d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7482403898213319d);
  }

  @Test
  void evalDutchOrganizationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6081871345029239d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6502808988764045d);
  }

  @Test
  void evalDutchOrganizationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5197969543147207d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5753228120516498d);
  }

  @Test
  void evalDutchOrganizationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5412748171368861d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5764966740576497d);
  }

  @Test
  void evalDutchLocationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7978609625668449d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7880434782608695d);
  }

  @Test
  void evalDutchLocationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.5451977401129944d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.680952380952381d);
  }

  @Test
  void evalDutchLocationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6737683089214381d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7433903576982893d);
  }

  @Test
  void evalDutchMiscPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.6651198762567672d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.6748166259168704d);
  }

  @Test
  void evalDutchMiscMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5831157528285466d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5762897914379803d);
  }

  @Test
  void evalDutchMiscMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.4227642276422764d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.455294863665187d);
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

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD, combinedType, 0.727808326787117d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD, combinedType, 0.7388253638253639d);
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

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD, combinedType, 0.6673209028459275d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD, combinedType, 0.6984085910208306d);
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

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD, combinedType, 0.6999800915787379d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD, combinedType, 0.7101430258496261d);
  }

  @Test
  void evalSpanishPersonPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8331210191082803d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8419705694177864d);
  }

  @Test
  void evalSpanishPersonMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.686960933536276d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8132033008252063d);
  }


  @Test
  void evalSpanishPersonMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7454634624816087d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8215339233038348d);
  }

  @Test
  void evalSpanishOrganizationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7478819748758399d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7715330894579315d);
  }

  @Test
  void evalSpanishOrganizationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6982288828337874d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7640449438202247d);
  }

  @Test
  void evalSpanishOrganizationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6904593639575972d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7843601895734598d);
  }

  @Test
  void evalSpanishLocationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7018867924528303d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6315158777711205d);
  }

  @Test
  void evalSpanishLocationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7386907929749867d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6772777167947311d);
  }

  @Test
  void evalSpanishLocationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7544565842438182d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7005019520356944d);
  }

  @Test
  void evalSpanishMiscPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5102880658436214d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5842696629213483d);
  }

  @Test
  void evalSpanishMiscMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.40971168437025796d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.45703124999999994d);
  }

  @Test
  void evalSpanishMiscMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.46467817896389324d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5020576131687243d);
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

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA, combinedType, 0.7476700838769804d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA, combinedType, 0.7692307692307693d);
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

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA, combinedType, 0.707400023454908d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA, combinedType, 0.7576868829337094d);
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

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA, combinedType, 0.7455564833591795d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA, combinedType, 0.7856735159817352d);
  }
}
