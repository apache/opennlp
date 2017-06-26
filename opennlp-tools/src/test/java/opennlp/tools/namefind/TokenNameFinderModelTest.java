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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.namefind.TokenNameFinderTrainerTool;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerMETest;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;

public class TokenNameFinderModelTest {

  @Test
  public void testNERWithPOSModel() throws IOException {

    // create a resources folder
    Path resourcesFolder = Files.createTempDirectory("resources").toAbsolutePath();

    // save a POS model there
    POSModel posModel = POSTaggerMETest.trainPOSModel(ModelType.MAXENT);
    File posModelFile = new File(resourcesFolder.toFile(),"pos-model.bin");
    FileOutputStream fos = new FileOutputStream(posModelFile);

    posModel.serialize(posModelFile);

    Assert.assertTrue(posModelFile.exists());

    // load feature generator xml bytes
    InputStream fgInputStream = this.getClass().getResourceAsStream("ner-pos-features.xml");
    BufferedReader buffers = new BufferedReader(new InputStreamReader(fgInputStream));
    String featureGeneratorString = buffers.lines().
        collect(Collectors.joining("\n"));

    // create a featuregenerator file
    Path featureGenerator = Files.createTempFile("ner-featuregen", ".xml");
    Files.write(featureGenerator, featureGeneratorString.getBytes());


    Map<String, Object> resources;
    try {
      resources = TokenNameFinderTrainerTool.loadResources(resourcesFolder.toFile(),
          featureGenerator.toAbsolutePath().toFile());
    }
    catch (IOException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    }


    // train a name finder
    ObjectStream<NameSample> sampleStream = new NameSampleDataStream(
        new PlainTextByLineStream(new MockInputStreamFactory(
            new File("opennlp/tools/namefind/voa1.train")), "UTF-8"));

    TrainingParameters params = new TrainingParameters();
    params.put(TrainingParameters.ITERATIONS_PARAM, 70);
    params.put(TrainingParameters.CUTOFF_PARAM, 1);

    TokenNameFinderModel nameFinderModel = NameFinderME.train("en", null, sampleStream,
        params, TokenNameFinderFactory.create(null,
            featureGeneratorString.getBytes(), resources, new BioCodec()));


    File model = File.createTempFile("nermodel", ".bin");
    FileOutputStream modelOut = new FileOutputStream(model);
    nameFinderModel.serialize(modelOut);

    modelOut.close();

    Assert.assertTrue(model.exists());
  }
}
