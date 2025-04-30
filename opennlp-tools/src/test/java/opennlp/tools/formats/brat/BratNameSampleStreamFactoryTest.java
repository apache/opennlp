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

package opennlp.tools.formats.brat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.AbstractSampleStreamFactoryTest;
import opennlp.tools.models.ModelType;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class BratNameSampleStreamFactoryTest extends
        AbstractSampleStreamFactoryTest<NameSample, BratNameSampleStreamFactory.Parameters> {

  private static final Path OPENNLP_DIR = Paths.get(System.getProperty("OPENNLP_DOWNLOAD_HOME",
          System.getProperty("user.home"))).resolve(".opennlp");
  private static final String TOKENIZER_MODEL_NAME = "opennlp-en-ud-ewt-tokens-1.2-2.5.0.bin";

  // SUT
  private BratNameSampleStreamFactory factory;

  private static String tokFileFullPath;
  private String bratFullPath;
  private String configPath;

  @Override
  protected AbstractSampleStreamFactory<NameSample, BratNameSampleStreamFactory.Parameters>
      getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return bratFullPath;
  }

  @BeforeAll
  static void initEnv() {
    BratNameSampleStreamFactory.registerFactory();
    try {
      // ensure, the model is available locally for later test purposes
      DownloadUtil.downloadModel("en", ModelType.TOKENIZER, TokenizerModel.class);
    } catch (IOException e) {
      fail(e.getLocalizedMessage());
    }
    tokFileFullPath = new File(OPENNLP_DIR + File.separator + TOKENIZER_MODEL_NAME).getPath();
  }

  @BeforeEach
  void setUp() {
    ObjectStreamFactory<NameSample, BratNameSampleStreamFactory.Parameters> f =
            StreamFactoryRegistry.getFactory(NameSample.class, "brat");
    assertInstanceOf(BratNameSampleStreamFactory.class, f);
    factory = (BratNameSampleStreamFactory) f;
    assertEquals(BratNameSampleStreamFactory.Parameters.class, factory.getParameters());
    bratFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + "brat/").getPath();
    configPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + "brat/" + "brat-ann.conf").getPath();
  }

  @ParameterizedTest
  @ValueSource(strings = {"simple", "whitespace"})
  void testCreateWithValidParameter(String tokType) throws IOException {
    try (ObjectStream<NameSample> stream = factory.create(new String[]{"-ruleBasedTokenizer", tokType,
        "-annotationConfig", configPath, "-bratDataDir", bratFullPath})) {
      NameSample sample = stream.read();
      assertNotNull(sample);
      Span[] names = sample.getNames();
      assertNotNull(names);
      assertEquals(1, names.length);
      assertEquals("Name", names[0].getType());
      assertEquals(0, names[0].getStart());
      if ("whitespace".equals(tokType)) {
        assertEquals(2, names[0].getEnd());
      } else {
        assertEquals(6, names[0].getEnd());
      }
    }
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {" ", "unknown"})
  void testCreateWithInvalidRBTokenizer(String tokType) {
    assertThrows(TerminateToolException.class, () -> {
      try (ObjectStream<NameSample> stream = factory.create(new String[]{"-ruleBasedTokenizer", tokType,
          "-annotationConfig", configPath, "-bratDataDir", bratFullPath})) {
        NameSample sample = stream.read();
        assertNotNull(sample);
      }
    });
  }

  @Test
  void testCreateWithMETokenizer() throws IOException {
    try (ObjectStream<NameSample> stream = factory.create(new String[]{"-tokenizerModel", tokFileFullPath,
        "-annotationConfig", configPath, "-bratDataDir", bratFullPath})) {
      NameSample sample = stream.read();
      assertNotNull(sample);
    }
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {" ", "unknown"})
  void testCreateWithInvalidMETokenizer(String path) {
    assertThrows(TerminateToolException.class, () -> {
      try (ObjectStream<NameSample> stream = factory.create(new String[]{"-tokenizerModel", path,
          "-annotationConfig", configPath, "-bratDataDir", bratFullPath})) {
        NameSample sample = stream.read();
        assertNotNull(sample);
      }
    });
  }

  /*
   * Note: Overriding this test case, as more params are required!
   */
  @Test
  @Override
  protected void testCreateWithInvalidDataFilePath() {
    assertThrows(TerminateToolException.class, () -> {
      try (ObjectStream<NameSample> stream = factory.create(new String[]{"-ruleBasedTokenizer", "whitespace",
          "-annotationConfig", configPath, "-bratDataDir", bratFullPath + "xyz"})) {
        NameSample sample = stream.read();
        assertNotNull(sample);
      }
    });
  }

}
