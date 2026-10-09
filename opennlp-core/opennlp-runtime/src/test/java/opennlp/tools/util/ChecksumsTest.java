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

package opennlp.tools.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the shared file digest helpers.
 */
public class ChecksumsTest {

  /**
   * Checks that both supported algorithms produce the lowercase hex digest of the file.
   *
   * @param dir A scratch directory managed by the test framework.
   * @throws IOException Thrown if the fixture file cannot be written or read.
   */
  @Test
  void testHexDigestMatchesTheReferenceDigest(@TempDir Path dir) throws IOException {
    final byte[] content = "payload".getBytes(StandardCharsets.UTF_8);
    final Path file = Files.write(dir.resolve("data.bin"), content);

    Assertions.assertEquals(DigestTestUtil.sha256(content).toLowerCase(),
        Checksums.hexDigest(file, Checksums.SHA_256));
    Assertions.assertEquals(DigestTestUtil.sha512(content).toLowerCase(),
        Checksums.hexDigest(file, Checksums.SHA_512));
  }

  @Test
  void testHexDigestRejectsAnUnknownAlgorithm(@TempDir Path dir) throws IOException {
    final Path file = Files.write(dir.resolve("data.bin"), new byte[0]);

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> Checksums.hexDigest(file, "NO-SUCH-DIGEST"));
    Assertions.assertEquals("NO-SUCH-DIGEST is unavailable in this runtime",
        e.getMessage());
  }

  /**
   * Checks digests of the required length in either letter case.
   *
   * @param value The candidate digest.
   */
  @ParameterizedTest
  @ValueSource(strings = {"0123456789abcdef", "0123456789ABCDEF", "aBcDeF0123456789"})
  void testIsHexDigestAcceptsHexOfTheRequiredLength(String value) {
    Assertions.assertTrue(Checksums.isHexDigest(value, 16));
  }

  /**
   * Checks values that are too short, too long, or contain a non-hex character.
   *
   * @param value The candidate digest.
   */
  @ParameterizedTest
  @ValueSource(strings = {"", "0123456789abcde", "0123456789abcdef0",
      "0123456789abcdeg", "0123456789abcde ", "0123456789abcd-f"})
  void testIsHexDigestRejectsOtherValues(String value) {
    Assertions.assertFalse(Checksums.isHexDigest(value, 16));
  }
}
