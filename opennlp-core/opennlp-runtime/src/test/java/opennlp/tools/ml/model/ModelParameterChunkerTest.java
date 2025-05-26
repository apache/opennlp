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

package opennlp.tools.ml.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test cases for {@link ModelParameterChunker}.
 *
 * @author <a href="mailto:martin.wiesner@hs-heilbronn.de">Martin Wiesner</a>
 */
public class ModelParameterChunkerTest {

  private File tmp;

  @BeforeEach
  void setup() throws IOException {
    tmp = Files.createTempFile("chunker-test", ".dat").toFile();
    tmp.deleteOnExit();
  }

  @AfterEach
  void tearDown() {
    tmp = null;
  }

  /*
   * Note: 8k Integer elements will be concatenated into a flat String. The size of the resulting character
   * sequence won't hit the critical 64K limit (see: DataOutputStream#writeUTF).
   *
   * No chunking is therefore required.
   */
  @Test
  void testWriteReadUTFWithoutChunking() {
    // 8k ints -> 48042 bytes for a flat String
    testAndCheck(8192, 48042);
  }

  /*
   * Note: 16k Integer elements will be concatenated into a flat String. The size of the resulting character
   * sequence will exceed the critical 64K limit (see: DataOutputStream#writeUTF).
   *
   * Chunking is therefore required and used internally to avoid the blow up of the serialization procedure.
   *
   * When restoring the chunked String, the signature string (#SIGNATURE_CHUNKED_PARAMS) will be escaped.
   * Thus, we can assume the restored string must be equal to the artificially created original input.
   */
  @Test
  void testWriteReadUTFWithChunking() {
    // 16k ints -> 103578 bytes for a flat String
    testAndCheck(16384, 103578);
  }

  private void testAndCheck(int elementCount, int expectedByteLength) {
    String p = getParameter(elementCount);
    Assertions.assertNotNull(p);
    Assertions.assertFalse(p.trim().isEmpty());
    Assertions.assertEquals(expectedByteLength, p.getBytes(StandardCharsets.UTF_8).length);

    // TEST
    try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(tmp.toPath()))) {
      ModelParameterChunker.writeUTF(dos, p);
    } catch (IOException e) {
      Assertions.fail(e.getLocalizedMessage());
    }
    // VERIFY
    try (DataInputStream dis = new DataInputStream(Files.newInputStream(tmp.toPath()))) {
      String restoredBelow64K = ModelParameterChunker.readUTF(dis);
      // assumptions
      Assertions.assertNotNull(restoredBelow64K);
      Assertions.assertFalse(restoredBelow64K.trim().isEmpty());
      Assertions.assertEquals(p, restoredBelow64K);
      Assertions.assertEquals(expectedByteLength, p.getBytes(StandardCharsets.UTF_8).length);
    } catch (IOException e) {
      Assertions.fail(e.getLocalizedMessage());
    }
  }

  private String getParameter(int elementCount) {
    List<Integer> someParameters = new ArrayList<>(elementCount);
    for (int i = 0; i < elementCount; i++) {
      someParameters.add(i);
    }
    return someParameters.toString();
  }
}
