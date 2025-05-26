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

package opennlp.uima.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.uima.resource.ResourceInitializationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.models.ModelType;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.TrainingParameters;
import opennlp.uima.AbstractTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
public class OpennlpUtilTest extends AbstractTest {

  @TempDir
  private Path tmp;

  private static SentenceModel sentModel;

  @BeforeAll
  public static void initEnv() throws IOException {
    sentModel = DownloadUtil.downloadModel(
            "en", ModelType.SENTENCE_DETECTOR, SentenceModel.class);
  }

  @Test
  void testSerialize() throws IOException {
    // prepare
    final File outModel = tmp.resolve("sent-detect-model.bin").toFile();
    outModel.deleteOnExit();
    assertFalse(outModel.exists());
    // test
    OpennlpUtil.serialize(sentModel, outModel);
    assertTrue(outModel.exists());
  }

  @Test
  void testSerializeInvalid1() {
    final File outModel = tmp.resolve("sent-detect-model.bin").toFile();
    outModel.deleteOnExit();
    assertFalse(outModel.exists());
    assertThrows(IllegalArgumentException.class, () -> OpennlpUtil.serialize(null, outModel));
  }

  @Test
  void testSerializeInvalid2() {
    assertThrows(IllegalArgumentException.class, () -> OpennlpUtil.serialize(sentModel, null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"opennlp-en-ud-ewt-sentence-1.2-2.5.0.bin"})
  void testLoadBytes(String file) {
    try {
      byte[] data = OpennlpUtil.loadBytes(OPENNLP_DIR.resolve(file).toFile());
      assertNotNull(data);
      assertTrue(data.length > 0);
    } catch (IOException e) {
      fail(e.getLocalizedMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testLoadTrainingParams(boolean seqTrainingAllowed) {
    final String trainingParamsFile = "training-params-test.conf";
    final String trainingParamsPath = Path.of(TARGET_DIR).resolve(trainingParamsFile).
            toAbsolutePath().toString();
    try {
      TrainingParameters params = OpennlpUtil.loadTrainingParams(trainingParamsPath, seqTrainingAllowed);
      assertNotNull(params);
      assertEquals("MAXENT", params.getStringParameter("Algorithm", "?"));
      assertEquals(150, params.getIntParameter("Iterations", 1));
      assertEquals(5, params.getIntParameter("Cutoff", 1));
      assertEquals(4, params.getIntParameter("Threads", 1));
    } catch (ResourceInitializationException e) {
      fail(e.getCause().getLocalizedMessage());
    }
  }

  @Test
  void testLoadTrainingParamsWithInvalidFileContent() {
    final String trainingParamsFile = "training-params-invalid.conf";
    final String trainingParamsPath = Path.of(TARGET_DIR).resolve(trainingParamsFile).
            toAbsolutePath().toString();
    assertThrows(ResourceInitializationException.class, () ->
            OpennlpUtil.loadTrainingParams(trainingParamsPath, false));
  }

  @Test
  void testLoadTrainingParamsNullYieldsDefaultParams() {
    try {
      TrainingParameters params = OpennlpUtil.loadTrainingParams(null, true);
      assertNotNull(params);
      assertEquals("MAXENT", params.getStringParameter("Algorithm", "?"));
      assertEquals(100, params.getIntParameter("Iterations", 1));
      assertEquals(5, params.getIntParameter("Cutoff", 1));
      assertEquals(1, params.getIntParameter("Threads", 1));
    } catch (ResourceInitializationException e) {
      fail(e.getCause().getLocalizedMessage());
    }
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {" ", "\t", "\n"})
  void testLoadTrainingParamsInvalid(String fileName) {
    assertThrows(ResourceInitializationException.class, () ->
            OpennlpUtil.loadTrainingParams(fileName, false));
  }

}
