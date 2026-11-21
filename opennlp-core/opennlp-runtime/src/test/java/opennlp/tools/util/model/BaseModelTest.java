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
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkSampleStream;
import opennlp.tools.chunker.ChunkerFactory;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InvalidFormatException;
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

  @Test
  void testBaseModelDoesNotReferenceObjectInputFilter() throws Exception {
    // java.io.ObjectInputFilter is not available on Android, so BaseModel itself
    // must not reference it - otherwise every model fails to load there.
    // See: https://github.com/GrapheneOS/SpeechServices/issues/32
    final String bytecode;
    try (InputStream in = BaseModel.class.getResourceAsStream("BaseModel.class")) {
      Assertions.assertNotNull(in);
      bytecode = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
    }
    Assertions.assertFalse(bytecode.contains("java/io/ObjectInputFilter"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"manifest.properties", "payload.txt"})
  void testLoadRejectsAnArtifactThatInflatesFarBeyondItsArchive(String entryName)
      throws IOException {
    final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytesOut)) {
      if (!"manifest.properties".equals(entryName)) {
        zip.putNextEntry(new ZipEntry("manifest.properties"));
        zip.write("Manifest-Version=1.0\nLanguage=en\n".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
      zip.putNextEntry(new ZipEntry(entryName));
      final byte[] zeros = new byte[1024 * 1024];
      for (int i = 0; i < 32; i++) {
        zip.write(zeros);
      }
      zip.closeEntry();
    }
    final byte[] archive = bytesOut.toByteArray();

    final InvalidFormatException e = Assertions.assertThrows(InvalidFormatException.class,
        () -> new ChunkerModel(new ByteArrayInputStream(archive)));
    Assertions.assertTrue(e.getMessage().contains("compression ratio"), e.getMessage());
  }
}
