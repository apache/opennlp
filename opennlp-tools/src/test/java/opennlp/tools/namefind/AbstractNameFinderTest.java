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

package opennlp.tools.namefind;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import opennlp.tools.AbstractModelLoaderTest;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

abstract class AbstractNameFinderTest extends AbstractModelLoaderTest {

  protected static boolean hasOtherAsOutcome(TokenNameFinderModel nameFinderModel) {
    SequenceClassificationModel model = nameFinderModel.getNameFinderSequenceModel();
    String[] outcomes = model.getOutcomes();
    for (String outcome : outcomes) {
      if (outcome.equals(NameFinderME.OTHER)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Trains a {@link TokenNameFinderModel} instance via the specified {@code trainingFile}.
   *
   * @param langCode The ISO-language code that fits the material in the {@code trainingFile}.
   * @param trainingFile The file with a (sufficient) amount of text data.
   *                     
   * @return A valid {@link TokenNameFinderModel} for the given input.
   * @throws IOException Thrown if IO errors occurred.
   */
  protected static TokenNameFinderModel trainModel(String langCode, String trainingFile) throws IOException {
    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 150);
    params.put(TrainingParameters.THREADS_PARAM, 4);
    params.put(TrainingParameters.CUTOFF_PARAM, 3);
    return trainModel(langCode, trainingFile, params);
  }

  /**
   * Trains a {@link TokenNameFinderModel} instance via the specified {@code trainingFile}.
   *
   * @param langCode The ISO-language code that fits the material in the {@code trainingFile}.
   * @param trainingFile The file with a (sufficient) amount of text data.
   * @param params A valid {@link TrainingParameters} configuration.
   *
   * @return A valid {@link TokenNameFinderModel} for the given input.
   * @throws IOException Thrown if IO errors occurred.
   */
  protected static TokenNameFinderModel trainModel(String langCode, String trainingFile,
                                            TrainingParameters params) throws IOException {
    return trainModel(langCode, trainingFile, params, null);
  }

  /**
   * Trains a {@link TokenNameFinderModel} instance via the specified {@code trainingFile}.
   *
   * @param langCode The ISO-language code that fits the material in the {@code trainingFile}.
   * @param trainingFile The file with a (sufficient) amount of text data.
   * @param params A valid {@link TrainingParameters} configuration.
   * @param featGeneratorBytes The {@code byte[]} representing the feature generator descriptor.
   *
   * @return A valid {@link TokenNameFinderModel} for the given input.
   * @throws IOException Thrown if IO errors occurred.
   */
  protected static TokenNameFinderModel trainModel(String langCode, String trainingFile,
                                                   TrainingParameters params,
                                                   byte[] featGeneratorBytes) throws IOException {
    ObjectStream<NameSample> sampleStream = new NameSampleDataStream(new PlainTextByLineStream(
            new MockInputStreamFactory(new File(trainingFile)), StandardCharsets.UTF_8));
    return NameFinderME.train(langCode, null, sampleStream, params,
            TokenNameFinderFactory.create(null, featGeneratorBytes, Collections.emptyMap(), new BioCodec()));
  }
}
