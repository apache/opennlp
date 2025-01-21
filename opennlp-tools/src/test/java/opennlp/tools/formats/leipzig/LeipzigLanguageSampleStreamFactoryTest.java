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

package opennlp.tools.formats.leipzig;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.AbstractSampleStreamFactoryTest;
import opennlp.tools.langdetect.LanguageSample;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LeipzigLanguageSampleStreamFactoryTest extends
        AbstractSampleStreamFactoryTest<LanguageSample, LeipzigLanguageSampleStreamFactory.Parameters> {

  // SUT
  private LeipzigLanguageSampleStreamFactory factory;

  private String sampleFileFullPath;

  @Override
  protected AbstractSampleStreamFactory<LanguageSample, LeipzigLanguageSampleStreamFactory.Parameters>
      getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return sampleFileFullPath;
  }

  @BeforeAll
  static void initEnv() {
    LeipzigLanguageSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  void setUp() {
    ObjectStreamFactory<LanguageSample, LeipzigLanguageSampleStreamFactory.Parameters> f =
            StreamFactoryRegistry.getFactory(LanguageSample.class, "leipzig");
    assertInstanceOf(LeipzigLanguageSampleStreamFactory.class, f);
    factory = (LeipzigLanguageSampleStreamFactory) f;
    assertEquals(LeipzigLanguageSampleStreamFactory.Parameters.class, factory.getParameters());
    sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + "leipzig/samples").getPath();
  }

  @Test
  void testCreateWithValidParameter() throws IOException {
    try (ObjectStream<LanguageSample> stream = factory.create(new String[]
        {"-sentencesPerSample", "1","-samplesPerLanguage", "1", "-samplesToSkip", "1",
         "-sentencesDir", sampleFileFullPath})) {
      LanguageSample sample = stream.read();
      assertNotNull(sample);
    }
  }

  @Test // Given the sample Leipzig are super small, these parameters should not work!
  void testCreateWithValidParametersTooLarge() {
    assertThrows(TerminateToolException.class, () -> {
      try (ObjectStream<LanguageSample> stream = factory.create(new String[]
          {"-sentencesPerSample", "2","-samplesPerLanguage", "2", "-samplesToSkip", "1",
           "-sentencesDir", sampleFileFullPath})) {
        LanguageSample sample = stream.read();
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
      try (ObjectStream<LanguageSample> stream = factory.create(new String[]
          {"-sentencesPerSample", "2","-samplesPerLanguage", "2", "-samplesToSkip", "1",
           "-sentencesDir", sampleFileFullPath + "xyz"})) {
        LanguageSample sample = stream.read();
        assertNotNull(sample);
      }
    });
  }
}
