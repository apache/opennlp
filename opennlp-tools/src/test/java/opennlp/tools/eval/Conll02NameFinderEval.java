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

import org.junit.Assert;
import org.junit.Test;

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
public class Conll02NameFinderEval {

  private static TokenNameFinderModel train(File trainFile, LANGUAGE lang,
      TrainingParameters params, int types) throws IOException {

    ObjectStream<NameSample> samples = new Conll02NameSampleStream(
        lang,new MarkableFileInputStreamFactory(trainFile), types);

    return  NameFinderME.train(lang.toString().toLowerCase(), null, samples,
        params, new TokenNameFinderFactory());
  }

  private static void eval(TokenNameFinderModel model, File testData, LANGUAGE lang,
      int types, double expectedFMeasure) throws IOException {

    ObjectStream<NameSample> samples = new Conll02NameSampleStream(
        lang, new MarkableFileInputStreamFactory(testData), types);

    TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(new NameFinderME(model));
    evaluator.evaluate(samples);

    Assert.assertEquals(expectedFMeasure, evaluator.getFMeasure().getFMeasure(), 0.0001);
  }

  @Test
  public void evalDutchPerson() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.train"), LANGUAGE.NL, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testa"), LANGUAGE.NL, Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.5696539485359361d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testb"), LANGUAGE.NL, Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.7127771911298839d);
  }

  @Test
  public void evalDutchOrganization() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.train"), LANGUAGE.NL, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testa"), LANGUAGE.NL, Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5197969543147207d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testb"), LANGUAGE.NL, Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.5753228120516498d);
  }

  @Test
  public void evalDutchLocation() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.train"), LANGUAGE.NL, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testa"), LANGUAGE.NL, Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.5451977401129944d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testb"), LANGUAGE.NL, Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.680952380952381d);
  }

  @Test
  public void evalDutchMisc() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.train"), LANGUAGE.NL, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testa"), LANGUAGE.NL, Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5831157528285466d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testb"), LANGUAGE.NL, Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.5762897914379803d);
  }

  @Test
  public void evalDutchCombined() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.train"), LANGUAGE.NL, params,
        combinedType);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testa"), LANGUAGE.NL,   combinedType, 0.6728164867517175d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/ned.testb"), LANGUAGE.NL, combinedType, 0.6985893619774816d);
  }

  @Test
  public void evalSpanishPerson() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.train"), LANGUAGE.ES, params,
        Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testa"), LANGUAGE.ES, Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.686960933536276d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testb"), LANGUAGE.ES, Conll02NameSampleStream.GENERATE_PERSON_ENTITIES, 0.8132033008252063d);
  }

  @Test
  public void evalSpanishOrganization() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.train"), LANGUAGE.ES, params,
        Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testa"), LANGUAGE.ES, Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.6982288828337874d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testb"), LANGUAGE.ES, Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 0.7640449438202247d);
  }

  @Test
  public void evalSpanishLocation() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.train"), LANGUAGE.ES, params,
        Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testa"), LANGUAGE.ES, Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.7386907929749867d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testb"), LANGUAGE.ES, Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES, 0.6772777167947311d);
  }

  @Test
  public void evalSpanishMisc() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.train"), LANGUAGE.ES, params,
        Conll02NameSampleStream.GENERATE_MISC_ENTITIES);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testa"), LANGUAGE.ES, Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.40971168437025796d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testb"), LANGUAGE.ES, Conll02NameSampleStream.GENERATE_MISC_ENTITIES, 0.45703124999999994d);
  }

  @Test
  public void evalSpanishCombined() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();

    int combinedType = Conll02NameSampleStream.GENERATE_PERSON_ENTITIES | Conll02NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
        | Conll02NameSampleStream.GENERATE_LOCATION_ENTITIES | Conll02NameSampleStream.GENERATE_MISC_ENTITIES;

    TokenNameFinderModel maxentModel = train(new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.train"), LANGUAGE.ES, params,
        combinedType);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testa"), LANGUAGE.ES, combinedType, 0.706765154179857d);

    eval(maxentModel, new File(EvalUtil.getOpennlpDataDir(),
        "conll02/ner/data/esp.testb"), LANGUAGE.ES, combinedType, 0.7583580194667795d);
  }
}
