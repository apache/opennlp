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
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BioNLP2004NameSampleStreamFactoryTest extends
        AbstractSampleStreamFactoryTest<NameSample, BioNLP2004NameSampleStreamFactory.Parameters> {

  private static final String SAMPLE_01 = "bionlp2004-01.sample";

  // SUT
  private BioNLP2004NameSampleStreamFactory factory;

  private String sampleFileFullPath;

  @Override
  protected AbstractSampleStreamFactory
      <NameSample, BioNLP2004NameSampleStreamFactory.Parameters> getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return sampleFileFullPath;
  }

  @BeforeAll
  static void initEnv() {
    BioNLP2004NameSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  void setUp() {
    ObjectStreamFactory<NameSample, BioNLP2004NameSampleStreamFactory.Parameters> f =
            StreamFactoryRegistry.getFactory(NameSample.class, "bionlp2004");
    assertInstanceOf(BioNLP2004NameSampleStreamFactory.class, f);
    factory = (BioNLP2004NameSampleStreamFactory) f;
    assertEquals(BioNLP2004NameSampleStreamFactory.Parameters.class, factory.params);
    sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + SAMPLE_01).getPath();
  }

  @Test
  void testCreateWithValidParameter() throws IOException {

    try (ObjectStream<NameSample> stream = factory.create(
            new String[]{"-types", "DNA,protein,cell_type,cell_line,RNA", "-data", sampleFileFullPath})) {
      NameSample sample = stream.read();
      assertNotNull(sample);
      /* some extra checks to make sure the 'types' parameter is handled correctly */
      assertEquals(5, sample.getNames().length);
      assertEquals("protein", sample.getNames()[0].getType());
      assertEquals("protein", sample.getNames()[1].getType());
      assertEquals("protein", sample.getNames()[2].getType());
      assertEquals("protein", sample.getNames()[3].getType());
      assertEquals("cell_type", sample.getNames()[4].getType());
    }
  }

  @Test
  void testCreateWithUnsupportedTypes() throws IOException {
    try (ObjectStream<NameSample> stream = factory.create(
            new String[]{"-types", "xyz", "-data", sampleFileFullPath})) {
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
          {"-types", "DNA,protein,cell_type,cell_line,RNA", "-data", sampleFileFullPath + "xyz"})) {
        NameSample sample = stream.read();
        assertNotNull(sample);
      }
    });
  }
}
