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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cache's teacher-reference contract and its download integrity, exercised against a hub
 * served on the loopback interface: no test here reaches the network. A local directory is
 * returned as-is, anything that is neither a directory nor an {@code org/model} hub id is rejected
 * before a request is made, and a download is pinned to one commit and accepted only when it
 * matches the digest the hub published for it.
 */
class HuggingFaceModelCacheTest {

  /** The address the test hub binds to, so that a test cannot leave the machine. */
  private static final String LOOPBACK = "127.0.0.1";

  /** The model id of the teacher the hub serves. */
  private static final String MODEL_ID = "acme/teacher";

  /** The cache directory name {@link #MODEL_ID} maps to, derived rather than restated. */
  private static final String CACHE_NAME = HuggingFaceModelCache.cacheDirectoryName(MODEL_ID);

  /**
   * {@return the cache directory name for {@link #MODEL_ID} pinned to a revision}
   *
   * @param revision The revision the reference names.
   */
  private static String cacheNameAt(String revision) {
    return HuggingFaceModelCache.cacheDirectoryName(MODEL_ID + "@" + revision);
  }

  /** The ref a teacher reference without a revision resolves. */
  private static final String DEFAULT_REF = "main";

  /** The commit {@link #DEFAULT_REF} resolves to, a sha of the shape the hub reports. */
  private static final String COMMIT = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41";

  /** A second commit, for the teacher that moved under its ref. */
  private static final String OTHER_COMMIT = "0f2b8b1d4c7e6a5938271605f4e3d2c1b0a99887";

  /** The tokenizer the hub serves, a file small enough for git to store it as a blob. */
  private static final byte[] TOKENIZER = bytes("{\"model\":{\"type\":\"WordPiece\"}}\n");

  /**
   * The git blob SHA-1 of {@link #TOKENIZER}, the 40 character form of the etag: this value comes
   * from {@code git hash-object} over the same bytes, not from the code under test.
   */
  private static final String TOKENIZER_BLOB_SHA1 = "296101682cfaaf7c2d1e2394062858aea9dd3ea5";

  /**
   * The SHA-1 of {@link #TOKENIZER}'s bytes alone, which is not how git names a blob: git hashes
   * the length and a NUL byte in front of the content.
   */
  private static final String TOKENIZER_PLAIN_SHA1 = "4d02516eda32c9ae5c590766d9e055835e0bb2c7";

  /** The ONNX graph the hub serves, large enough in reality to be stored in Git LFS. */
  private static final byte[] ONNX = bytes("ONNX GRAPH BYTES\n");

  /** The SHA-256 of {@link #ONNX}, the 64 character form of the etag, from {@code sha256sum}. */
  private static final String ONNX_SHA256 =
      "faffaa0a29c6cf303b7a0dfc59d54131b17b2658c22e02c5da3a66d7526360ef";

  /**
   * A tokenizer larger than the buffer a download is digested in, so that a digest taken from a
   * single read instead of a loop over the whole file would not match.
   */
  private static final byte[] BIG_TOKENIZER = repeated('x', 20000);

  /** The git blob SHA-1 of {@link #BIG_TOKENIZER}, from {@code git hash-object}. */
  private static final String BIG_TOKENIZER_BLOB_SHA1 =
      "7eded2aa2b98c9f0d9d4bb82c277cbbd09dcd044";

  /** An ONNX graph larger than that buffer, for the SHA-256 form. */
  private static final byte[] BIG_ONNX = repeated('y', 20000);

  /** The SHA-256 of {@link #BIG_ONNX}, from {@code sha256sum}. */
  private static final String BIG_ONNX_SHA256 =
      "fdb7f88419c3dd0053ff7c3e9db63fda5bcedf3b8a7344fc1a955a17f4423b58";

  /** The tokenizer configuration the hub serves, an optional file. */
  private static final byte[] TOKENIZER_CONFIG = bytes("{\"do_lower_case\":true}\n");

  /** The git blob SHA-1 of {@link #TOKENIZER_CONFIG}, from {@code git hash-object}. */
  private static final String TOKENIZER_CONFIG_BLOB_SHA1 =
      "67a56d358bc09865322d344d13922261a6277f26";

  /** The SentencePiece model the hub serves, an optional file. */
  private static final byte[] SENTENCEPIECE = bytes("SPM\n");

  /** The git blob SHA-1 of {@link #SENTENCEPIECE}, from {@code git hash-object}. */
  private static final String SENTENCEPIECE_BLOB_SHA1 =
      "91a9c1344fe72a78cc937f3cc515050ab1b52f20";

  /** The first of the SentencePiece file names the cache tries. */
  private static final String SENTENCEPIECE_MODEL = ModelFileNames.SENTENCEPIECE_MODELS.get(0);

  /** The HTTP status of a file a revision does not have. */
  private static final int NOT_FOUND = 404;

  private Hub hub;

  @BeforeEach
  void startHub() throws IOException {
    hub = new Hub();
  }

  @AfterEach
  void stopHub() {
    hub.close();
  }

