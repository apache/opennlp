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

public class GermEval2014NameSampleStreamFactoryTest extends
    AbstractSampleStreamFactoryTest<NameSample, GermEval2014NameSampleStreamFactory.Parameters> {

  private static final String SAMPLE = "germeval2014.sample";

  // SUT
  private GermEval2014NameSampleStreamFactory factory;

  private String sampleFileFullPath;

  @Override
  protected AbstractSampleStreamFactory<NameSample,
      GermEval2014NameSampleStreamFactory.Parameters> getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return sampleFileFullPath;
  }

  @BeforeAll
  static void initEnv() {
    GermEval2014NameSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  void setUp() {
    final ObjectStreamFactory<NameSample, GermEval2014NameSampleStreamFactory.Parameters> f =
        StreamFactoryRegistry.getFactory(NameSample.class, "germeval2014");
    assertInstanceOf(GermEval2014NameSampleStreamFactory.class, f);
    factory = ((GermEval2014NameSampleStreamFactory) f);
    assertEquals(GermEval2014NameSampleStreamFactory.Parameters.class, factory.params);
    sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + SAMPLE).getPath();
  }

  @Test
  void testCreateWithValidParameter() throws IOException {
    try (final ObjectStream<NameSample> stream = factory.create(
        new String[]{"-types", "per,loc,org,misc", "-layer", "outer",
            "-data", sampleFileFullPath})) {
      final NameSample sample = stream.read();
      assertNotNull(sample);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"outer", "inner"})
  void testCreateWithDifferentLayers(final String layer) throws IOException {
    try (final ObjectStream<NameSample> stream = factory.create(
        new String[]{"-types", "per,loc,org,misc", "-layer", layer,
            "-data", sampleFileFullPath})) {
      final NameSample sample = stream.read();
      assertNotNull(sample);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "per", "loc", "org", "misc", "per,loc,org,misc"})
  void testCreateWithDifferentTypes(final String types) throws IOException {
    try (final ObjectStream<NameSample> stream = factory.create(
        new String[]{"-types", types, "-layer", "outer", "-data", sampleFileFullPath})) {
      final NameSample sample = stream.read();
      assertNotNull(sample);
    }
  }

  @Test
  void testCreateWithInvalidLayer() {
    assertThrows(TerminateToolException.class, () -> {
      try (final ObjectStream<NameSample> stream = factory.create(
          new String[]{"-types", "per,loc,org,misc", "-layer", "xyz",
              "-data", sampleFileFullPath})) {
        final NameSample sample = stream.read();
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
      try (final ObjectStream<NameSample> stream = factory.create(new String[]
          {"-types", "per,loc,org,misc", "-layer", "outer",
              "-data", sampleFileFullPath + "xyz"})) {
        final NameSample sample = stream.read();
        assertNotNull(sample);
      }
    });
  }
}
