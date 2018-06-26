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
 * <a href="http://www.cnts.ua.ac.be/conll2002/ner/"> site </a>
 * and decompress it into this directory: $OPENNLP_DATA_DIR/conll2002.
 * Also decompress the training files.
 *
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
        lang,new MarkableFileInputStreamFactory(trainFile), types);

    return  NameFinderME.train(lang.toString().toLowerCase(), null, samples,
        params, new TokenNameFinderFactory());
  }

  private void eval(TokenNameFinderModel model, File testData, LANGUAGE lang,
      int types, double expectedFMeasure) throws IOException {

    ObjectStream<NameSample> samples = new Conll02NameSampleStream(
        lang, new MarkableFileInputStreamFactory(testData), types);

    TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(new NameFinderME(model));
    evaluator.evaluate(samples);

    Assert.assertEquals(expectedFMeasure, evaluator.getFMeasure().getFMeasure(), 0.0001);
  }
  
  @BeforeClass
  public static void verifyTrainingData() throws Exception {

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
  public void evalDutchPersonPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.6349496797804209d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7254575707154741d);
  }

  @Test
  public void evalDutchPersonMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.5660714285714287d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.6967257112184647d);
  }

  @Test
  public void evalDutchPersonMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.647985989492119d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7538218239325251d);
  }

  @Test
  public void evalDutchOrganizationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6120857699805068d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6367521367521367d);
  }

  @Test
  public void evalDutchOrganizationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5241935483870968d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5757358219669778d);
  }

  @Test
  public void evalDutchOrganizationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5774647887323944d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6099495313626532d);
  }

  @Test
  public void evalDutchLocationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.8056768558951964d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7971830985915492d);
  }

  @Test
  public void evalDutchLocationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.5598885793871866d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6873508353221957d);
  }

  @Test
  public void evalDutchLocationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6816380449141347d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7421875d);
  }

  @Test
  public void evalDutchMiscPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.6691176470588235d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.6788560712611346d);
  }

  @Test
  public void evalDutchMiscMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5819456617002629d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5709552733296521d);
  }

  @Test
  public void evalDutchMiscMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5871559633027523d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.572928821470245d);
  }

  @Test
  public void evalDutchCombinedPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        combinedType);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,   combinedType, 0.7225274725274724d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD, combinedType, 0.7463230508915789d);
  }

  @Test
  public void evalDutchCombinedMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        combinedType);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,   combinedType, 0.6681034482758621d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD, combinedType, 0.6937297996121526d);
  }

  @Test
  public void evalDutchCombinedMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(dutchTrainingFile, LANGUAGE.NLD, params,
        combinedType);

    eval(maxentModel, dutchTestAFile, LANGUAGE.NLD,   combinedType, 0.6804615996816554d);

    eval(maxentModel, dutchTestBFile, LANGUAGE.NLD, combinedType, 0.7041513399894903d);
  }

  @Test
  public void evalSpanishPersonPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8156374944469125d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8577101257445401d);
  }

  @Test
  public void evalSpanishPersonMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.6958710976837865d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8035714285714285d);
  }


  @Test
  public void evalSpanishPersonMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7475633528265108d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8277372262773722d);
  }

  @Test
  public void evalSpanishOrganizationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7429893032668402d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7630548302872063d);
  }

  @Test
  public void evalSpanishOrganizationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6979911474293496d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7628711010898159d);
  }

  @Test
  public void evalSpanishOrganizationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6874105865522175d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7796340493237868d);
  }

  @Test
  public void evalSpanishLocationPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7032136105860114d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6150121065375302d);
  }

  @Test
  public void evalSpanishLocationMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7417640807651434d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6761800219538968d);
  }

  @Test
  public void evalSpanishLocationMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7520278099652375d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6840317100792752d);
  }

  @Test
  public void evalSpanishMiscPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5190409026798307d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5769230769230769d);
  }

  @Test
  public void evalSpanishMiscMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.4108761329305136d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.4470588235294118d);
  }

  @Test
  public void evalSpanishMiscMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.48037676609105173d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.4686192468619247d);
  }

  @Test
  public void evalSpanishCombinedPerceptron() throws IOException {
    TrainingParameters params = createPerceptronParams();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        combinedType);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA, combinedType, 0.7526026435840448d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA, combinedType, 0.7633094535749403d);
  }

  @Test
  public void evalSpanishCombinedMaxentGis() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        combinedType);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA, combinedType, 0.7130964943135186d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA, combinedType, 0.7600507829030893d);
  }

  @Test
  public void evalSpanishCombinedMaxentQn() throws IOException {
    TrainingParameters params = createMaxentQnParams();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES
        | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(spanishTrainingFile, LANGUAGE.SPA, params,
        combinedType);

    eval(maxentModel, spanishTestAFile, LANGUAGE.SPA, combinedType, 0.7192413875885248d);

    eval(maxentModel, spanishTestBFile, LANGUAGE.SPA, combinedType, 0.7692528735632184d);
  }
}
