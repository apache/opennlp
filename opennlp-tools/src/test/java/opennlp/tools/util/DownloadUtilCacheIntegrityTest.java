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
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.chunker.ChunkerModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that models served from the local download home are checked against their
 * SHA-512 checksum, and that doing so does not require network access.
 * <p>
 * The "remote" side is a {@code file:} URL inside the test's temporary directory, so these
 * tests never contact the CDN.
 */
public class DownloadUtilCacheIntegrityTest {

  private static final String DOWNLOAD_HOME_PROPERTY = "OPENNLP_DOWNLOAD_HOME";

  private static final String MODEL_FILENAME = "opennlp-test-chunker.bin";

  /**
   * The model that gets published, downloaded, and whose checksum is the authoritative one.
   */
  private static final String PUBLISHED_MODEL = "/opennlp/tools/chunker/chunker170default.bin";

  /**
   * A different, but equally loadable, model. Used to replace a cached file so that the only
   * thing distinguishing it from the expected model is its checksum - not its parsability.
   */
  private static final String SUBSTITUTE_MODEL = "/opennlp/tools/chunker/chunker180custom.bin";

  @TempDir
  Path tempDir;

  private Path downloadHome;
  private Path remoteModel;
  private Path remoteChecksum;
  private URL modelUrl;
  private String previousDownloadHome;

  @BeforeEach
  void setUp() throws IOException {
    previousDownloadHome = System.getProperty(DOWNLOAD_HOME_PROPERTY);
    System.setProperty(DOWNLOAD_HOME_PROPERTY, tempDir.toString());
    downloadHome = tempDir.resolve(".opennlp");

    final Path remoteDir = Files.createDirectories(tempDir.resolve("remote"));
    remoteModel = remoteDir.resolve(MODEL_FILENAME);
    copyResource(PUBLISHED_MODEL, remoteModel);

    remoteChecksum = remoteDir.resolve(MODEL_FILENAME + ".sha512");
    Files.writeString(remoteChecksum, sha512(remoteModel) + "  " + MODEL_FILENAME,
        StandardCharsets.UTF_8);

    modelUrl = remoteModel.toUri().toURL();
  }

  @AfterEach
  void tearDown() {
    if (previousDownloadHome == null) {
      System.clearProperty(DOWNLOAD_HOME_PROPERTY);
    } else {
      System.setProperty(DOWNLOAD_HOME_PROPERTY, previousDownloadHome);
    }
  }

  /**
   * Sanity check: a first download validates and populates the cache, storing the published
   * checksum file next to the model so that later loads have something to check against.
   */
  @Test
  void testFirstDownloadPopulatesCache() throws IOException {
    assertNotNull(DownloadUtil.downloadModel(modelUrl, ChunkerModel.class));
    assertTrue(Files.exists(downloadHome.resolve(MODEL_FILENAME)),
        "The model should have been cached in the download home");

    final Path sidecar = downloadHome.resolve(MODEL_FILENAME + ".sha512");
    assertTrue(Files.exists(sidecar), "The published checksum should have been stored");
    assertEquals(Files.readString(remoteChecksum, StandardCharsets.UTF_8).strip(),
        Files.readString(sidecar, StandardCharsets.UTF_8).strip(),
        "The stored checksum should be interchangeable with the published one");
  }

  /**
   * The actual defect: once a model is cached, its contents are never re-checked. The cached
   * file is replaced with a <em>different but perfectly loadable</em> model, so that a passing
   * result cannot be explained by the model parser rejecting garbage.
   */
  @Test
  void testTamperedCachedModelIsRejected() throws IOException {
    assertNotNull(DownloadUtil.downloadModel(modelUrl, ChunkerModel.class));

    copyResource(SUBSTITUTE_MODEL, downloadHome.resolve(MODEL_FILENAME));

    final IOException e = assertThrows(IOException.class,
        () -> DownloadUtil.downloadModel(modelUrl, ChunkerModel.class),
        "A cached model that no longer matches its published checksum must be rejected");
    assertTrue(e.getMessage().contains("SHA512"),
        "Expected a checksum failure, but got: " + e.getMessage());
  }

  /**
   * Guards the constraint called out in OPENNLP-1902: verifying the cache must not turn every
   * cached load into a network request. Both published artifacts are removed after the first
   * download, so any attempt to reach the "remote" side would fail.
   */
  @Test
  void testValidCachedModelLoadsWithoutNetworkAccess() throws IOException {
    assertNotNull(DownloadUtil.downloadModel(modelUrl, ChunkerModel.class));

    Files.delete(remoteChecksum);
    Files.delete(remoteModel);

    assertNotNull(DownloadUtil.downloadModel(modelUrl, ChunkerModel.class),
        "A valid cached model must still load when the CDN is unreachable");
  }

  /**
   * A cache populated by an older OpenNLP release has no checksum sidecar. When the published
   * checksum is still reachable it must be used, so that pre-existing caches are covered too.
   */
  @Test
  void testLegacyCacheWithoutSidecarIsVerified() throws IOException {
    Files.createDirectories(downloadHome);
    copyResource(SUBSTITUTE_MODEL, downloadHome.resolve(MODEL_FILENAME));

    final IOException e = assertThrows(IOException.class,
        () -> DownloadUtil.downloadModel(modelUrl, ChunkerModel.class),
        "A legacy cached model must be verified against the published checksum");
    assertTrue(e.getMessage().contains("SHA512"),
        "Expected a checksum failure, but got: " + e.getMessage());
  }

  /**
   * The other half of the legacy-cache decision: when there is no sidecar and the published
   * checksum cannot be reached either, the model is loaded rather than refused, so that an
   * offline download home populated by an older release keeps working. Nothing is stored in
   * that case, so the verification is retried the next time the CDN is available.
   */
  @Test
  void testLegacyCacheIsLoadedWhenChecksumIsUnreachable() throws IOException {
    Files.createDirectories(downloadHome);
    copyResource(PUBLISHED_MODEL, downloadHome.resolve(MODEL_FILENAME));
    Files.delete(remoteChecksum);

    assertNotNull(DownloadUtil.downloadModel(modelUrl, ChunkerModel.class),
        "A legacy cached model must still load when the published checksum is unreachable");
    assertFalse(Files.exists(downloadHome.resolve(MODEL_FILENAME + ".sha512")),
        "Nothing should be stored when the published checksum could not be retrieved");
  }

  private void copyResource(String resource, Path target) throws IOException {
    try (InputStream in = DownloadUtilCacheIntegrityTest.class.getResourceAsStream(resource)) {
      assertNotNull(in, "Missing test resource: " + resource);
      Files.createDirectories(target.getParent());
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String sha512(Path file) throws IOException {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-512");
      return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-512 algorithm not found", e);
    }
  }
}
