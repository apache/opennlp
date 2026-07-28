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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The teacher-reference contract of the cache, exercised without touching the network: a local
 * directory is returned as-is and anything that is neither a directory nor an {@code org/model}
 * hub id is rejected before a request is made.
 */
class HuggingFaceModelCacheTest {

  @Test
  void testNullTeacherFailsLoudly() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> HuggingFaceModelCache.resolve(null, null));
    assertTrue(e.getMessage().contains("must not be null"), e.getMessage());
  }

  @Test
  void testLocalDirectoryIsUsedAsIs(@TempDir Path teacher) {
    assertEquals(teacher, HuggingFaceModelCache.resolve(teacher.toString(), null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bge-m3", "BAAI/bge m3", "BAAI/bge-m3/onnx", "/BAAI/bge-m3",
      "BAAI/bge-m3/", "BAAI//bge-m3"})
  void testMalformedTeacherReferenceIsRejectedBeforeAnyRequest(String teacher) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> HuggingFaceModelCache.resolve(teacher, null));
    assertTrue(e.getMessage().contains("org/model"), e.getMessage());
  }

  /**
   * A local directory wins over the hub even when its path ends in something shaped like a model
   * id, so an {@code org/model} directory on disk is never downloaded over instead.
   */
  @Test
  void testALocalDirectoryShapedLikeAModelIdIsUsedAsIs(@TempDir Path root) throws IOException {
    final Path teacher = Files.createDirectories(root.resolve("BAAI").resolve("bge-m3"));

    assertEquals(teacher, HuggingFaceModelCache.resolve(teacher.toString(), null));
  }

  /** A path that exists but is a regular file is not a teacher directory. */
  @Test
  void testAnExistingRegularFileIsRejected(@TempDir Path root) throws IOException {
    final Path file = Files.writeString(root.resolve("teacher.txt"), "not a directory");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> HuggingFaceModelCache.resolve(file.toString(), null));
    assertTrue(e.getMessage().contains("org/model"), e.getMessage());
  }
}
