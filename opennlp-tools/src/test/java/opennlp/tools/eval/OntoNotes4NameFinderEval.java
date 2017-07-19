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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import opennlp.tools.cmdline.namefind.TokenNameFinderTrainerTool;
import opennlp.tools.formats.DirectorySampleStream;
import opennlp.tools.formats.convert.FileToStringSampleStream;
import opennlp.tools.formats.ontonotes.OntoNotesNameSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleTypeFilter;
import opennlp.tools.namefind.TokenNameFinderCrossValidator;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

public class OntoNotes4NameFinderEval extends AbstractEvalTest {

  private static ObjectStream<NameSample> createNameSampleStream() throws IOException {
    ObjectStream<File> documentStream = new DirectorySampleStream(new File(
        getOpennlpDataDir(), "ontonotes4/data/files/data/english"),
        file -> {
          if (file.isFile()) {
            return file.getName().endsWith(".name");
          }

          return file.isDirectory();
        }, true);

    return new OntoNotesNameSampleStream(new FileToStringSampleStream(
        documentStream, StandardCharsets.UTF_8));
  }

  private void crossEval(TrainingParameters params, String type, double expectedScore)
      throws IOException {
    try (ObjectStream<NameSample> samples = createNameSampleStream()) {

      TokenNameFinderCrossValidator cv = new TokenNameFinderCrossValidator("eng", null,
          params, new TokenNameFinderFactory());

      ObjectStream<NameSample> filteredSamples;
      if (type != null) {
        filteredSamples = new NameSampleTypeFilter(new String[] {type}, samples);
      }
      else {
        filteredSamples = samples;
      }

      cv.evaluate(filteredSamples, 5);

      Assert.assertEquals(expectedScore, cv.getFMeasure().getFMeasure(), 0.001d);
    }
  }

  @BeforeClass
  public static void verifyTrainingData() throws Exception {
    verifyDirectoryChecksum(new File(getOpennlpDataDir(), "ontonotes4/data/files/data/english").toPath(),
        ".name", new BigInteger("74675117716526375898817028829433420680"));
  }

  @Test
  public void evalEnglishPersonNameFinder() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put("Threads", "4");
    crossEval(params, "person", 0.822014580552418d);
  }

  @Test
  public void evalEnglishDateNameFinder() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put("Threads", "4");
    crossEval(params, "date", 0.8043873255040994d);
  }

  @Test
  public void evalAllTypesNameFinder() throws IOException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put("Threads", "4");
    crossEval(params, null, 0.8014054850253551d);
  }

  @Test
  public void evalAllTypesWithPOSNameFinder() throws IOException, URISyntaxException {
    TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
    params.put("Threads", "4");

    // load the feature generator
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = this.getClass().getResourceAsStream(
        "ner-en_pos-features.xml")) {
      byte[] buf = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0) {
        bytes.write(buf, 0, len);
      }
    }

    byte[] featureGen = bytes.toByteArray();

    // create a temp resource folder and copy the pos model there
    Path resourcesPath = Files.createTempDirectory("opennlp_resources");
    Files.copy(new File(getOpennlpDataDir(), "models-sf/en-pos-perceptron.bin").toPath(),
        new File(resourcesPath.toFile(), "en-pos-perceptron.bin").toPath(),
        StandardCopyOption.REPLACE_EXISTING);

    Map<String, Object> resources = TokenNameFinderTrainerTool.loadResources(resourcesPath.toFile(),
          Paths.get(this.getClass().getResource("ner-en_pos-features.xml").toURI()).toFile());

    try (ObjectStream<NameSample> samples = createNameSampleStream()) {

      TokenNameFinderCrossValidator cv = new TokenNameFinderCrossValidator("eng", null,
          params, featureGen, resources);

      ObjectStream<NameSample> filteredSamples;

      filteredSamples = samples;

      cv.evaluate(filteredSamples, 5);

      Assert.assertEquals(0.8070226153653437d, cv.getFMeasure().getFMeasure(), 0.001d);
    }
  }
}