  @Test
  void testRejectsNullTeacher() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> HuggingFaceModelCache.resolve(null, null));
    assertTrue(e.getMessage().contains("must not be null"), e.getMessage());
  }

  @Test
  void testRejectsNullHubBase(@TempDir Path cacheRoot) {
    assertEquals("hubBase must not be null", assertThrows(IllegalArgumentException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, null, cacheRoot, null)).getMessage());
  }

  @Test
  void testRejectsNullCacheRoot() {
    assertEquals("cacheRoot must not be null", assertThrows(IllegalArgumentException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), null, null)).getMessage());
  }

  @Test
  void testLocalDirectoryIsUsedAsIs(@TempDir Path teacher) throws IOException {
    assertEquals(teacher, HuggingFaceModelCache.resolve(teacher.toString(), null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bge-m3", "BAAI/bge m3", "BAAI/bge-m3/onnx", "/BAAI/bge-m3",
      "BAAI/bge-m3/", "BAAI//bge-m3", "BAAI/bge-m3@", "BAAI/bge-m3@/main",
      "BAAI/bge-m3@main/", "BAAI/bge-m3@refs//1", "BAAI/bge-m3@a b",
      "BAAI/bge-m3@main@main", "../bge-m3", "BAAI/..", "BAAI/bge-m3@..",
      "BAAI/bge-m3@refs/../main"})
  void testMalformedTeacherReferenceIsRejectedBeforeAnyRequest(String teacher,
                                                               @TempDir Path cacheRoot) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> HuggingFaceModelCache.resolve(teacher, hub.base(), cacheRoot, null));
    assertTrue(e.getMessage().contains("org/model"), e.getMessage());
    assertTrue(hub.requests.isEmpty(), hub.requests.toString());
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

  /**
   * The two digest forms the hub uses, on the two files a distillation needs: a git blob SHA-1 for
   * a file stored in git and a SHA-256 for one stored in Git LFS.
   */
  @Test
  void testDownloadsAndVerifiesBothEtagForms(@TempDir Path cacheRoot) throws IOException {
    serveTeacher();
    final List<String> progress = new ArrayList<>();

    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot,
        progress::add);

    assertEquals(cacheRoot.resolve(CACHE_NAME), cache);
    assertArrayEquals(TOKENIZER, Files.readAllBytes(cache.resolve(ModelFileNames.TOKENIZER_JSON)));
    assertArrayEquals(ONNX, Files.readAllBytes(cache.resolve(ModelFileNames.ONNX_MODEL)));
    assertEquals(COMMIT, HuggingFaceModelCache.pinnedRevision(cache));
    assertTrue(progress.stream().anyMatch(line -> line.contains(COMMIT)), progress.toString());
  }

  /** The recorded revision is what a reader of the cache directory finds, in plain text. */
  @Test
  void testTheResolvedCommitIsRecordedInTheCacheDirectory(@TempDir Path cacheRoot)
      throws IOException {
    serveTeacher();

    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertEquals(COMMIT,
        Files.readString(cache.resolve(HuggingFaceModelCache.REVISION_FILE)).trim());
  }

  /**
   * The ref is resolved once and every file is then asked for by commit sha, so that a ref moving
   * mid-download cannot mix two revisions into one cache directory.
   */
  @Test
  void testEveryFileIsRequestedAtTheResolvedCommit(@TempDir Path cacheRoot) throws IOException {
    serveTeacher();

    HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertEquals(1, hub.requests.stream().filter(p -> p.contains("/" + DEFAULT_REF + "/")).count(),
        hub.requests.toString());
    assertTrue(hub.requests.stream().filter(p -> !p.contains("/" + DEFAULT_REF + "/"))
        .allMatch(p -> p.startsWith("/" + MODEL_ID + "/resolve/" + COMMIT + "/")),
        hub.requests.toString());
  }

  @Test
  void testACorruptedBodyIsRejected(@TempDir Path cacheRoot) throws IOException {
    serveTeacher();
    hub.serve(COMMIT, ModelFileNames.ONNX_MODEL, bytes("not the graph the hub promised\n"),
        quoted(ONNX_SHA256));

    final IOException e = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains("SHA-256 checksum validation failed"), e.getMessage());
    assertTrue(e.getMessage().contains(ModelFileNames.ONNX_MODEL), e.getMessage());
    assertTrue(e.getMessage().contains(ONNX_SHA256), e.getMessage());
    assertTrue(e.getMessage().contains("but got:"), e.getMessage());
    assertNothingUsable(cacheRoot.resolve(CACHE_NAME), ModelFileNames.ONNX_MODEL);
  }

  /**
   * The 40 character etag is the git blob SHA-1, not the SHA-1 of the content, and a file that
   * only matches the latter is a file whose length git would disagree about.
   */
  @Test
  void testThePlainSha1OfTheContentIsNotAcceptedAsTheGitBlobSha1(@TempDir Path cacheRoot) {
    serveTeacher();
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_PLAIN_SHA1));

    final IOException e = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains("git blob SHA-1 checksum validation failed"),
        e.getMessage());
    assertTrue(e.getMessage().contains(TOKENIZER_BLOB_SHA1), e.getMessage());
  }

  /**
   * A download is digested by reading it in a loop, so a file longer than one of those reads is
   * digested whole, in both of the forms the hub publishes. The expected values come from
   * {@code git hash-object} and {@code sha256sum} over the same bytes.
   */
  @Test
  void testABodyLongerThanTheDigestBufferIsDigestedWhole(@TempDir Path cacheRoot)
      throws IOException {
    hub.serve(DEFAULT_REF, ModelFileNames.TOKENIZER_JSON, BIG_TOKENIZER,
        quoted(BIG_TOKENIZER_BLOB_SHA1));
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, BIG_TOKENIZER,
        quoted(BIG_TOKENIZER_BLOB_SHA1));
    hub.serve(COMMIT, ModelFileNames.ONNX_MODEL, BIG_ONNX, quoted(BIG_ONNX_SHA256));

    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertArrayEquals(BIG_TOKENIZER,
        Files.readAllBytes(cache.resolve(ModelFileNames.TOKENIZER_JSON)));
    assertArrayEquals(BIG_ONNX, Files.readAllBytes(cache.resolve(ModelFileNames.ONNX_MODEL)));
  }

  /** Hex is hex: a hub that states its digests in upper case is verified against just the same. */
  @Test
  void testAnEtagInUpperCaseIsAccepted(@TempDir Path cacheRoot) throws IOException {
    serveTeacher();
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER,
        quoted(TOKENIZER_BLOB_SHA1.toUpperCase(Locale.ROOT)));
    hub.serve(COMMIT, ModelFileNames.ONNX_MODEL, ONNX,
        quoted(ONNX_SHA256.toUpperCase(Locale.ROOT)));

    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertArrayEquals(TOKENIZER, Files.readAllBytes(cache.resolve(ModelFileNames.TOKENIZER_JSON)));
    assertArrayEquals(ONNX, Files.readAllBytes(cache.resolve(ModelFileNames.ONNX_MODEL)));
    assertEquals(COMMIT, HuggingFaceModelCache.pinnedRevision(cache));
  }

  @Test
  void testAMissingEtagIsRejected(@TempDir Path cacheRoot) throws IOException {
    serveTeacher();
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, null);

    final IOException e = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains("Expected checksum could not be retrieved"), e.getMessage());
    assertTrue(e.getMessage().contains(ModelFileNames.TOKENIZER_JSON), e.getMessage());
    assertNothingUsable(cacheRoot.resolve(CACHE_NAME), ModelFileNames.TOKENIZER_JSON);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not-a-digest", "296101682cfaaf7c2d1e2394062858aea9dd3ea",
      "296101682cfaaf7c2d1e2394062858aea9dd3ea55", "zzz101682cfaaf7c2d1e2394062858aea9dd3ea5",
      "sha256:faffaa0a29c6cf303b7a0dfc59d54131b17b2658c22e02c5da3a66d7526360ef"})
  void testAMalformedEtagIsRejected(String etag, @TempDir Path cacheRoot) {
    serveTeacher();
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(etag));

    final IOException e = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains("Expected checksum could not be retrieved"), e.getMessage());
    assertTrue(e.getMessage().contains("neither a git blob SHA-1 nor a SHA-256"), e.getMessage());
  }

  @Test
  void testAnEtagWithAnEmbeddedQuoteIsRejected(@TempDir Path cacheRoot) {
    serveTeacher();
    final int middle = TOKENIZER_BLOB_SHA1.length() / 2;
    final String malformed = TOKENIZER_BLOB_SHA1.substring(0, middle) + '"'
        + TOKENIZER_BLOB_SHA1.substring(middle);
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, malformed);

    final IOException e = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains("Expected checksum could not be retrieved"), e.getMessage());
    assertTrue(e.getMessage().contains("neither a git blob SHA-1 nor a SHA-256"), e.getMessage());
  }

  /** A file the repository does not have is absent, and one it has is downloaded and verified. */
  @Test
  void testOptionalFilesAreDownloadedWhenPresentAndAbsentOnA404(@TempDir Path cacheRoot)
      throws IOException {
    serveTeacher();
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_CONFIG, TOKENIZER_CONFIG,
        quoted(TOKENIZER_CONFIG_BLOB_SHA1));
    hub.serve(COMMIT, SENTENCEPIECE_MODEL, SENTENCEPIECE, quoted(SENTENCEPIECE_BLOB_SHA1));

    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertArrayEquals(TOKENIZER_CONFIG,
        Files.readAllBytes(cache.resolve(ModelFileNames.TOKENIZER_CONFIG)));
    assertArrayEquals(SENTENCEPIECE, Files.readAllBytes(cache.resolve(SENTENCEPIECE_MODEL)));
    // The hub was asked for the external ONNX weights and answered 404, which is not an error.
    assertTrue(hub.requests.contains(resolvePath(COMMIT, ModelFileNames.ONNX_MODEL_DATA)),
        hub.requests.toString());
    assertTrue(Files.notExists(cache.resolve(ModelFileNames.ONNX_MODEL_DATA)));
  }

  @Test
  void testAMissingRequiredFileFails(@TempDir Path cacheRoot) {
    serveTeacher();
    hub.status(COMMIT, ModelFileNames.ONNX_MODEL, NOT_FOUND);

    final IOException e = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains(ModelFileNames.ONNX_MODEL), e.getMessage());
    assertTrue(e.getMessage().contains("the distillation needs this file"), e.getMessage());
  }

  /** Only a 404 means absent; another error status must remain visible to the caller. */
  @Test
  void testAnOptionalFileServedWithAnErrorStatusIsNotTreatedAsAbsent(@TempDir Path cacheRoot) {
    serveTeacher();
    hub.status(COMMIT, ModelFileNames.TOKENIZER_CONFIG, 503);

    final IOException e = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains(ModelFileNames.TOKENIZER_CONFIG), e.getMessage());
    assertTrue(e.getMessage().contains("HTTP 503"), e.getMessage());
  }

  /**
   * The hub answers a resolve request with a redirect to a content delivery network and states the
   * commit and the digest on the redirecting response, which the client does not carry over to the
   * response it finally returns.
   */
  @Test
  void testTheHeadersOfARedirectingResponseAreUsed(@TempDir Path cacheRoot) throws IOException {
    hub.redirect(DEFAULT_REF, ModelFileNames.TOKENIZER_JSON, quoted(TOKENIZER_BLOB_SHA1),
        "/cdn/tokenizer");
    hub.redirect(COMMIT, ModelFileNames.TOKENIZER_JSON, quoted(TOKENIZER_BLOB_SHA1),
        "/cdn/tokenizer");
    hub.redirect(COMMIT, ModelFileNames.ONNX_MODEL, quoted(ONNX_SHA256), "/cdn/onnx");
    hub.reply("/cdn/tokenizer", new Reply(200, null, null, null, TOKENIZER));
    hub.reply("/cdn/onnx", new Reply(200, null, null, null, ONNX));

    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertArrayEquals(TOKENIZER, Files.readAllBytes(cache.resolve(ModelFileNames.TOKENIZER_JSON)));
    assertArrayEquals(ONNX, Files.readAllBytes(cache.resolve(ModelFileNames.ONNX_MODEL)));
    assertEquals(COMMIT, HuggingFaceModelCache.pinnedRevision(cache));
  }

  @Test
  void testRejectsCommitHeaderSuppliedOnlyByRedirectTarget(@TempDir Path cacheRoot) {
    hub.reply(resolvePath(DEFAULT_REF, ModelFileNames.TOKENIZER_JSON),
        new Reply(302, null, null, "/cdn/tokenizer", null));
    hub.reply("/cdn/tokenizer",
        new Reply(200, COMMIT, quoted(TOKENIZER_BLOB_SHA1), null, TOKENIZER));
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_BLOB_SHA1));
    hub.serve(COMMIT, ModelFileNames.ONNX_MODEL, ONNX, quoted(ONNX_SHA256));

    final IOException error = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(error.getMessage().contains("x-repo-commit"), error.getMessage());
  }

  @Test
  void testRejectsChecksumHeaderSuppliedOnlyByRedirectTarget(@TempDir Path cacheRoot) {
    hub.serve(DEFAULT_REF, ModelFileNames.TOKENIZER_JSON, TOKENIZER,
        quoted(TOKENIZER_BLOB_SHA1));
    hub.reply(resolvePath(COMMIT, ModelFileNames.TOKENIZER_JSON),
        new Reply(302, COMMIT, null, "/cdn/tokenizer", null));
    hub.reply("/cdn/tokenizer",
        new Reply(200, null, quoted(TOKENIZER_BLOB_SHA1), null, TOKENIZER));
    hub.serve(COMMIT, ModelFileNames.ONNX_MODEL, ONNX, quoted(ONNX_SHA256));

    final IOException error = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(error.getMessage().contains("x-linked-etag"), error.getMessage());
  }

  /** A complete cache directory is a usable teacher with the hub unreachable. */
  @Test
  void testACompleteCacheIsReusedWithoutContactingTheHub(@TempDir Path cacheRoot)
      throws IOException {
    serveTeacher();
    final Path first = HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);
    hub.replies.clear();
    hub.requests.clear();

    final Path second = HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertEquals(first, second);
    assertTrue(hub.requests.isEmpty(), hub.requests.toString());
    assertArrayEquals(TOKENIZER, Files.readAllBytes(second.resolve(ModelFileNames.TOKENIZER_JSON)));
  }

  /** Concurrent writers for one teacher must not publish files from different revisions. */
  @Test
  void testConcurrentResolutionsOfOneTeacherAreSerialized(@TempDir Path cacheRoot)
      throws Exception {
    serveTeacher();
    final CountDownLatch firstAtGraph = new CountDownLatch(1);
    final CountDownLatch releaseFirst = new CountDownLatch(1);
    hub.gate(COMMIT, ModelFileNames.ONNX_MODEL, firstAtGraph, releaseFirst);

    final ExecutorService executor = Executors.newFixedThreadPool(2);
    try (Hub movedHub = new Hub()) {
      movedHub.serve(DEFAULT_REF, ModelFileNames.TOKENIZER_JSON, BIG_TOKENIZER,
          quoted(BIG_TOKENIZER_BLOB_SHA1), OTHER_COMMIT);
      movedHub.serve(OTHER_COMMIT, ModelFileNames.TOKENIZER_JSON, BIG_TOKENIZER,
          quoted(BIG_TOKENIZER_BLOB_SHA1), OTHER_COMMIT);
      movedHub.serve(OTHER_COMMIT, ModelFileNames.ONNX_MODEL, BIG_ONNX,
          quoted(BIG_ONNX_SHA256), OTHER_COMMIT);
      final CountDownLatch secondReachedHub = new CountDownLatch(1);
      movedHub.signalOnRequest(DEFAULT_REF, ModelFileNames.TOKENIZER_JSON, secondReachedHub);

      final Future<Path> first = executor.submit(
          () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));
      assertTrue(firstAtGraph.await(5, TimeUnit.SECONDS), "first download did not reach the graph");
      final Future<Path> second = executor.submit(
          () -> HuggingFaceModelCache.resolve(MODEL_ID, movedHub.base(), cacheRoot, null));
      try {
        assertFalse(secondReachedHub.await(1, TimeUnit.SECONDS),
            "a second writer contacted the hub while the first held the cache");
      } finally {
        releaseFirst.countDown();
      }

      assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
    } finally {
      releaseFirst.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void testAMarkedCacheMissingAnOptionalFileIsDownloadedAgain(@TempDir Path cacheRoot)
      throws IOException {
    serveTeacher();
    hub.serve(COMMIT, SENTENCEPIECE_MODEL, SENTENCEPIECE, quoted(SENTENCEPIECE_BLOB_SHA1));
    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);
    Files.delete(cache.resolve(SENTENCEPIECE_MODEL));
    hub.requests.clear();

    HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertArrayEquals(SENTENCEPIECE, Files.readAllBytes(cache.resolve(SENTENCEPIECE_MODEL)));
    assertFalse(hub.requests.isEmpty(), "the incomplete snapshot must be downloaded again");
  }

  /** A recorded revision without the files it vouches for is not a cache directory. */
  @Test
  void testAMarkedCacheMissingItsFilesIsDownloadedAgain(@TempDir Path cacheRoot)
      throws IOException {
    serveTeacher();
    final Path cache = Files.createDirectories(cacheRoot.resolve(CACHE_NAME));
    Files.writeString(cache.resolve(HuggingFaceModelCache.REVISION_FILE), COMMIT);

    HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertArrayEquals(ONNX, Files.readAllBytes(cache.resolve(ModelFileNames.ONNX_MODEL)));
  }

  /**
   * A run that stops on a failed verification must leave nothing the next run would trust. The
   * record of the revision the directory used to hold is dropped before the first file is fetched,
   * so a retry checks what is on disk against the hub instead of handing out a directory half
   * replaced by a revision it never finished downloading.
   */
  @Test
  void testAFailedVerificationLeavesNoTrustedCacheBehind(@TempDir Path cacheRoot)
      throws IOException {
    final Path cache = Files.createDirectories(cacheRoot.resolve(CACHE_NAME));
    Files.createDirectories(cache.resolve(ModelFileNames.ONNX_MODEL).getParent());
    // A directory marked complete whose tokenizer is gone: its graph is the earlier revision's.
    Files.write(cache.resolve(ModelFileNames.ONNX_MODEL), bytes("an older revision\n"));
    Files.writeString(cache.resolve(HuggingFaceModelCache.REVISION_FILE), COMMIT + "\n");
    serveTeacher();
    hub.serve(COMMIT, ModelFileNames.ONNX_MODEL, bytes("not the graph the hub promised\n"),
        quoted(ONNX_SHA256));

    assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertNull(HuggingFaceModelCache.pinnedRevision(cache));
    hub.replies.clear();
    assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null),
        "an incomplete directory must not be returned");
  }

  /** Only a commit sha names a teacher, so a stray file cannot make a directory look pinned. */
  @Test
  void testAnUnusableRevisionFileIsNotAPin(@TempDir Path cache) throws IOException {
    assertNull(HuggingFaceModelCache.pinnedRevision(cache));

    Files.writeString(cache.resolve(HuggingFaceModelCache.REVISION_FILE), "not a commit sha");
    assertNull(HuggingFaceModelCache.pinnedRevision(cache));

    Files.writeString(cache.resolve(HuggingFaceModelCache.REVISION_FILE), COMMIT + "\n");
    assertEquals(COMMIT, HuggingFaceModelCache.pinnedRevision(cache));
  }

  /**
   * An incomplete cache has no revision marker. Each cached file is therefore checked against the
   * requested revision and reused only when its digest matches.
   */
  @Test
  void testAnUnmarkedCachedFileThatMatchesTheRevisionIsKept(@TempDir Path cacheRoot)
      throws IOException {
    serveTeacher();
    final Path cache = Files.createDirectories(cacheRoot.resolve(CACHE_NAME));
    Files.write(cache.resolve(ModelFileNames.TOKENIZER_JSON), TOKENIZER);
    // A body that would fail verification: reaching it means the cached file was not reused.
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, bytes("re-downloaded\n"),
        quoted(TOKENIZER_BLOB_SHA1));

    HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertArrayEquals(TOKENIZER, Files.readAllBytes(cache.resolve(ModelFileNames.TOKENIZER_JSON)));
  }

  @Test
  void testAnUnmarkedCachedFileFromAnotherRevisionIsReplaced(@TempDir Path cacheRoot)
      throws IOException {
    serveTeacher();
    final Path cache = Files.createDirectories(cacheRoot.resolve(CACHE_NAME));
    Files.write(cache.resolve(ModelFileNames.TOKENIZER_JSON), bytes("an older revision\n"));

    HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertArrayEquals(TOKENIZER, Files.readAllBytes(cache.resolve(ModelFileNames.TOKENIZER_JSON)));
  }

  /** A cache directory holds one revision, so a file the new one does not have has to go. */
  @Test
  void testAnOptionalFileTheRevisionDoesNotHaveIsRemovedFromTheCache(@TempDir Path cacheRoot)
      throws IOException {
    serveTeacher();
    final Path cache = Files.createDirectories(cacheRoot.resolve(CACHE_NAME));
    Files.write(cache.resolve(SENTENCEPIECE_MODEL), SENTENCEPIECE);

    HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null);

    assertTrue(Files.notExists(cache.resolve(SENTENCEPIECE_MODEL)));
  }

  /** An explicit revision is downloaded, and pinned into a cache directory of its own. */
  @Test
  void testAnExplicitRevisionIsRequestedAndCachedApart(@TempDir Path cacheRoot) throws IOException {
    hub.serve(OTHER_COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_BLOB_SHA1),
        OTHER_COMMIT);
    hub.serve(OTHER_COMMIT, ModelFileNames.ONNX_MODEL, ONNX, quoted(ONNX_SHA256), OTHER_COMMIT);

    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID + "@" + OTHER_COMMIT, hub.base(),
        cacheRoot, null);

    assertEquals(cacheRoot.resolve(cacheNameAt(OTHER_COMMIT)), cache);
    assertEquals(OTHER_COMMIT, HuggingFaceModelCache.pinnedRevision(cache));
    assertTrue(hub.requests.stream()
        .allMatch(p -> p.startsWith("/" + MODEL_ID + "/resolve/" + OTHER_COMMIT + "/")),
        hub.requests.toString());
  }

  /** A named branch or tag is a revision too, and resolves to the commit the hub reports. */
  @Test
  void testAnExplicitBranchIsResolvedToItsCommit(@TempDir Path cacheRoot) throws IOException {
    hub.serve("refs-pr-1", ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_BLOB_SHA1));
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_BLOB_SHA1));
    hub.serve(COMMIT, ModelFileNames.ONNX_MODEL, ONNX, quoted(ONNX_SHA256));

    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID + "@refs-pr-1", hub.base(),
        cacheRoot, null);

    assertEquals(COMMIT, HuggingFaceModelCache.pinnedRevision(cache));
  }

  @Test
  void testARevisionWithSlashesIsEncodedAsOnePathSegment(@TempDir Path cacheRoot)
      throws IOException {
    final String requestPath = "/" + MODEL_ID + "/resolve/refs%2Fpr%2F1/"
        + ModelFileNames.TOKENIZER_JSON;
    hub.reply(requestPath,
        new Reply(200, COMMIT, quoted(TOKENIZER_BLOB_SHA1), null, TOKENIZER));
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_BLOB_SHA1));
    hub.serve(COMMIT, ModelFileNames.ONNX_MODEL, ONNX, quoted(ONNX_SHA256));

    final Path cache = HuggingFaceModelCache.resolve(MODEL_ID + "@refs/pr/1", hub.base(),
        cacheRoot, null);

    assertEquals(COMMIT, HuggingFaceModelCache.pinnedRevision(cache));
    assertTrue(hub.requests.contains(requestPath), hub.requests.toString());
  }

  @Test
  void testARequestedCommitTheHubResolvesElsewhereIsRejected(@TempDir Path cacheRoot) {
    hub.serve(OTHER_COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_BLOB_SHA1));

    final IOException e = assertThrows(IOException.class, () -> HuggingFaceModelCache.resolve(
        MODEL_ID + "@" + OTHER_COMMIT, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains("resolved to commit " + COMMIT), e.getMessage());
  }

  /** A directory recording one commit is not the answer to a reference naming another. */
  @Test
  void testACacheRecordingAnotherCommitThanTheOneAskedForIsNotReused(@TempDir Path cacheRoot)
      throws IOException {
    final Path cache = Files.createDirectories(cacheRoot.resolve(cacheNameAt(OTHER_COMMIT)));
    Files.createDirectories(cache.resolve(ModelFileNames.ONNX_MODEL).getParent());
    Files.write(cache.resolve(ModelFileNames.TOKENIZER_JSON), TOKENIZER);
    Files.write(cache.resolve(ModelFileNames.ONNX_MODEL), ONNX);
    Files.writeString(cache.resolve(HuggingFaceModelCache.REVISION_FILE), COMMIT + "\n");
    hub.serve(OTHER_COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_BLOB_SHA1),
        OTHER_COMMIT);
    hub.serve(OTHER_COMMIT, ModelFileNames.ONNX_MODEL, ONNX, quoted(ONNX_SHA256), OTHER_COMMIT);

    final Path resolved = HuggingFaceModelCache.resolve(MODEL_ID + "@" + OTHER_COMMIT, hub.base(),
        cacheRoot, null);

    assertEquals(cache, resolved);
    assertEquals(OTHER_COMMIT, HuggingFaceModelCache.pinnedRevision(resolved));
    assertFalse(hub.requests.isEmpty(), "the hub must be asked, not the stale record believed");
  }

  @Test
  void testARevisionThatCannotBePinnedIsRejected(@TempDir Path cacheRoot) {
    hub.reply(resolvePath(DEFAULT_REF, ModelFileNames.TOKENIZER_JSON),
        new Reply(200, null, quoted(TOKENIZER_BLOB_SHA1), null, TOKENIZER));

    final IOException e = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains("could not be pinned"), e.getMessage());
    assertTrue(Files.notExists(cacheRoot.resolve(CACHE_NAME)));
  }

  @Test
  void testAModelTheHubDoesNotHaveIsRejected(@TempDir Path cacheRoot) {
    final IOException e = assertThrows(IOException.class,
        () -> HuggingFaceModelCache.resolve(MODEL_ID, hub.base(), cacheRoot, null));

    assertTrue(e.getMessage().contains("Failed to resolve revision 'main'"), e.getMessage());
  }

  /**
   * Asserts that a failed download left nothing a distillation could pick up: neither the file it
   * was verifying nor the temporary file it streamed into.
   *
   * @param cache The cache directory; need not exist.
   * @param file  The repository-relative name of the file that failed.
   * @throws IOException Thrown if the directory cannot be walked.
   */
  private void assertNothingUsable(Path cache, String file) throws IOException {
    assertTrue(Files.notExists(cache.resolve(file)), file + " must not be published");
    if (Files.isDirectory(cache)) {
      try (Stream<Path> entries = Files.walk(cache)) {
        assertFalse(entries.anyMatch(p -> p.getFileName().toString().contains(".download")),
            "a partial download must not be left behind");
      }
    }
  }

  /** Serves the ref and the two files a distillation needs, all at {@link #COMMIT}. */
  private void serveTeacher() {
    hub.serve(DEFAULT_REF, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_BLOB_SHA1));
    hub.serve(COMMIT, ModelFileNames.TOKENIZER_JSON, TOKENIZER, quoted(TOKENIZER_BLOB_SHA1));
    hub.serve(COMMIT, ModelFileNames.ONNX_MODEL, ONNX, quoted(ONNX_SHA256));
  }

  /**
   * {@return the request path of a file at a revision}
   *
   * @param revision The revision.
   * @param file     The repository-relative file name.
   */
  private static String resolvePath(String revision, String file) {
    return "/" + MODEL_ID + "/resolve/" + revision + "/" + file;
  }

  /**
   * {@return a header value in the quotes the hub puts around it}
   *
   * @param value The value.
   */
  private static String quoted(String value) {
    return "\"" + value + "\"";
  }

  /**
   * {@return the UTF-8 bytes of a fixture}
   *
   * @param content The content.
   */
  private static byte[] bytes(String content) {
    return content.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * {@return a fixture of one character repeated, long enough to outrun a single read}
   *
   * @param content The character to repeat; must be an ASCII one, so that the fixture is as many
   *                bytes long as it is characters.
   * @param length  The number of characters.
   */
  private static byte[] repeated(char content, int length) {
    return bytes(String.valueOf(content).repeat(length));
  }

  /**
   * One canned response.
   *
   * @param status   The HTTP status.
   * @param commit   The {@code x-repo-commit} header value, or {@code null} to send none.
   * @param etag     The {@code x-linked-etag} header value, or {@code null} to send none.
   * @param location The {@code Location} header value, or {@code null} to send none.
   * @param body     The response body, or {@code null} to send none.
   */
  private record Reply(int status, String commit, String etag, String location, byte[] body) {
  }

  /** Coordinates one response with a concurrent test. */
  private record Gate(CountDownLatch entered, CountDownLatch release) {
  }

  /**
   * A stand-in for the hub on the loopback interface, answering canned responses per request path
   * and recording the paths it was asked for.
   */
  private static final class Hub implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> arrivals = new ConcurrentHashMap<>();
    private final Map<String, Gate> gates = new ConcurrentHashMap<>();
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());

    private Hub() throws IOException {
      server = HttpServer.create(new InetSocketAddress(LOOPBACK, 0), 0);
      server.createContext("/", this::answer);
      server.start();
    }

    /** {@return the base URL of this hub, ending in a slash} */
    private String base() {
      return "http://" + LOOPBACK + ":" + server.getAddress().getPort() + "/";
    }

    /**
     * Serves a file at a revision, reporting {@link #COMMIT} as the commit the request resolved to.
     *
     * @param revision The revision to serve it at.
     * @param file     The repository-relative file name.
     * @param body     The response body.
     * @param etag     The {@code x-linked-etag} header value, or {@code null} to send none.
     */
    private void serve(String revision, String file, byte[] body, String etag) {
      serve(revision, file, body, etag, COMMIT);
    }

    /**
     * Serves a file at a revision.
     *
     * @param revision The revision to serve it at.
     * @param file     The repository-relative file name.
     * @param body     The response body.
     * @param etag     The {@code x-linked-etag} header value, or {@code null} to send none.
     * @param commit   The commit the request resolves to.
     */
    private void serve(String revision, String file, byte[] body, String etag, String commit) {
      reply(resolvePath(revision, file), new Reply(200, commit, etag, null, body));
    }

    /**
     * Answers a file with a redirect carrying the headers, as the hub does for a file its content
     * delivery network serves.
     *
     * @param revision The revision to serve it at.
     * @param file     The repository-relative file name.
     * @param etag     The {@code x-linked-etag} header value.
     * @param target   The path the redirect points at.
     */
    private void redirect(String revision, String file, String etag, String target) {
      reply(resolvePath(revision, file), new Reply(302, COMMIT, etag, target, null));
    }

    /**
     * Answers a file with a status and nothing else.
     *
     * @param revision The revision to serve it at.
     * @param file     The repository-relative file name.
     * @param status   The HTTP status.
     */
    private void status(String revision, String file, int status) {
      reply(resolvePath(revision, file), new Reply(status, COMMIT, null, null, null));
    }

    /**
     * Registers one canned response, replacing any response registered for the same path.
     *
     * @param path  The request path.
     * @param reply The response.
     */
    private void reply(String path, Reply reply) {
      replies.put(path, reply);
    }

    /** Records when the requested file reaches this hub. */
    private void signalOnRequest(String revision, String file, CountDownLatch arrival) {
      arrivals.put(resolvePath(revision, file), arrival);
    }

    /** Pauses the requested file until {@code release} is opened. */
    private void gate(String revision, String file, CountDownLatch entered,
                      CountDownLatch release) {
      gates.put(resolvePath(revision, file), new Gate(entered, release));
    }

    /**
     * Answers one request, with 404 when nothing is registered for its path.
     *
     * @param exchange The exchange.
     * @throws IOException Thrown if the response headers cannot be sent.
     */
    private void answer(HttpExchange exchange) throws IOException {
      final String path = exchange.getRequestURI().getRawPath();
      requests.add(path);
      final CountDownLatch arrival = arrivals.get(path);
      if (arrival != null) {
        arrival.countDown();
      }
      final Gate gate = gates.get(path);
      if (gate != null) {
        gate.entered().countDown();
        try {
          if (!gate.release().await(5, TimeUnit.SECONDS)) {
            throw new IOException("Timed out waiting to release " + path);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted while waiting to release " + path, e);
        }
      }
      final Reply reply = replies.get(path);
      if (reply == null) {
        exchange.sendResponseHeaders(NOT_FOUND, -1);
        exchange.close();
        return;
      }
      if (reply.commit() != null) {
        exchange.getResponseHeaders().add("x-repo-commit", reply.commit());
      }
      if (reply.etag() != null) {
        exchange.getResponseHeaders().add("x-linked-etag", reply.etag());
      }
      if (reply.location() != null) {
        exchange.getResponseHeaders().add("Location", reply.location());
      }
      if (reply.body() == null) {
        exchange.sendResponseHeaders(reply.status(), -1);
      } else {
        exchange.sendResponseHeaders(reply.status(), reply.body().length);
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(reply.body());
        } catch (IOException e) {
          // The client closes a body it does not need, which fails this write; that is the point
          // of the header-only requests, so it is not a test failure.
        }
      }
      exchange.close();
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
  /** Verifies that flattening reference characters does not create cache-name collisions. */
  @Test
  void testDistinctTeachersDoNotShareACacheDirectory() {
    final Set<String> names = new HashSet<>();
    for (final String teacher : List.of(
        "acme/model_v1", "acme/model.v1", "acme/model@v1", "acme/model-v1")) {
      names.add(HuggingFaceModelCache.cacheDirectoryName(teacher));
    }
    assertEquals(4, names.size(), "each distinct teacher reference needs its own directory");
  }

}
