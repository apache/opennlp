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
import java.io.FileInputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.chunker.ChunkerCrossValidator;
import opennlp.tools.chunker.ChunkerFactory;
import opennlp.tools.formats.ad.ADChunkSampleStream;
import opennlp.tools.formats.ad.ADNameSampleStream;
import opennlp.tools.formats.ad.ADSentenceSampleStream;
import opennlp.tools.formats.convert.NameToTokenSampleStream;
import opennlp.tools.ml.perceptron.PerceptronTrainer;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.sentdetect.SDCrossValidator;
import opennlp.tools.sentdetect.SentenceDetectorFactory;
import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.tokenize.DetokenizationDictionary;
import opennlp.tools.tokenize.DictionaryDetokenizer;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerCrossValidator;
import opennlp.tools.tokenize.TokenizerFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

/**
 * Cross validation of Sentence Detector, Tokenizer and Chunker against the
 * Portugues corpus.
 * <p>
 * Download the gz files from the Floresta Sintactica project <a
 * href="http://www.linguateca.pt/floresta/corpus.html"> site </a> and
 * decompress it into this directory: $OPENNLP_DATA_DIR/ad.
 * <ul>
 * <li><a href=
 * "http://www.linguateca.pt/floresta/ficheiros/gz/FlorestaVirgem_CF_3.0_ad.txt.gz"
 * > FlorestaVirgem_CF_3.0_ad.txt.gz </a></li>
 * <li><a href=
 * "http://www.linguateca.pt/floresta/ficheiros/gz/Bosque_CF_8.0.ad.txt.gz">
 * Bosque_CF_8.0.ad.txt.gz </a></li>
 * </ul>
 */
public class ArvoresDeitadasEval {

  private static final String BOSQUE = "ad/Bosque_CF_8.0.ad.txt";
  private static final String FLORESTA_VIRGEM = "ad/FlorestaVirgem_CF_3.0_ad.txt";

  private static final String ENCODING = "ISO-8859-1";

  private static final String LANG = "pt";

  private static TrainingParameters getPerceptronZeroCutoff() {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM,
        PerceptronTrainer.PERCEPTRON_VALUE);
    params.put(TrainingParameters.CUTOFF_PARAM, "0");

    return params;
  }

  private static ObjectStream<String> getLineSample(String corpus)
      throws IOException {
    return new PlainTextByLineStream(new MarkableFileInputStreamFactory(
        new File(EvalUtil.getOpennlpDataDir(), corpus)), ENCODING);
  }

  private static void sentenceCrossEval(TrainingParameters params,
                                        double expectedScore) throws IOException {

    ADSentenceSampleStream samples = new ADSentenceSampleStream(
        getLineSample(FLORESTA_VIRGEM), false);

    SDCrossValidator cv = new SDCrossValidator(LANG, params,
        new SentenceDetectorFactory(LANG, true, null,
            new Factory().getEOSCharacters(LANG)));

    cv.evaluate(samples, 10);

    System.out.println(cv.getFMeasure());
    Assert.assertEquals(expectedScore, cv.getFMeasure().getFMeasure(), 0.0001d);
  }

  private static void tokenizerCrossEval(TrainingParameters params,
                                         double expectedScore) throws IOException {

    ObjectStream<NameSample> nameSamples = new ADNameSampleStream(
        getLineSample(FLORESTA_VIRGEM), true);

    DictionaryDetokenizer detokenizer = new DictionaryDetokenizer(
        new DetokenizationDictionary(new FileInputStream(new File(
            "lang/pt/tokenizer/pt-detokenizer.xml"))));

    ObjectStream<TokenSample> samples = new NameToTokenSampleStream(
        detokenizer, nameSamples);

    TokenizerCrossValidator validator;

    TokenizerFactory tokFactory = TokenizerFactory.create(null, LANG, null,
        true, null);
    validator = new opennlp.tools.tokenize.TokenizerCrossValidator(params,
        tokFactory);

    validator.evaluate(samples, 10);

    System.out.println(validator.getFMeasure());
    Assert.assertEquals(expectedScore, validator.getFMeasure().getFMeasure(),
        0.0001d);
  }

  private static void chunkerCrossEval(TrainingParameters params,
                                       double expectedScore) throws IOException {

    ADChunkSampleStream samples = new ADChunkSampleStream(getLineSample(BOSQUE));

    ChunkerCrossValidator cv = new ChunkerCrossValidator(LANG, params,
        new ChunkerFactory());

    cv.evaluate(samples, 10);
    Assert.assertEquals(expectedScore, cv.getFMeasure().getFMeasure(), 0.0001d);
  }

  @Test
  public void evalPortugueseSentenceDetector() throws IOException {
    sentenceCrossEval(getPerceptronZeroCutoff(), 0.9892778840089301d);
  }

  @Test
  public void evalPortugueseTokenizer() throws IOException {
    tokenizerCrossEval(getPerceptronZeroCutoff(), 0.9994887308380267d);
  }

  @Test
  public void evalPortugueseChunker() throws IOException {
    chunkerCrossEval(ModelUtil.createDefaultTrainingParameters(),
        0.9573860781121228d);
  }
}
