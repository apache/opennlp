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
 * Pins {@link DownloadUtil#download(java.net.URI, Path, String)} against local file URIs
 * so digest verification and the size ceiling are covered without a network.
 */
public class DownloadUtilFileTest {

  /** The fixture bytes the download tests serve and digest. */
  private static final byte[] PAYLOAD = "dictionary-bytes".getBytes(StandardCharsets.UTF_8);

  /**
   * Verifies that a download whose bytes match the expected digest lands in the
   * target file.
   *
   * @param dir A scratch directory managed by the test framework.
   * @throws IOException Thrown if the fixture cannot be written or fetched.
   */
  @Test
  void testDownloadAcceptsMatchingDigest(@TempDir Path dir) throws IOException {
    final Path source = dir.resolve("source.bin");
    Files.write(source, PAYLOAD);
    final Path target = dir.resolve("target.bin");

    DownloadUtil.download(source.toUri(), target, DigestTestUtil.sha512(PAYLOAD));

    Assertions.assertArrayEquals(PAYLOAD, Files.readAllBytes(target));
  }

  /**
   * Verifies that a digest mismatch fails the download and leaves no target file
   * behind.
   *
   * @param dir A scratch directory managed by the test framework.
   * @throws IOException Thrown if the fixture cannot be written.
   */
  @Test
  void testDownloadRejectsMismatchedDigest(@TempDir Path dir) throws IOException {
    final Path source = dir.resolve("source.bin");
    Files.write(source, PAYLOAD);
    final Path target = dir.resolve("target.bin");
    final String wrong = DigestTestUtil.sha512("other".getBytes(StandardCharsets.UTF_8));

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> DownloadUtil.download(source.toUri(), target, wrong));
    Assertions.assertTrue(e.getMessage().contains("SHA512 checksum validation failed"));
    Assertions.assertTrue(Files.notExists(target));
  }

  /** Verifies that a {@code null} digest is rejected with the documented exception. */
  @Test
  void testDownloadRequiresSha512() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DownloadUtil.download(Path.of("x").toUri(), Path.of("y"), null));
  }

  /**
   * Verifies that a digest shorter than 128 hex digits is rejected before anything is
   * fetched.
   *
   * @param dir A scratch directory managed by the test framework.
   * @throws IOException Thrown if the fixture cannot be written.
   */
  @Test
  void testDownloadRejectsMalformedSha512(@TempDir Path dir) throws IOException {
    final Path source = dir.resolve("source.bin");
    Files.write(source, PAYLOAD);

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DownloadUtil.download(source.toUri(), dir.resolve("target.bin"), "abc123"));
  }

  /**
   * Verifies that a source larger than the byte ceiling fails the download and leaves
   * no target file behind.
   *
   * @param dir A scratch directory managed by the test framework.
   * @throws IOException Thrown if the fixture cannot be written.
   */
  @Test
  void testDownloadRejectsOversizedSource(@TempDir Path dir) throws IOException {
    final Path source = dir.resolve("source.bin");
    Files.write(source, PAYLOAD);
    final Path target = dir.resolve("target.bin");

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> DownloadUtil.download(source.toUri(), target,
            DigestTestUtil.sha512(PAYLOAD), PAYLOAD.length - 1));
    Assertions.assertTrue(e.getMessage().contains("exceeds safe limit"));
    Assertions.assertTrue(Files.notExists(target));
  }

  /**
   * Pins the inclusive byte ceiling: a source of exactly the ceiling's size still
   * downloads.
   *
   * @param dir A scratch directory managed by the test framework.
   * @throws IOException Thrown if the fixture cannot be written or fetched.
   */
  @Test
  void testDownloadCeilingIsInclusive(@TempDir Path dir) throws IOException {
    final Path source = dir.resolve("source.bin");
    Files.write(source, PAYLOAD);
    final Path target = dir.resolve("target.bin");

    DownloadUtil.download(source.toUri(), target,
        DigestTestUtil.sha512(PAYLOAD), PAYLOAD.length);

    Assertions.assertArrayEquals(PAYLOAD, Files.readAllBytes(target));
  }

  /** Verifies that a positive property value overrides the fallback limit. */
  @Test
  void testConfiguredLimitOverridesFromProperty() {
    final String property = "opennlp.test.limit.override";
    System.setProperty(property, "1024");
    try {
      Assertions.assertEquals(1024L, DownloadUtil.configuredLimit(property, 7L));
    } finally {
      System.clearProperty(property);
    }
  }

  /** Verifies that an unset property falls back to the given default. */
  @Test
  void testConfiguredLimitFallsBackWhenAbsent() {
    Assertions.assertEquals(7L,
        DownloadUtil.configuredLimit("opennlp.test.limit.absent", 7L));
  }

  /** Verifies that blank, non-numeric, and non-positive values fall back. */
  @ParameterizedTest(name = "value \"{0}\" falls back")
  @ValueSource(strings = {"", "  ", "abc", "-1", "0"})
  void testConfiguredLimitRejectsInvalidValues(String invalid) {
    final String property = "opennlp.test.limit.invalid";
    System.setProperty(property, invalid);
    try {
      Assertions.assertEquals(7L, DownloadUtil.configuredLimit(property, 7L));
    } finally {
      System.clearProperty(property);
    }
  }

  /** Pins the default download ceiling of 64 MiB when no override property is set. */
  @Test
  void testDefaultBudgetsWithoutOverrides() {
    Assertions.assertEquals(64L * 1024 * 1024, DownloadUtil.MAX_DOWNLOAD_BYTES);
  }
}
