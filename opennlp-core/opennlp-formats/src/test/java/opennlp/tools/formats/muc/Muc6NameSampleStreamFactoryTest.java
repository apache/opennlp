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

package opennlp.tools.formats.muc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class Muc6NameSampleStreamFactoryTest extends
        AbstractSampleStreamFactoryTest<NameSample, Muc6NameSampleStreamFactory.Parameters> {

  private static final Path OPENNLP_DIR = Paths.get(System.getProperty("OPENNLP_DOWNLOAD_HOME",
          System.getProperty("user.home"))).resolve(".opennlp");
  private static final String TOKENIZER_MODEL_NAME = "opennlp-en-ud-ewt-tokens-1.2-2.5.0.bin";

  // SUT
  private Muc6NameSampleStreamFactory factory;

  private static String tokenizerFileFullPath;
  private String sampleFileFullPath;

  @Override
  protected AbstractSampleStreamFactory<NameSample, Muc6NameSampleStreamFactory.Parameters> getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return sampleFileFullPath;
  }

  @BeforeAll
  static void initEnv() {
    Muc6NameSampleStreamFactory.registerFactory();
    try {
      // ensure, the model is available locally for later test purposes
      DownloadUtil.downloadModel("en", ModelType.TOKENIZER, TokenizerModel.class);
    } catch (IOException e) {
      fail(e.getLocalizedMessage());
    }
    tokenizerFileFullPath = new File(OPENNLP_DIR + File.separator + TOKENIZER_MODEL_NAME).getPath();
  }

  @BeforeEach
  void setUp() {
    ObjectStreamFactory<NameSample, Muc6NameSampleStreamFactory.Parameters> f =
            StreamFactoryRegistry.getFactory(NameSample.class, "muc6");
    assertInstanceOf(Muc6NameSampleStreamFactory.class, f);
    factory = (Muc6NameSampleStreamFactory) f;
    assertEquals(Muc6NameSampleStreamFactory.Parameters.class, factory.getParameters());
    sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + "muc").getPath();
  }

  @Test
  void testCreateWithValidParameter() throws IOException {
    try (ObjectStream<NameSample> stream = factory.create(new String[]
        {"-tokenizerModel", tokenizerFileFullPath, "-data", sampleFileFullPath + File.separator})) {
      NameSample sample = stream.read();
      assertNotNull(sample);
    }
  }

  /*
   * Note: Overriding this test case, as more params are required!
   */
  @Test
  @Override
  protected void testCreateWithInvalidDataFilePath() {
    assertThrows(TerminateToolException.class, () -> {
      try (ObjectStream<NameSample> stream = factory.create(new String[]
          {"-tokenizerModel", tokenizerFileFullPath, "-data", sampleFileFullPath + "xyz" + File.separator})) {
        NameSample sample = stream.read();
        assertNotNull(sample);
      }
    });
  }

}
