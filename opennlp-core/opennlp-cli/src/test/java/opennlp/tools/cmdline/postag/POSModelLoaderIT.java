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

package opennlp.tools.cmdline.postag;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.AbstractModelLoaderTest;
import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.models.ModelType;
import opennlp.tools.postag.POSModel;
import opennlp.tools.util.DownloadUtil;

@EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
public class POSModelLoaderIT extends AbstractModelLoaderTest {

  // SUT
  private POSModelLoader loader;

  @BeforeAll
  public static void initResources() {
    SUPPORTED_LANG_CODES.forEach(lang -> {
      try {
        DownloadUtil.downloadModel(lang, ModelType.POS, POSModel.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @BeforeEach
  public void setup() {
    loader = new POSModelLoader();
  }

  @ParameterizedTest(name = "Verify \"{0}\" POS model loading")
  @ValueSource(strings = {"en-ud-ewt", "fr-ud-gsd", "de-ud-gsd", "it-ud-vit", "nl-ud-alpino",
      "bg-ud-btb", "ca-ud-ancora", "cs-ud-pdt", "da-ud-ddt", "el-ud-gdt", "es-ud-gsd", "et-ud-edt",
      "eu-ud-bdt", "fi-ud-tdt", "hr-ud-set", "hy-ud-bsut", "is-ud-icepahc", "ka-ud-glc", "kk-ud-ktb",
      "ko-ud-kaist", "lv-ud-lvtb", "no-ud-bokmaal", "pl-ud-pdb", "pt-ud-gsd", "ro-ud-rrt", "ru-ud-gsd",
      "sr-ud-set", "sk-ud-snk", "sl-ud-ssj", "sv-ud-talbanken", "tr-ud-boun", "uk-ud-iu"})
  public void testLoadModelByLanguage(String langModel) throws IOException {
    String modelName = "opennlp-" + langModel + "-pos-" + VER + BIN;
    POSModel model = loader.loadModel(Files.newInputStream(OPENNLP_DIR.resolve(modelName)));
    Assertions.assertNotNull(model);
    Assertions.assertTrue(model.isLoadedFromSerialized());
  }
}
