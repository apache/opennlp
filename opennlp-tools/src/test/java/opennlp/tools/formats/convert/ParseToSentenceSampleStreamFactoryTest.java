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

package opennlp.tools.formats.convert;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.AbstractSampleStreamFactoryTest;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ParseToSentenceSampleStreamFactoryTest extends
        AbstractSampleStreamFactoryTest<SentenceSample, ParseToSentenceSampleStreamFactory.Parameters> {

  private static final String DETOKENIZER_FILE = "opennlp/tools/tokenize/latin-detokenizer.xml";
  private static final String SAMPLE_01 = "parse-01.sample";

  // SUT
  private ParseToSentenceSampleStreamFactory factory;

  private String detokFileFullPath;
  private String sampleFileFullPath;

  @Override
  protected AbstractSampleStreamFactory<SentenceSample, ParseToSentenceSampleStreamFactory.Parameters>
      getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return sampleFileFullPath;
  }

  @BeforeAll
  static void initEnv() {
    ParseToSentenceSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  void setUp() {
    ObjectStreamFactory<SentenceSample, ParseToSentenceSampleStreamFactory.Parameters> f =
            StreamFactoryRegistry.getFactory(SentenceSample.class, "parse");
    assertInstanceOf(ParseToSentenceSampleStreamFactory.class, f);
    factory = (ParseToSentenceSampleStreamFactory) f;
    assertEquals(ParseToSentenceSampleStreamFactory.Parameters.class, factory.getParameters());
    sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + SAMPLE_01).getPath();
    detokFileFullPath = getResourceWithoutPrefix(DETOKENIZER_FILE).getPath();
  }

  @Test
  void testCreateWithValidParameter() throws IOException {
    try (ObjectStream<SentenceSample> stream = factory.create(new String[]
        {"-detokenizer", detokFileFullPath,"-data", sampleFileFullPath})) {
      SentenceSample sample = stream.read();
      assertNotNull(sample);
    }
  }

  /*
   * Note: Overriding this test case, as different exception is expected!
   */
  @Test
  @Override
  protected void testCreateWithInvalidDataFilePath() {
    assertThrows(TerminateToolException.class, () -> {
      try (ObjectStream<SentenceSample> stream = factory.create(new String[]
          {"-detokenizer", detokFileFullPath, "-data", sampleFileFullPath + "xyz"})) {
        SentenceSample sample = stream.read();
        assertNotNull(sample);
      }
    });
  }
}
