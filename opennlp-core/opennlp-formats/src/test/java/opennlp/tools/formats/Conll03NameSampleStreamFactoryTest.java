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

package opennlp.tools.formats;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Conll03NameSampleStreamFactoryTest extends
        AbstractSampleStreamFactoryTest<NameSample, Conll03NameSampleStreamFactory.Parameters> {

  private static final String SAMPLE_01 = "conll2003-de.sample";
  private static final String SAMPLE_02 = "conll2003-en.sample";

  // SUT
  private Conll03NameSampleStreamFactory factory;

  private String sampleFileFullPath;

  @Override
  protected AbstractSampleStreamFactory<NameSample, Conll03NameSampleStreamFactory.Parameters> getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return sampleFileFullPath;
  }

  @BeforeAll
  static void initEnv() {
    Conll03NameSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  void setUp() {
    ObjectStreamFactory<NameSample, Conll03NameSampleStreamFactory.Parameters> f =
            StreamFactoryRegistry.getFactory(NameSample.class, "conll03");
    assertInstanceOf(Conll03NameSampleStreamFactory.class, f);
    factory = ((Conll03NameSampleStreamFactory) f);
    assertEquals(Conll03NameSampleStreamFactory.Parameters.class, factory.params);
    sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + SAMPLE_01).getPath();
  }

  @ParameterizedTest
  @ValueSource(strings = {"deu", "eng"})
  void testCreateWithValidParameter(String lang) throws IOException {
    // prepare depending on language
    if ("deu".equals(lang)) {
      sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + SAMPLE_01).getPath();
    } else {
      sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + SAMPLE_02).getPath();
    }

    try (ObjectStream<NameSample> stream = factory.create(
            new String[]{"-lang", lang, "-types", "per,loc,org,misc", "-data", sampleFileFullPath})) {
      NameSample sample = stream.read();
      assertNotNull(sample);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "per", "loc", "org", "misc", "per,loc,org,misc"})
  void testCreateWithDifferentTypes(String types) throws IOException {
    try (ObjectStream<NameSample> stream = factory.create(
            new String[]{"-lang", "deu", "-types", types, "-data", sampleFileFullPath})) {
      NameSample sample = stream.read();
      assertNotNull(sample);
    }
  }

  @Test
  void testCreateWithInvalidLanguage() {
    assertThrows(TerminateToolException.class, () -> {
      try (ObjectStream<NameSample> stream = factory.create(
              new String[]{"-lang", "xyz", "-types", "per,loc,org,misc", "-data", sampleFileFullPath})) {
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
      try (ObjectStream<NameSample> stream = factory.create(new String[]
          {"-lang", "deu", "-types", "per,loc,org,misc", "-data", sampleFileFullPath + "xyz"})) {
        NameSample sample = stream.read();
        assertNotNull(sample);
      }
    });
  }
}
