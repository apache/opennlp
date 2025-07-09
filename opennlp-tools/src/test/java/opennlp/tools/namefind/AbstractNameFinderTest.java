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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

abstract class AbstractNameFinderTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractNameFinderTest.class);

  protected static final Path OPENNLP_DIR = Paths.get(System.getProperty("OPENNLP_DOWNLOAD_HOME",
          System.getProperty("user.home"))).resolve(".opennlp");

  private static final String BASE_URL_MODELS_V15 = System.getProperty("opennlp.model.v15.base.url", "https://opennlp.sourceforge.net/models-1.5/");

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
    params.put(Parameters.ITERATIONS_PARAM, 150);
    params.put(Parameters.THREADS_PARAM, 4);
    params.put(Parameters.CUTOFF_PARAM, 3);
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

  protected static void downloadVersion15Model(String modelName) throws IOException {
    downloadModel(new URL(BASE_URL_MODELS_V15 + modelName));
  }

  private static void downloadModel(URL url) throws IOException {
    if (!Files.isDirectory(OPENNLP_DIR)) {
      OPENNLP_DIR.toFile().mkdir();
    }
    final String filename = url.toString().substring(url.toString().lastIndexOf("/") + 1);
    final Path localFile = Paths.get(OPENNLP_DIR.toString(), filename);

    if (!Files.exists(localFile)) {
      logger.debug("Downloading model from {} to {}.", url, localFile);
      try (final InputStream in = new BufferedInputStream(url.openStream())) {
        Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
      }
      logger.debug("Download complete.");
    }
  }
}
