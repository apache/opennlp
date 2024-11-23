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

package opennlp.tools.postag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.AbstractModelLoaderTest;
import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

/**
 * Tests for the {@link POSTaggerME} class.
 */

public class POSTaggerMETest extends AbstractModelLoaderTest {

  private static final String[] sentence =
      {"The", "driver", "got", "badly", "injured", "by", "the", "accident", "."};

  private static ObjectStream<POSSample> createSampleStream() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(POSTaggerMETest.class,
        "/opennlp/tools/postag/AnnotatedSentences.txt"); //PENN FORMAT

    return new WordTagSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  /**
   * Trains a POSModel from the annotated test data.
   *
   * @return {@link POSModel}
   */
  public static POSModel trainPennFormatPOSModel(ModelType type) throws IOException {
    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ALGORITHM_PARAM, type.toString());
    params.put(TrainingParameters.ITERATIONS_PARAM, 100);
    params.put(TrainingParameters.CUTOFF_PARAM, 5);

    return POSTaggerME.train("eng", createSampleStream(), params,
        new POSTaggerFactory());
  }

  @Test
  void testPOSTagger() throws IOException {
    final String[] expected = {"DT", "NN", "VBD", "RB", "VBN", "IN", "DT", "NN", "."};
    testPOSTagger(new POSTaggerME(trainPennFormatPOSModel(ModelType.MAXENT),
        POSTagFormat.PENN), sentence, expected);
  }

  @Test
  void testPOSTaggerPENNtoUD() throws IOException {
    final String[] expected = {"DET", "NOUN", "VERB", "ADV", "VERB", "ADP", "DET", "NOUN", "PUNCT"};
    //convert PENN to UD on the fly.
    testPOSTagger(new POSTaggerME(trainPennFormatPOSModel(ModelType.MAXENT),
        POSTagFormat.UD), sentence, expected);
  }

  @Test
  void testPOSTaggerMappingNoOp() throws IOException {
    final String[] expected = {"DT", "NN", "VBD", "RB", "VBN", "IN", "DT", "NN", "."};
    //leave it as is
    testPOSTagger(new POSTaggerME(trainPennFormatPOSModel(ModelType.MAXENT),
        POSTagFormat.CUSTOM), sentence, expected);
  }

  @Test
  @EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
  void testPOSTaggerDefault() throws IOException {
    final String[] expected = {"DET", "NOUN", "VERB", "ADV", "VERB", "ADP", "DET", "NOUN", "PUNCT"};
    //this downloads a UD model
    testPOSTagger(new POSTaggerME("en"), sentence, expected);
  }

  @Test
  @EnabledWhenCDNAvailable(hostname = "opennlp.sourceforge.net")
  void testPOSTaggerLegacyPerceptronPennToUD() throws IOException {
    final String[] expected = {"DET", "NOUN", "VERB", "ADV", "VERB", "ADP", "DET", "NOUN", "PUNCT"};
    //convert PENN to UD on the fly.
    testPOSTagger(new POSTaggerME(getVersion15Model("en-pos-perceptron.bin"),
        POSTagFormat.UD), sentence, expected);
  }

  @Test
  @EnabledWhenCDNAvailable(hostname = "opennlp.sourceforge.net")
  void testPOSTaggerLegacyPerceptronPenn() throws IOException {
    final String[] expected = {"DT", "NN", "VBD", "RB", "VBN", "IN", "DT", "NN", "."};
    //convert PENN to UD on the fly.
    testPOSTagger(new POSTaggerME(getVersion15Model("en-pos-perceptron.bin"),
        POSTagFormat.PENN), sentence, expected);
  }

  @Test
  @EnabledWhenCDNAvailable(hostname = "opennlp.sourceforge.net")
  void testPOSTaggerLegacyMaxentPennToUD() throws IOException {
    final String[] expected = {"DET", "NOUN", "VERB", "ADV", "VERB", "ADP", "DET", "NOUN", "PUNCT"};
    //convert PENN to UD on the fly.
    testPOSTagger(new POSTaggerME(getVersion15Model("en-pos-maxent.bin"),
        POSTagFormat.UD), sentence, expected);
  }

  @Test
  @EnabledWhenCDNAvailable(hostname = "opennlp.sourceforge.net")
  void testPOSTaggerLegacyMaxentPenn() throws IOException {
    final String[] expected = {"DT", "NN", "VBD", "RB", "VBN", "IN", "DT", "NN", "."};
    //convert PENN to UD on the fly.
    testPOSTagger(new POSTaggerME(getVersion15Model("en-pos-maxent.bin"),
        POSTagFormat.PENN), sentence, expected);
  }

  private POSModel getVersion15Model(String modelName) throws IOException {
    downloadVersion15Model(modelName);
    final Path modelPath = OPENNLP_DIR.resolve(modelName);
    return new POSModel(modelPath);
  }

  private void testPOSTagger(POSTagger tagger, String[] sentences, String[] expectedTags) {
    Assertions.assertArrayEquals(expectedTags, tagger.tag(sentences));
  }

  @Test
  void testBuildNGramDictionary() throws IOException {
    ObjectStream<POSSample> samples = createSampleStream();
    POSTaggerME.buildNGramDictionary(samples, 0);
  }

  @Test
  void insufficientTestData() {

    Assertions.assertThrows(InsufficientTrainingDataException.class, () -> {

      InputStreamFactory in = new ResourceAsStreamFactory(POSTaggerMETest.class,
          "/opennlp/tools/postag/AnnotatedSentencesInsufficient.txt");

      ObjectStream<POSSample> stream = new WordTagSampleStream(
          new PlainTextByLineStream(in, StandardCharsets.UTF_8));

      TrainingParameters params = new TrainingParameters();
      params.put(TrainingParameters.ALGORITHM_PARAM, ModelType.MAXENT.name());
      params.put(TrainingParameters.ITERATIONS_PARAM, 100);
      params.put(TrainingParameters.CUTOFF_PARAM, 5);

      POSTaggerME.train("eng", stream, params, new POSTaggerFactory());

    });


  }

}
