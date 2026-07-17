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
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.chunker.ChunkerModel;

/**
 * Tests {@link BaseModel#deserialize(Class, java.io.InputStream)} and the
 * {@link java.io.ObjectInputFilter} it installs.
 */
public class BaseModelTest {

  @Test
  void testDeserializeRoundTrip() throws Exception {
    ChunkerModel model = new ChunkerModel(
        this.getClass().getResourceAsStream("/opennlp/tools/chunker/chunker170default.bin"));

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
