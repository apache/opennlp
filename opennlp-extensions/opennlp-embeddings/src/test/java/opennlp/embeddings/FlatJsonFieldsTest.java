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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatJsonFieldsTest {

  private static Path write(Path dir, String json) throws IOException {
    final Path file = dir.resolve("config.json");
    Files.writeString(file, json);
    return file;
  }

  @Test
  void testReadsTopLevelBooleans(@TempDir Path dir) throws IOException {
    final Path file = write(dir, "{\"normalize\":true,\"do_lower_case\":false}");

    assertEquals(Boolean.TRUE, FlatJsonFields.topLevelBoolean(file, "normalize"));
    assertEquals(Boolean.FALSE, FlatJsonFields.topLevelBoolean(file, "do_lower_case"));
  }

  @Test
  void testAbsentFieldAndExplicitNullBothReadAsNull(@TempDir Path dir) throws IOException {
    final Path file = write(dir, "{\"strip_accents\":null}");

    assertNull(FlatJsonFields.topLevelBoolean(file, "strip_accents"));
    assertNull(FlatJsonFields.topLevelBoolean(file, "missing"));
  }

  @Test
  void testSkipsFieldsOfEveryOtherType(@TempDir Path dir) throws IOException {
    // The shapes real tokenizer_config.json files carry around the looked-up field: nested
    // objects, arrays, floats, and strings must all be skipped structurally.
    final Path file = write(dir, "{\"added_tokens_decoder\":{\"0\":{\"special\":true}},"
        + "\"model_max_length\":1.0E9,\"architectures\":[\"StaticModel\"],"
        + "\"cls_token\":\"[CLS]\",\"normalize\":true}");

    assertEquals(Boolean.TRUE, FlatJsonFields.topLevelBoolean(file, "normalize"));
  }

  @Test
  void testNestedOccurrencesOfTheNameDoNotMatch(@TempDir Path dir) throws IOException {
    final Path file = write(dir, "{\"outer\":{\"normalize\":true}}");

    assertNull(FlatJsonFields.topLevelBoolean(file, "normalize"));
  }

  @Test
  void testToleratesAnEmptyObjectAndTrailingWhitespace(@TempDir Path dir) throws IOException {
    assertNull(FlatJsonFields.topLevelBoolean(write(dir, "{}  \n"), "normalize"));
  }

  @Test
  void testRejectsANonBooleanValue(@TempDir Path dir) throws IOException {
    final Path file = write(dir, "{\"normalize\":\"yes\"}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FlatJsonFields.topLevelBoolean(file, "normalize"));
    assertTrue(e.getMessage().contains("must be a boolean"));
  }

  @Test
  void testRejectsADuplicateField(@TempDir Path dir) throws IOException {
    final Path file = write(dir, "{\"normalize\":true,\"normalize\":false}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FlatJsonFields.topLevelBoolean(file, "normalize"));
    assertTrue(e.getMessage().contains("more than once"));
  }

  @Test
  void testRejectsMalformedJsonWithTheFileNameInTheMessage(@TempDir Path dir) throws IOException {
    final Path file = write(dir, "{\"normalize\" true}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FlatJsonFields.topLevelBoolean(file, "normalize"));
    assertTrue(e.getMessage().contains("config.json"));
  }

  @Test
  void testRejectsTrailingGarbage(@TempDir Path dir) throws IOException {
    final Path file = write(dir, "{} x");

    assertThrows(IllegalArgumentException.class,
        () -> FlatJsonFields.topLevelBoolean(file, "normalize"));
  }

  @Test
  void testMissingFileFailsAsAnIoProblem(@TempDir Path dir) {
    assertThrows(IOException.class,
        () -> FlatJsonFields.topLevelBoolean(dir.resolve("absent.json"), "normalize"));
  }
}
