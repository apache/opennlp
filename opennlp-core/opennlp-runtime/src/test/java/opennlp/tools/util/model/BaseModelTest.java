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

package opennlp.tools.util.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkSampleStream;
import opennlp.tools.chunker.ChunkerFactory;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests {@link BaseModel#deserialize(Class, java.io.InputStream)} and the
 * {@link java.io.ObjectInputFilter} it installs.
 */
public class BaseModelTest {

  private static ChunkerModel model;

  @BeforeAll
  static void trainModel() throws IOException {
    ResourceAsStreamFactory in = new ResourceAsStreamFactory(BaseModelTest.class,
        "/opennlp/tools/chunker/test.txt");

    ObjectStream<ChunkSample> sampleStream = new ChunkSampleStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));

    TrainingParameters params = new TrainingParameters();
    params.put(Parameters.ITERATIONS_PARAM, 5);
    params.put(Parameters.CUTOFF_PARAM, 1);

    model = ChunkerME.train("eng", sampleStream, params, new ChunkerFactory());
  }

  @Test
  void testDeserializeRoundTrip() throws Exception {

    final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(bytesOut)) {
      oos.writeObject(model);
    }

    final ChunkerModel deserialized = BaseModel.deserialize(
        ChunkerModel.class, new ByteArrayInputStream(bytesOut.toByteArray()));
    Assertions.assertNotNull(deserialized);
    Assertions.assertEquals(model.getLanguage(), deserialized.getLanguage());
  }

  @Test
  void testDeserializeRejectsDisallowedTopLevelClass() throws Exception {
    final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(bytesOut)) {
      // Not on the DESERIALIZE_FILTER allowlist - simulates an attacker substituting
      // an entirely different top-level class for the expected model.
      oos.writeObject(new java.util.ArrayList<>());
    }

    Assertions.assertThrows(InvalidClassException.class, () ->
        BaseModel.deserialize(ChunkerModel.class, new ByteArrayInputStream(bytesOut.toByteArray())));
  }
}
