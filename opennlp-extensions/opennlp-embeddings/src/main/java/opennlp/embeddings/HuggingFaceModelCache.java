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
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches the files a distillation needs from a Hugging Face model repository into a local cache
 * directory, so a teacher can be named by its hub id ({@code org/model}) instead of a local path.
 *
 * <p>A download is pinned and verified. The teacher's ref is resolved to a commit sha once, every
 * file is then requested at that sha, and every file is checked against the digest the hub
 * publishes for it before it is published into the cache directory. A file the hub does not
 * publish a digest for is refused rather than used, the same posture
 * {@link opennlp.tools.util.DownloadUtil} takes for a model whose {@code .sha512} sidecar cannot
 * be read. A file the repository does not have (a 404, e.g. a WordPiece teacher's
 * {@code sentencepiece.bpe.model}) is reported as absent, not an error, but only when that file is
 * optional.</p>
 *
 * <p>The resolved commit sha is recorded in {@value #REVISION_FILE} in the cache directory, which
 * also marks the directory as a complete snapshot of that one revision: while it is there, the
 * files are reused without a request to the hub and without being digested again. The record is
 * written only once every file has been verified, and re-reading a multi-gigabyte ONNX graph on
 * every distillation would only guard against something that could rewrite the record just as
 * easily. A directory without the record is not trusted: its files are checked against the
 * revision now being downloaded before they are kept. A record that does not describe the
 * directory, because a file it vouches for is gone or because it names a different commit than the
 * reference asks for, is removed before anything is downloaded, so that a download failing halfway
 * cannot leave a directory the next attempt would trust on the strength of it.</p>
 */
final class HuggingFaceModelCache {

  /** The hub's base URL, the prefix of every download URL. */
  private static final String HUB_BASE = "https://huggingface.co/";

  /** The hub's download path between the model id and the revision. */
  private static final String RESOLVE_PATH = "/resolve/";

  /** The revision downloaded when a teacher does not name one: the repository's default branch. */
  private static final String DEFAULT_REVISION = "main";

  /**
   * A teacher reference: an organization and a model name, both of word characters, dots, or
   * dashes, optionally followed by {@code @} and the revision to pin.
   */
  private static final Pattern TEACHER_PATTERN = Pattern.compile("([\\w.-]+/[\\w.-]+)(?:@([\\w.-]+))?");

  /** The directory the cache lives in, below the user's home directory. */
  private static final String CACHE_DIRECTORY = ".cache";

  /** The cache's own directory, below {@link #CACHE_DIRECTORY}. */
  private static final String CACHE_NAME = "opennlp-embeddings";

  /** The hex length of the digest suffix that makes a cache directory name injective. */
  private static final int CACHE_KEY_HEX_LENGTH = 16;

  /**
   * The file recording the commit sha the cache directory holds. It is written only after every
   * file of that revision has been downloaded and verified, so its presence means the directory is
   * complete. The name starts with a dot so that it cannot collide with a repository file.
   */
  static final String REVISION_FILE = ".opennlp-revision";

  /** The suffix of the temporary file a download streams into before it is moved into place. */
  private static final String DOWNLOAD_SUFFIX = ".download";

  /** The response header holding the commit sha a ref resolved to. */
  private static final String COMMIT_HEADER = "x-repo-commit";

  /** The response header holding the digest of the file, quoted. */
  private static final String ETAG_HEADER = "x-linked-etag";

  /** The length in hex of a SHA-1: the shape of a commit sha and of a git object name. */
  private static final int SHA1_HEX_LENGTH = 40;

  /** The length in hex of a SHA-256: the shape of the digest published for a Git LFS file. */
  private static final int SHA256_HEX_LENGTH = 64;

  /** A hex string of any length, the shape both the commit sha and the digests have. */
  private static final Pattern HEX_PATTERN = Pattern.compile("[0-9a-fA-F]+");

  /** The header git hashes in front of a blob's bytes, completed by the length and a NUL byte. */
  private static final String GIT_BLOB_PREFIX = "blob ";

  /** The read size when digesting a downloaded file. */
  private static final int DIGEST_BUFFER_SIZE = 8192;

  /** The HTTP status a served file answers with. */
  private static final int HTTP_OK = 200;

  /** The HTTP status of a file the repository does not have at the requested revision. */
  private static final int HTTP_NOT_FOUND = 404;

  /** How long the client waits for a connection to the hub. */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

  /** How long a single file download may take; an ONNX graph can be gigabytes. */
  private static final Duration DOWNLOAD_TIMEOUT = Duration.ofHours(1);

  /** The files a distillation needs, relative to the repository root. */
  private static final List<String> REQUIRED_FILES =
      List.of(ModelFileNames.TOKENIZER_JSON, ModelFileNames.ONNX_MODEL);

  /**
   * The files used when present: the pad-token config, the trained SentencePiece model under any
   * of the names a repository may ship it as, and the external weights of an ONNX export that
   * splits them out (as bge-m3 does).
   */
  private static final List<String> OPTIONAL_FILES = optionalFiles();

  /** Not instantiable. */
  private HuggingFaceModelCache() {
  }

  /** {@return the repository-relative names of the files downloaded when the repository has them} */
  private static List<String> optionalFiles() {
    final List<String> files = new ArrayList<>();
    files.add(ModelFileNames.TOKENIZER_CONFIG);
    files.addAll(ModelFileNames.SENTENCEPIECE_MODELS);
    files.add(ModelFileNames.ONNX_MODEL_DATA);
    return List.copyOf(files);
  }

  /**
   * Resolves a teacher reference to a local directory holding its files, downloading them from the
   * Hugging Face hub when the reference is a model id.
   *
   * @param teacher  A local directory, used as-is, or a Hugging Face model id ({@code org/model},
   *                 or {@code org/model@revision} to pin a branch, tag, or commit sha instead of
   *                 the default branch), downloaded into
   *                 {@code ~/.cache/opennlp-embeddings/org-model} on first use (the slash becomes
   *                 a dash, dots and the revision separator become underscores). Must not be
   *                 {@code null}.
   * @param listener Receives one progress line per download; may be {@code null}.
   * @return The local teacher directory.
   * @throws IllegalArgumentException Thrown if {@code teacher} is {@code null}, or is neither a
   *     directory nor a well-formed model id.
   * @throws IOException Thrown if a required file cannot be downloaded, or if a downloaded file
   *     cannot be verified against the digest the hub publishes for it.
   */
  static Path resolve(String teacher, ModelDistiller.ProgressListener listener) throws IOException {
    return resolve(teacher, HUB_BASE, defaultCacheRoot(), listener);
  }

  /**
   * Resolves a teacher reference against a given hub and cache location, the form used by the
   * tests and by an installation that mirrors the hub.
   *
   * @param teacher   The teacher reference, as in {@link #resolve(String,
   *                  ModelDistiller.ProgressListener)}. Must not be {@code null}.
   * @param hubBase   The hub's base URL, ending in a slash. Must not be {@code null}.
   * @param cacheRoot The directory the per-teacher cache directories live in. Must not be
   *                  {@code null}.
   * @param listener  Receives one progress line per download; may be {@code null}.
   * @return The local teacher directory.
   * @throws IllegalArgumentException Thrown if {@code teacher}, {@code hubBase}, or
   *     {@code cacheRoot} is {@code null}, or if {@code teacher} is neither a directory nor a
   *     well-formed model id.
   * @throws IOException Thrown if a required file cannot be downloaded, or if a downloaded file
   *     cannot be verified against the digest the hub publishes for it.
   */
  static Path resolve(String teacher, String hubBase, Path cacheRoot,
                      ModelDistiller.ProgressListener listener) throws IOException {
    if (teacher == null) {
      throw new IllegalArgumentException("Teacher must not be null");
    }
    if (hubBase == null) {
      throw new IllegalArgumentException("HubBase must not be null");
    }
    if (cacheRoot == null) {
      throw new IllegalArgumentException("CacheRoot must not be null");
    }
    final Path local = Path.of(teacher);
    if (Files.isDirectory(local)) {
      return local;
    }
    final Matcher reference = TEACHER_PATTERN.matcher(teacher);
    if (!reference.matches()) {
      throw new IllegalArgumentException("Teacher '" + teacher + "' is neither a local "
          + "directory nor a Hugging Face model id (expected 'org/model' or 'org/model@revision')");
    }
    final String modelId = reference.group(1);
    final String requestedRevision = reference.group(2);
    final Path cache = cacheRoot.resolve(cacheDirectoryName(teacher));
    final String pinned = pinnedRevision(cache);
    if (pinned != null && hasRequiredFiles(cache)
        && (!isCommitSha(requestedRevision) || pinned.equalsIgnoreCase(requestedRevision))) {
      return cache;
    }
    // The directory is not a complete snapshot of a revision this reference names, so the record
    // it carries does not describe it either. The record goes before the first file is fetched:
    // a download that then fails verification leaves a directory the next attempt distrusts and
    // verifies file by file, rather than one it would hand out whole on the strength of a record
    // written for an earlier revision.
    Files.deleteIfExists(cache.resolve(REVISION_FILE));
    final String ref = requestedRevision == null ? DEFAULT_REVISION : requestedRevision;
    // A client built through the builder has no proxy selector unless one is set, so the
    // http.proxyHost / https.proxyHost system properties would otherwise be ignored.
    final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .proxy(ProxySelector.getDefault())
        .connectTimeout(CONNECT_TIMEOUT)
        .build();
    final String commit = resolveCommit(client, hubBase, modelId, ref, requestedRevision);
    report(listener, "Teacher " + modelId + " at " + ref + " is commit " + commit);
    for (final String file : REQUIRED_FILES) {
      download(client, hubBase, modelId, commit, file, cache, true, listener);
    }
    for (final String file : OPTIONAL_FILES) {
      download(client, hubBase, modelId, commit, file, cache, false, listener);
    }
    Files.writeString(cache.resolve(REVISION_FILE), commit + System.lineSeparator(),
        StandardCharsets.UTF_8);
    return cache;
  }

  /**
   * {@return the commit sha a cache directory was downloaded at, or {@code null} when the
   * directory is not a complete cached snapshot of a hub revision}
   *
   * @param teacherDirectory The directory to read; need not exist.
   */
  static String pinnedRevision(Path teacherDirectory) {
    final Path file = teacherDirectory.resolve(REVISION_FILE);
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      final String recorded = Files.readString(file, StandardCharsets.UTF_8).trim();
      return isCommitSha(recorded) ? recorded : null;
    } catch (IOException e) {
      return null;
    }
  }

  /** {@return the directory the per-teacher cache directories live in} */
  private static Path defaultCacheRoot() {
    return Path.of(System.getProperty("user.home"), CACHE_DIRECTORY, CACHE_NAME);
  }

  /**
   * {@return the cache directory name for a teacher reference}
   *
   * <p>The readable part replaces the characters a path cannot carry, which alone is not
   * injective: {@code acme/model@v1}, {@code acme/model.v1} and {@code acme/model_v1} would all
   * name one directory, and the cached fast path answers from that directory without contacting
   * the hub, so one teacher would be served another's files. The suffix is a digest of the exact
   * reference, so distinct references never share a directory.</p>
   *
   * @param teacher The teacher reference, as the caller wrote it.
   */
  static String cacheDirectoryName(String teacher) {
    final String readable = teacher.replace('/', '-').replace('.', '_').replace('@', '_');
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required of every JVM", e);
    }
    final byte[] hash = digest.digest(teacher.getBytes(StandardCharsets.UTF_8));
    final String suffix = HexFormat.of().formatHex(hash, 0, CACHE_KEY_HEX_LENGTH / 2);
    return readable + '-' + suffix;
  }

  /**
   * {@return whether every file a distillation needs is in the cache directory}
   *
   * @param cache The cache directory.
   */
  private static boolean hasRequiredFiles(Path cache) {
    for (final String file : REQUIRED_FILES) {
      if (!Files.isRegularFile(cache.resolve(file))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Resolves a ref to the commit sha it points at, so that the files of one download all come from
   * one revision even if the ref moves while the download runs. The hub reports the sha on every
   * resolve response, so the body of the probed file is not read.
   *
   * @param client            The HTTP client.
   * @param hubBase           The hub's base URL.
   * @param modelId           The hub model id.
   * @param ref               The revision to resolve.
   * @param requestedRevision The revision the teacher reference named, or {@code null} when it
   *                          named none.
   * @return The commit sha, 40 hex characters.
   * @throws IOException Thrown if the ref cannot be resolved.
   */
  private static String resolveCommit(HttpClient client, String hubBase, String modelId, String ref,
                                      String requestedRevision) throws IOException {
    final String probe = REQUIRED_FILES.get(0);
    final HttpResponse<InputStream> response = send(client, hubBase, modelId, ref, probe);
    // The headers carry everything this probe wants, so the body is closed unread.
    final InputStream body = response.body();
    try (body) {
      if (response.statusCode() != HTTP_OK) {
        throw new IOException("Failed to resolve revision '" + ref + "' of " + modelId + ": HTTP "
            + response.statusCode() + " for " + probe);
      }
      final String commit = originHeader(response, COMMIT_HEADER);
      if (!isCommitSha(commit)) {
        throw new IOException("Revision '" + ref + "' of " + modelId + " could not be pinned: the "
            + "hub sent " + (commit == null ? "no " + COMMIT_HEADER + " header"
                : COMMIT_HEADER + " '" + commit + "', which is not a commit sha")
            + "; refusing to download files that cannot be attributed to one revision");
      }
      if (isCommitSha(requestedRevision) && !commit.equalsIgnoreCase(requestedRevision)) {
        throw new IOException("Revision '" + requestedRevision + "' of " + modelId + " resolved to "
            + "commit " + commit + " instead");
      }
      return commit;
    }
  }

  /**
   * Downloads one repository file at a pinned revision into the cache, keeping a copy that is
   * already there when it matches the revision's digest.
   *
   * @param client   The HTTP client.
   * @param hubBase  The hub's base URL.
   * @param modelId  The hub model id.
   * @param commit   The commit sha every file of this download is requested at.
   * @param file     The repository-relative file name.
   * @param cache    The cache directory.
   * @param required Whether a file the revision does not have is an error.
   * @param listener The progress listener; may be {@code null}.
   * @throws IOException Thrown if a required file cannot be downloaded, or if the download cannot
   *     be verified against the digest the hub publishes for it.
   */
  private static void download(HttpClient client, String hubBase, String modelId, String commit,
                               String file, Path cache, boolean required,
                               ModelDistiller.ProgressListener listener) throws IOException {
    final Path target = cache.resolve(file);
    final HttpResponse<InputStream> response = send(client, hubBase, modelId, commit, file);
    Path temporary = null;
    try (InputStream body = response.body()) {
      if (response.statusCode() == HTTP_NOT_FOUND && !required) {
        // The cache directory holds one revision: a copy left by an earlier one has to go.
        Files.deleteIfExists(target);
        return;
      }
      if (response.statusCode() != HTTP_OK) {
        throw new IOException("Failed to download " + file + " of " + modelId + " at commit "
            + commit + ": HTTP " + response.statusCode()
            + (required ? "; the distillation needs this file" : ""));
      }
      final Digest expected = expectedDigest(response, modelId, file);
      if (Files.isRegularFile(target) && expected.matches(target)) {
        return;
      }
      report(listener, "Downloading " + modelId + "/" + file + " ...");
      Files.createDirectories(target.getParent());
      // A temporary name unique per download: two processes sharing one cache directory must not
      // stream two copies of the same file into one partial file and publish the interleaving.
      temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(),
          DOWNLOAD_SUFFIX);
      Files.copy(body, temporary, StandardCopyOption.REPLACE_EXISTING);
      final String actual = expected.form().hexOf(temporary);
      if (!expected.hex().equalsIgnoreCase(actual)) {
        throw new IOException(expected.form().displayName() + " checksum validation failed for "
            + file + " of " + modelId + " at commit " + commit + ". Expected: " + expected.hex()
            + ", but got: " + actual);
      }
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      temporary = null;
    } finally {
      deleteIfPresent(temporary);
    }
  }

  /**
   * Sends one GET to the hub.
   *
   * @param client   The HTTP client.
   * @param hubBase  The hub's base URL.
   * @param modelId  The hub model id.
   * @param revision The revision to request the file at.
   * @param file     The repository-relative file name.
   * @return The response, whose body has not been read yet.
   * @throws IOException Thrown if the request fails.
   */
  private static HttpResponse<InputStream> send(HttpClient client, String hubBase, String modelId,
                                                String revision, String file) throws IOException {
    final HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(hubBase + modelId + RESOLVE_PATH + revision + "/" + file))
        .timeout(DOWNLOAD_TIMEOUT)
        .GET()
        .build();
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException e) {
      throw new IOException("Failed to download " + file + " of " + modelId + ": "
          + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while downloading " + file + " of " + modelId, e);
    }
  }

  /**
   * Reads the digest the hub publishes for a file, refusing a file that carries none.
   *
   * @param response The response.
   * @param modelId  The hub model id, for the message.
   * @param file     The repository-relative file name, for the message.
   * @return The expected digest, either a git blob SHA-1 or a SHA-256.
   * @throws IOException Thrown if the header is absent or is not one of the two digest forms.
   */
  private static Digest expectedDigest(HttpResponse<InputStream> response, String modelId,
                                       String file) throws IOException {
    final String header = originHeader(response, ETAG_HEADER);
    if (header == null) {
      throw new IOException("Expected checksum could not be retrieved for " + file + " of "
          + modelId + ": the hub sent no " + ETAG_HEADER + " header; refusing to use a file that "
          + "cannot be verified");
    }
    final String hex = header.trim().replace("\"", "");
    final Checksum form = Checksum.of(hex);
    if (form == null) {
      throw new IOException("Expected checksum could not be retrieved for " + file + " of "
          + modelId + ": " + ETAG_HEADER + " '" + header + "' is neither a git blob SHA-1 nor a "
          + "SHA-256; refusing to use a file that cannot be verified");
    }
    return new Digest(form, hex);
  }

  /**
   * {@return the value the hub sent for a header, or {@code null} when it sent none}
   *
   * <p>A resolve request answers with a redirect to a content delivery network, and the client
   * does not copy the headers of that redirecting response onto the response it finally returns,
   * so the redirect chain is walked back to its start. The earliest response wins: the digest to
   * verify against is the one the hub itself stated, not one a later hop restated.</p>
   *
   * @param response The response, at the end of its redirect chain.
   * @param name     The header name.
   */
  private static String originHeader(HttpResponse<InputStream> response, String name) {
    final List<HttpResponse<InputStream>> chain = new ArrayList<>();
    for (HttpResponse<InputStream> hop = response; hop != null;
         hop = hop.previousResponse().orElse(null)) {
      chain.add(hop);
    }
    for (int i = chain.size() - 1; i >= 0; i--) {
      final Optional<String> value = chain.get(i).headers().firstValue(name);
      if (value.isPresent()) {
        return value.get();
      }
    }
    return null;
  }

  /**
   * {@return whether a value is a commit sha, 40 hex characters}
   *
   * @param value The value to check; may be {@code null}.
   */
  private static boolean isCommitSha(String value) {
    return value != null && value.length() == SHA1_HEX_LENGTH
        && HEX_PATTERN.matcher(value).matches();
  }

  /**
   * Reports one progress line, if anyone is listening.
   *
   * @param listener The listener; may be {@code null}.
   * @param message  The message.
   */
  private static void report(ModelDistiller.ProgressListener listener, String message) {
    if (listener != null) {
      listener.progress(message);
    }
  }

  /**
   * Deletes a partial download, if there is one, without reporting a failure to do so.
   *
   * @param file The file to delete; may be {@code null}.
   */
  private static void deleteIfPresent(Path file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      // A leftover partial download costs disk space; the next attempt writes a fresh file.
    }
  }

  /**
   * The digest the hub published for one file.
   *
   * @param form The digest form the hub stated it in.
   * @param hex  The digest value in hex, without the quotes the header carries.
   */
  private record Digest(Checksum form, String hex) {

    /**
     * {@return whether a file digests to the value the hub published}
     *
     * @param file The file to digest.
     * @throws IOException Thrown if the file cannot be read.
     */
    boolean matches(Path file) throws IOException {
      return hex.equalsIgnoreCase(form.hexOf(file));
    }
  }

  /**
   * The two digest forms the hub publishes in its {@code x-linked-etag} header, told apart by the
   * length of the hex value.
   */
  private enum Checksum {

    /** The git object name of a file stored in git itself: its bytes behind a blob header. */
    GIT_BLOB_SHA1("git blob SHA-1", "SHA-1", SHA1_HEX_LENGTH),

    /** The digest of a file stored in Git LFS: its bytes alone. */
    LFS_SHA256("SHA-256", "SHA-256", SHA256_HEX_LENGTH);

    private final String displayName;
    private final String algorithm;
    private final int hexLength;

    Checksum(String displayName, String algorithm, int hexLength) {
      this.displayName = displayName;
      this.algorithm = algorithm;
      this.hexLength = hexLength;
    }

    /**
     * {@return the digest form a hex value of this length is, or {@code null} when the value is
     * not a hex string of either length}
     *
     * @param value The digest value, without its quotes. Must not be {@code null}.
     */
    static Checksum of(String value) {
      for (final Checksum checksum : values()) {
        if (value.length() == checksum.hexLength && HEX_PATTERN.matcher(value).matches()) {
          return checksum;
        }
      }
      return null;
    }

    /** {@return the name of this digest form, for a message} */
    String displayName() {
      return displayName;
    }

    /**
     * {@return the digest of a file in this form, in lower case hex}
     *
     * @param file The file to digest.
     * @throws IOException Thrown if the file cannot be read.
     */
    String hexOf(Path file) throws IOException {
      final MessageDigest digest;
      try {
        digest = MessageDigest.getInstance(algorithm);
      } catch (NoSuchAlgorithmException e) {
        throw new IOException(algorithm + " is not available", e);
      }
      if (this == GIT_BLOB_SHA1) {
        digest.update((GIT_BLOB_PREFIX + Files.size(file) + '\0')
            .getBytes(StandardCharsets.US_ASCII));
      }
      try (InputStream in = Files.newInputStream(file);
           DigestInputStream digesting = new DigestInputStream(in, digest)) {
        final byte[] buffer = new byte[DIGEST_BUFFER_SIZE];
        while (digesting.read(buffer) != -1) {
          // Reading the file is what updates the digest.
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    }
  }
}
