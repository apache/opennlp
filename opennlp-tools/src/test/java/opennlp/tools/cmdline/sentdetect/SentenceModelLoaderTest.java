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

package opennlp.tools.cmdline.sentdetect;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.cmdline.AbstractModelLoaderTest;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.DownloadUtil;

@EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
public class SentenceModelLoaderTest extends AbstractModelLoaderTest {

  // SUT
  private SentenceModelLoader loader;

  @BeforeAll
  public static void initResources() {
    List<String> resources = List.of("en", "de");
    resources.forEach(lang -> {
      try {
        DownloadUtil.downloadModel(lang,
                DownloadUtil.ModelType.SENTENCE_DETECTOR, SentenceModel.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @BeforeEach
  public void setup() {
    loader = new SentenceModelLoader();
  }

  @ParameterizedTest(name = "Verify \"{0}\" sentence model loading")
  @ValueSource(strings = {"en-ud-ewt", "de-ud-gsd"})
  public void testLoadModelByLanguage(String langModel) throws IOException {
    String modelName = "opennlp-" + langModel + "-sentence-1.0-1.9.3.bin";
    SentenceModel model = loader.loadModel(Files.newInputStream(OPENNLP_DIR.resolve(modelName)));
    Assertions.assertNotNull(model);
    Assertions.assertTrue(model.isLoadedFromSerialized());
  }
}
