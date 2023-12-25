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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.cmdline.AbstractModelLoaderTest;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.namefind.TokenNameFinderTrainerTool;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerMETest;
import opennlp.tools.util.FileUtil;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

public class TokenNameFinderModelTest extends AbstractModelLoaderTest {

  @Test
  void testNERWithPOSModel() throws IOException {

    // create a resources folder
    Path resourcesFolder = Files.createTempDirectory("resources").toAbsolutePath();

    // save a POS model there
    POSModel posModel = POSTaggerMETest.trainPOSModel(ModelType.MAXENT);
    Assertions.assertNotNull(posModel);

    File posModelFile = new File(resourcesFolder.toFile(), "pos-model.bin");
    posModel.serialize(posModelFile);

    Assertions.assertTrue(posModelFile.exists());

    // load feature generator xml bytes
    try (InputStream fgInputStream = this.getClass().getResourceAsStream("ner-pos-features.xml");
         BufferedReader buffers = new BufferedReader(new InputStreamReader(fgInputStream))) {
      
      String featureGeneratorString = buffers.lines().collect(Collectors.joining("\n"));

      // create a featuregenerator file
      Path featureGenerator = Files.createTempFile("ner-featuregen", ".xml");
      Files.write(featureGenerator, featureGeneratorString.getBytes());

      Map<String, Object> resources;
      try {
        resources = TokenNameFinderTrainerTool.loadResources(resourcesFolder.toFile(),
                featureGenerator.toAbsolutePath().toFile());
      } catch (IOException e) {
        throw new TerminateToolException(-1, e.getMessage(), e);
      } finally {
        Files.delete(featureGenerator);
      }

      // train a name finder
      ObjectStream<NameSample> sampleStream = new NameSampleDataStream(
              new PlainTextByLineStream(new MockInputStreamFactory(
                      new File("opennlp/tools/namefind/voa1.train")), StandardCharsets.UTF_8));

      TrainingParameters params = new TrainingParameters();
      params.put(TrainingParameters.ITERATIONS_PARAM, 70);
      params.put(TrainingParameters.CUTOFF_PARAM, 1);

      TokenNameFinderModel nameFinderModel = NameFinderME.train("en", null, sampleStream,
              params, TokenNameFinderFactory.create(null,
                      featureGeneratorString.getBytes(), resources, new BioCodec()));

      File model = Files.createTempFile("nermodel", ".bin").toFile();
      try (FileOutputStream modelOut = new FileOutputStream(model)) {
        nameFinderModel.serialize(modelOut);
        Assertions.assertTrue(model.exists());
      } finally {
        Assertions.assertTrue(model.delete());
        FileUtil.deleteDirectory(resourcesFolder.toFile());
      }
    }
  }

  /*
   * OPENNLP-1369
   */
  @EnabledWhenCDNAvailable(hostname = "opennlp.sourceforge.net")
  @Test
  void testNERWithPOSModelV15() throws IOException, URISyntaxException {

    // 0. Download model from sourceforge and place at the right location
    final String modelName = "pt-pos-perceptron.bin";

    downloadVersion15Model(modelName);

    final Path model = OPENNLP_DIR.resolve(modelName);
    final Path resourcesFolder = Files.createTempDirectory("resources").toAbsolutePath();

    Assertions.assertNotNull(model);
    Assertions.assertNotNull(resourcesFolder);

    // 1. Copy the downloaded model to the temporary resource folder, so it can be referenced from
    // the feature gen xml file.

    final Path copy = resourcesFolder.resolve(modelName);

    Files.copy(OPENNLP_DIR.resolve(modelName), copy, StandardCopyOption.REPLACE_EXISTING);

    Assertions.assertTrue(copy.toFile().exists());

    // 2. Load feature generator xml bytes
    final URL featureGeneratorXmlUrl = this.getClass().getResource("ner-pos-features-v15.xml");
    Assertions.assertNotNull(featureGeneratorXmlUrl);

    final Path featureGeneratorXmlPath = Path.of(featureGeneratorXmlUrl.toURI());
    Assertions.assertNotNull(featureGeneratorXmlPath);

    final Path featureGenerator = Files.createTempFile("ner-featuregen-v15", ".xml");
    Assertions.assertNotNull(featureGenerator);

    Files.copy(featureGeneratorXmlPath, featureGenerator, StandardCopyOption.REPLACE_EXISTING);
    Assertions.assertTrue(featureGenerator.toFile().exists());

    Map<String, Object> resources;
    try {
      resources = TokenNameFinderTrainerTool.loadResources(resourcesFolder.toFile(),
          featureGenerator.toAbsolutePath().toFile());
    } catch (IOException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    } finally {
      Files.delete(featureGenerator);
    }


    // train a name finder
    ObjectStream<NameSample> sampleStream = new NameSampleDataStream(
        new PlainTextByLineStream(new MockInputStreamFactory(
            new File("opennlp/tools/namefind/voa1.train")), StandardCharsets.UTF_8));

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 70);
    params.put(TrainingParameters.CUTOFF_PARAM, 1);

    TokenNameFinderModel nameFinderModel = NameFinderME.train("en", null, sampleStream,
        params, TokenNameFinderFactory.create(null,
            Files.readString(featureGeneratorXmlPath, StandardCharsets.UTF_8)
                .getBytes(StandardCharsets.UTF_8), resources, new BioCodec()));


    File nerModel = Files.createTempFile("nermodel", ".bin").toFile();
    try (FileOutputStream modelOut = new FileOutputStream(nerModel)) {
      nameFinderModel.serialize(modelOut);
      Assertions.assertTrue(nerModel.exists());
    } finally {
      Assertions.assertTrue(nerModel.delete());
      FileUtil.deleteDirectory(resourcesFolder.toFile());
    }
  }
}
