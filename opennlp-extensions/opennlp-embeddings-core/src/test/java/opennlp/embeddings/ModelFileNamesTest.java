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
package opennlp.embeddings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The file lookup the loader and the assembler share to find a SentencePiece model under whichever
 * of its several names a teacher shipped it as: the first name that is a regular file wins, in the
 * order given.
 */
class ModelFileNamesTest {

  @Test
  void testReturnsTheFirstNameThatExists(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("spiece.model"), "second");
    Files.writeString(dir.resolve("tokenizer.model"), "third");

    assertEquals(dir.resolve("spiece.model"),
        ModelFileNames.firstRegularFile(dir, ModelFileNames.SENTENCEPIECE_MODELS));
  }

  @Test
  void testPrefersTheEarlierNameWhenSeveralExist(@TempDir Path dir) throws IOException {
    for (final String name : ModelFileNames.SENTENCEPIECE_MODELS) {
      Files.writeString(dir.resolve(name), name);
    }

    assertEquals(dir.resolve(ModelFileNames.SENTENCEPIECE_MODELS.get(0)),
        ModelFileNames.firstRegularFile(dir, ModelFileNames.SENTENCEPIECE_MODELS));
  }

  /**
   * A directory carrying one of the names is not the model file. Accepting it would hand the
   * loader a path it cannot read, one step further from the cause.
   */
  @Test
  void testSkipsADirectoryWithAMatchingName(@TempDir Path dir) throws IOException {
    Files.createDirectory(dir.resolve("sentencepiece.bpe.model"));
    Files.writeString(dir.resolve("spiece.model"), "the real one");

    assertEquals(dir.resolve("spiece.model"),
        ModelFileNames.firstRegularFile(dir, ModelFileNames.SENTENCEPIECE_MODELS));
  }

  @Test
  void testReturnsNullWhenNoNameExists(@TempDir Path dir) {
    assertNull(ModelFileNames.firstRegularFile(dir, ModelFileNames.SENTENCEPIECE_MODELS));
  }

  @Test
  void testReturnsNullForAnEmptyNameList(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("spiece.model"), "not asked for");

    assertNull(ModelFileNames.firstRegularFile(dir, List.of()));
  }

  @Test
  void testReturnsNullForADirectoryThatDoesNotExist(@TempDir Path dir) {
    assertNull(ModelFileNames.firstRegularFile(dir.resolve("missing"),
        ModelFileNames.SENTENCEPIECE_MODELS));
  }
}
