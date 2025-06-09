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

package opennlp.tools.formats.conllu;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.AbstractSampleStreamFactoryTest;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConlluTokenSampleStreamFactoryTest extends
        AbstractSampleStreamFactoryTest<TokenSample, ConlluTokenSampleStreamFactory.Parameters> {

  private static final String SAMPLE_01 = "es-ud-sample.conllu";
  
  // SUT
  private ConlluTokenSampleStreamFactory factory;

  private String sampleFileFullPath;

  @Override
  protected AbstractSampleStreamFactory<TokenSample, ConlluTokenSampleStreamFactory.Parameters>
      getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return sampleFileFullPath;
  }

  @BeforeAll
  static void initEnv() {
    ConlluTokenSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  void setUp() {
    ObjectStreamFactory<TokenSample, ConlluTokenSampleStreamFactory.Parameters> f =
            StreamFactoryRegistry.getFactory(TokenSample.class, ConlluPOSSampleStreamFactory.CONLLU_FORMAT);
    assertInstanceOf(ConlluTokenSampleStreamFactory.class, f);
    factory = (ConlluTokenSampleStreamFactory) f;
    assertEquals(ConlluTokenSampleStreamFactory.Parameters.class, factory.getParameters());
    sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + "conllu/" + SAMPLE_01).getPath();
  }

  @Test
  void testCreateWithValidParameter() throws IOException {
    try (ObjectStream<TokenSample> stream = factory.create(
            new String[]{"-data", sampleFileFullPath})) {
      TokenSample sample = stream.read();
      assertNotNull(sample);
    }
  }

}
