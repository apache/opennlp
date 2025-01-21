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

import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WordTagSampleStreamFactoryTest extends
        AbstractSampleStreamFactoryTest<POSSample, WordTagSampleStreamFactory.Parameters> {

  private static final String SAMPLE_01 = "word-tags-01.sample";
  
  // SUT
  private WordTagSampleStreamFactory factory;

  private String sampleFileFullPath;

  @Override
  protected AbstractSampleStreamFactory<POSSample, WordTagSampleStreamFactory.Parameters> getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return sampleFileFullPath;
  }

  @BeforeAll
  static void initEnv() {
    WordTagSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  void setUp() {
    ObjectStreamFactory<POSSample, WordTagSampleStreamFactory.Parameters> f =
            StreamFactoryRegistry.getFactory(POSSample.class, StreamFactoryRegistry.DEFAULT_FORMAT);
    assertInstanceOf(WordTagSampleStreamFactory.class, f);
    factory = (WordTagSampleStreamFactory) f;
    assertEquals(WordTagSampleStreamFactory.Parameters.class, factory.params);
    sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + SAMPLE_01).getPath();
  }

  @Test
  void testCreateWithValidParameter() throws IOException {
    try (ObjectStream<POSSample> stream = factory.create(
            new String[]{"-data", sampleFileFullPath})) {
      POSSample sample = stream.read();
      assertNotNull(sample);
      assertNotNull(sample.getTags());
      assertEquals(6, sample.getTags().length);
      assertNotNull(sample.getSentence());
      assertEquals(6, sample.getSentence().length);
    }
  }

}
