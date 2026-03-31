/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LoadVocabTest {

  private final AbstractDL dl = new AbstractDL() {
    @Override
    public void close() {
    }
  };

  private File getResource(String name) throws IOException {
    try (InputStream is = Objects.requireNonNull(
        getClass().getResourceAsStream("/opennlp/dl/" + name))) {
      final File tempFile = File.createTempFile("vocab-test-", "-" + name);
      tempFile.deleteOnExit();
      Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return tempFile;
    }
  }

  @Test
  void testLoadPlainTextVocab() throws IOException {
    final Map<String, Integer> vocab = dl.loadVocab(getResource("vocab-plain.txt"));

    assertNotNull(vocab);
    assertEquals(6, vocab.size());
    assertEquals(0, vocab.get("[CLS]"));
    assertEquals(1, vocab.get("[SEP]"));
    assertEquals(2, vocab.get("[UNK]"));
    assertEquals(3, vocab.get("hello"));
    assertEquals(4, vocab.get("world"));
    assertEquals(5, vocab.get("##ing"));
  }

  @Test
  void testLoadJsonVocab() throws IOException {
    final Map<String, Integer> vocab = dl.loadVocab(getResource("vocab.json"));

    assertNotNull(vocab);
    assertEquals(6, vocab.size());
    assertEquals(0, vocab.get("[CLS]"));
    assertEquals(1, vocab.get("[SEP]"));
    assertEquals(2, vocab.get("[UNK]"));
    assertEquals(3, vocab.get("hello"));
    assertEquals(4, vocab.get("world"));
    assertEquals(5, vocab.get("##ing"));
  }

  @Test
  void testJsonVocabWithEscapedCharacters() throws IOException {
    final File tempFile = File.createTempFile("vocab-escaped", ".json");
    tempFile.deleteOnExit();

    Files.writeString(tempFile.toPath(),
        "{\"hello\\\"world\": 0, \"back\\\\slash\": 1}");

    final Map<String, Integer> vocab = dl.loadVocab(tempFile);

    assertNotNull(vocab);
    assertEquals(2, vocab.size());
    assertEquals(0, vocab.get("hello\"world"));
    assertEquals(1, vocab.get("back\\slash"));
  }

  @Test
  void testJsonAndPlainTextVocabProduceSameResult() throws IOException {
    final Map<String, Integer> plainVocab = dl.loadVocab(getResource("vocab-plain.txt"));
    final Map<String, Integer> jsonVocab = dl.loadVocab(getResource("vocab.json"));

    assertEquals(plainVocab, jsonVocab);
  }
}
