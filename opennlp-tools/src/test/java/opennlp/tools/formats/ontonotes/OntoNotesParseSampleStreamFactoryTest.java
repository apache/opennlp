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

package opennlp.tools.formats.ontonotes;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.AbstractSampleStreamFactoryTest;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OntoNotesParseSampleStreamFactoryTest extends
        AbstractSampleStreamFactoryTest<Parse, OntoNotesFormatParameters> {

  private static final String REFERENCE =
          "*PRO* Judging from the Americana in Haruki Murakami 's " +
          "`` A Wild Sheep Chase '' ( Kodansha , 320 pages , $ 18.95 *U* ) , " +
          "baby boomers on both sides of the Pacific have a lot in common . ";

  // SUT
  private OntoNotesParseSampleStreamFactory factory;

  private String sampleFileFullPath;

  @Override
  protected AbstractSampleStreamFactory<Parse, OntoNotesFormatParameters>
      getFactory() {
    return factory;
  }

  @Override
  protected String getDataFilePath() {
    return sampleFileFullPath;
  }

  @BeforeAll
  static void initEnv() {
    OntoNotesParseSampleStreamFactory.registerFactory();
  }

  @BeforeEach
  void setUp() {
    ObjectStreamFactory<Parse, OntoNotesFormatParameters> f =
            StreamFactoryRegistry.getFactory(Parse.class, "ontonotes");
    assertInstanceOf(OntoNotesParseSampleStreamFactory.class, f);
    factory = (OntoNotesParseSampleStreamFactory) f;
    assertEquals(OntoNotesFormatParameters.class, factory.getParameters());
    sampleFileFullPath = getResourceWithoutPrefix(FORMAT_SAMPLE_DIR + "ontonotes").getPath();
  }

  @Test
  void testCreateWithValidParameter() throws IOException {
    try (ObjectStream<Parse> stream = factory.create(new String[]
        {"-ontoNotesDir", sampleFileFullPath})) {
      Parse sample = stream.read();
      assertNotNull(sample);
      assertEquals(REFERENCE, sample.getText());
    }
  }

  /*
   * Note: Overriding this test case, as more params are required!
   */
  @Test
  @Override
  protected void testCreateWithInvalidDataFilePath() {
    assertThrows(TerminateToolException.class, () -> {
      try (ObjectStream<Parse> stream = factory.create(new String[]
          {"-ontoNotesDir", sampleFileFullPath + "xyz"})) {
        Parse sample = stream.read();
        assertNotNull(sample);
      }
    });
  }
}
