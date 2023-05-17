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

package opennlp.tools.cmdline.namefind;

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
import opennlp.tools.namefind.TokenNameFinderModel;

@EnabledWhenCDNAvailable(hostname = "opennlp.sourceforge.net")
public class TokenNameFinderModelLoaderTest extends AbstractModelLoaderTest {

  // SUT
  private TokenNameFinderModelLoader loader;

  @BeforeAll
  public static void initResources() {
    List<String> resources = List.of("en");
    resources.forEach(lang -> {
      try {
        downloadVersion15Model("en-ner-location.bin");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @BeforeEach
  public void setup() {
    loader = new TokenNameFinderModelLoader();
  }

  @ParameterizedTest(name = "Verify \"{0}\" NER model loading")
  @ValueSource(strings = {"en-ner-location.bin"})
  public void testLoadModelViaResource(String modelName) throws IOException {
    TokenNameFinderModel model = loader.loadModel(Files.newInputStream(OPENNLP_DIR.resolve(modelName)));
    Assertions.assertNotNull(model);
    Assertions.assertTrue(model.isLoadedFromSerialized());
  }
}
