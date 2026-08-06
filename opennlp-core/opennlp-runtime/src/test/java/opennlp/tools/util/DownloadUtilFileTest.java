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

/**
 * Pins {@link DownloadUtil#download(java.net.URI, Path, String)} against local file URIs
 * so digest verification and the size ceiling are covered without a network.
 */
public class DownloadUtilFileTest {

  private static final byte[] PAYLOAD = "dictionary-bytes".getBytes(StandardCharsets.UTF_8);

  @Test
  void testDownloadAcceptsMatchingDigest(@TempDir Path dir) throws IOException {
    final Path source = dir.resolve("source.bin");
    Files.write(source, PAYLOAD);
    final Path target = dir.resolve("target.bin");

    DownloadUtil.download(source.toUri(), target, DigestTestUtil.sha512(PAYLOAD));

    Assertions.assertArrayEquals(PAYLOAD, Files.readAllBytes(target));
  }

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

  @Test
  void testDownloadRequiresSha512() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DownloadUtil.download(Path.of("x").toUri(), Path.of("y"), null));
  }

  @Test
  void testDownloadRejectsMalformedSha512(@TempDir Path dir) throws IOException {
    final Path source = dir.resolve("source.bin");
    Files.write(source, PAYLOAD);

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> DownloadUtil.download(source.toUri(), dir.resolve("target.bin"), "abc123"));
  }

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

  @Test
  void testDownloadCeilingIsInclusive(@TempDir Path dir) throws IOException {
    final Path source = dir.resolve("source.bin");
    Files.write(source, PAYLOAD);
    final Path target = dir.resolve("target.bin");

    DownloadUtil.download(source.toUri(), target,
        DigestTestUtil.sha512(PAYLOAD), PAYLOAD.length);

    Assertions.assertArrayEquals(PAYLOAD, Files.readAllBytes(target));
  }

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

  @Test
  void testConfiguredLimitFallsBackWhenAbsent() {
    Assertions.assertEquals(7L,
        DownloadUtil.configuredLimit("opennlp.test.limit.absent", 7L));
  }

  @Test
  void testConfiguredLimitRejectsInvalidValues() {
    final String property = "opennlp.test.limit.invalid";
    for (final String invalid : new String[] {"", "  ", "abc", "-1", "0"}) {
      System.setProperty(property, invalid);
      try {
        Assertions.assertEquals(7L, DownloadUtil.configuredLimit(property, 7L),
            "value <" + invalid + "> must fall back");
      } finally {
        System.clearProperty(property);
      }
    }
  }

  @Test
  void testDefaultBudgetsWithoutOverrides() {
    Assertions.assertEquals(512L * 1024 * 1024, DownloadUtil.MAX_DOWNLOAD_BYTES);
  }
}
