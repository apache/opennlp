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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import opennlp.tools.util.archive.TarStream;

/**
 * Fetches a third-party resource, such as a training corpus, a dictionary archive, or a
 * lexicon, into a local directory. The caller supplies the location and thereby accepts
 * that resource's license; no locations are built in and no data is bundled.
 *
 * <p>An optional checksum is verified against the downloaded bytes before anything is
 * unpacked: a 64-character hex digest selects SHA-256, a 128-character one SHA-512.
 * The content format is detected from the bytes, not from the name: gzip-compressed
 * tar archives and zip archives are unpacked with their relative structure, entries
 * that would escape the target directory are rejected, plain gzip files are
 * decompressed, and anything else is stored as a file under its source name. One name
 * rule overrides byte detection: a {@code *.bin} source is always stored packed,
 * because an OpenNLP model file is itself a zip archive that its consumers load
 * packed.</p>
 *
 * <p>Every installation is bounded by {@link Limits}: http and https fetches carry
 * connection and read timeouts, follow at most a fixed number of redirects, refuse
 * redirects that leave the http and https schemes or downgrade https to http, and
 * abort once the download or the expanded content crosses its size ceiling. The
 * defaults in {@link Limits#DEFAULT} apply when no limits are given.</p>
 *
 * <p>Installation is staged: content is unpacked into a hidden staging directory on
 * the same filesystem and moved into the target only after the download was verified
 * and every entry unpacked cleanly. A failed installation therefore leaves the target
 * directory as it was, without partially written files.</p>
 *
 * @see DownloadUtil
 * @since 3.0.0
 */
public final class ResourceInstaller {

  private static final String SHA_256 = "SHA-256";
  private static final String SHA_512 = "SHA-512";
  private static final int SHA_256_HEX_LENGTH = 64;
  private static final int SHA_512_HEX_LENGTH = 128;
  private static final String GZIP_SUFFIX = ".gz";

  /** OpenNLP model files are packed zip archives; install them packed. */
  private static final String MODEL_SUFFIX = ".bin";
  private static final String DEFAULT_RESOURCE_NAME = "resource";
  private static final String STAGING_PREFIX = ".opennlp-staging";
  private static final int BUFFER_SIZE = 8192;
  private static final int MAGIC_LENGTH = 2;
  private static final int GZIP_MAGIC_FIRST = 0x1F;
  private static final int GZIP_MAGIC_SECOND = 0x8B;
  private static final int ZIP_MAGIC_FIRST = 'P';
  private static final int ZIP_MAGIC_SECOND = 'K';
  private static final int HTTP_TEMPORARY_REDIRECT = 307;
  private static final int HTTP_PERMANENT_REDIRECT = 308;
  private static final String SCHEME_HTTP = "http";
  private static final String SCHEME_HTTPS = "https";

  /**
   * Safety ceilings and network behavior for one installation.
   *
   * @param connectTimeout How long to wait for a connection to be established. Must
   *                       be positive.
   * @param readTimeout How long to wait for data on an established connection. Must
   *                    be positive.
   * @param maxRedirects How many http redirects to follow before failing. Must not be
   *                     negative; zero refuses all redirects.
   * @param maxDownloadBytes The largest download accepted, in bytes. Must be positive.
   * @param maxExpandedBytes The largest total expanded content accepted, in bytes,
   *                         summed over all archive entries. Must be positive.
   */
  public record Limits(Duration connectTimeout, Duration readTimeout, int maxRedirects,
                       long maxDownloadBytes, long maxExpandedBytes) {

    /**
     * The limits applied when none are given: 20 second connect timeout, 60 second
     * read timeout, at most 5 redirects, a 1 GiB download ceiling, and a 4 GiB
     * expansion ceiling.
     */
    public static final Limits DEFAULT = new Limits(Duration.ofSeconds(20),
        Duration.ofSeconds(60), 5, 1L << 30, 4L << 30);

    public Limits {
      if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
        throw new IllegalArgumentException("connectTimeout must be positive");
      }
      if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
        throw new IllegalArgumentException("readTimeout must be positive");
      }
      if (maxRedirects < 0) {
        throw new IllegalArgumentException("maxRedirects must not be negative");
      }
      if (maxDownloadBytes <= 0) {
        throw new IllegalArgumentException("maxDownloadBytes must be positive");
      }
      if (maxExpandedBytes <= 0) {
        throw new IllegalArgumentException("maxExpandedBytes must be positive");
      }
    }
  }

  private ResourceInstaller() {
  }

  /**
   * Fetches and unpacks a resource without checksum verification, under
   * {@link Limits#DEFAULT}.
   *
   * @param source The resource location. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @return The target directory. Never {@code null}.
   * @throws IOException Thrown if fetching or unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source} or
   *         {@code targetDirectory} is {@code null}.
   */
  public static Path install(URI source, Path targetDirectory) throws IOException {
    return install(source, targetDirectory, null);
  }

  /**
   * Fetches, verifies, and unpacks a resource under {@link Limits#DEFAULT}.
   *
   * @param source The resource location. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @param checksum The expected digest of the downloaded bytes as a hex string,
   *                 compared case-insensitively and ignoring leading and trailing
   *                 whitespace: 64 characters select SHA-256, 128 characters SHA-512.
   *                 Pass {@code null} to skip verification.
   * @return The target directory. Never {@code null}.
   * @throws IOException Thrown if fetching fails, the checksum does not match, or
   *         unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source} or
   *         {@code targetDirectory} is {@code null}, or {@code checksum} is neither a
   *         64-character nor a 128-character hex string.
   */
  public static Path install(URI source, Path targetDirectory, String checksum)
      throws IOException {
    return install(source, targetDirectory, checksum, Limits.DEFAULT);
  }

  /**
   * Fetches, verifies, and unpacks a resource under the given {@link Limits}.
   *
   * @param source The resource location. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @param checksum The expected digest of the downloaded bytes as a hex string,
   *                 compared case-insensitively and ignoring leading and trailing
   *                 whitespace: 64 characters select SHA-256, 128 characters SHA-512.
   *                 Pass {@code null} to skip verification.
   * @param limits The timeouts, redirect allowance, and size ceilings to enforce.
   *               Must not be {@code null}.
   * @return The target directory. Never {@code null}.
   * @throws IOException Thrown if fetching fails, a limit is exceeded, the checksum
   *         does not match, or unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source}, {@code targetDirectory},
   *         or {@code limits} is {@code null}, or {@code checksum} is neither a
   *         64-character nor a 128-character hex string.
   */
  public static Path install(URI source, Path targetDirectory, String checksum,
      Limits limits) throws IOException {
    if (source == null) {
      throw new IllegalArgumentException("source must not be null");
    }
    if (targetDirectory == null) {
      throw new IllegalArgumentException("targetDirectory must not be null");
    }
    if (limits == null) {
      throw new IllegalArgumentException("limits must not be null");
    }
    final String expected = validateChecksum(checksum);
    Files.createDirectories(targetDirectory);
    final Path downloaded = Files.createTempFile("opennlp-resource", ".download");
    try {
      download(source, downloaded, limits);
      if (expected != null) {
        verify(downloaded, expected);
      }
      installStaged(downloaded, sourceName(source), targetDirectory, limits);
      return targetDirectory;
    } finally {
      Files.deleteIfExists(downloaded);
    }
  }

  /**
   * Validates the checksum argument and normalizes it for comparison.
   *
   * @param checksum The digest as given by the caller, or {@code null} to skip.
   * @return The trimmed digest, or {@code null} when verification is skipped.
   * @throws IllegalArgumentException Thrown if the digest is neither a 64-character
   *         nor a 128-character hex string.
   */
  private static String validateChecksum(String checksum) {
    if (checksum == null) {
      return null;
    }
    final String trimmed = checksum.trim();
    if ((trimmed.length() == SHA_256_HEX_LENGTH || trimmed.length() == SHA_512_HEX_LENGTH)
        && isHex(trimmed)) {
      return trimmed;
    }
    throw new IllegalArgumentException(
        "checksum must be 64 (SHA-256) or 128 (SHA-512) hex characters; pass null to skip");
  }

  /**
   * @param value The string to inspect.
   * @return {@code true} if every character is a hexadecimal digit.
   */
  private static boolean isHex(String value) {
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      final boolean digit = c >= '0' && c <= '9';
      final boolean lower = c >= 'a' && c <= 'f';
      final boolean upper = c >= 'A' && c <= 'F';
      if (!digit && !lower && !upper) {
        return false;
      }
    }
    return true;
  }

  /**
   * Fetches the source into the given file, bounded by the download ceiling. Http and
   * https locations are fetched with timeouts and the redirect policy; other schemes,
   * such as {@code file}, are opened directly.
   *
   * @param source The resource location.
   * @param file The file receiving the downloaded bytes.
   * @param limits The limits to enforce.
   * @throws IOException Thrown if fetching fails or a limit is exceeded.
   */
  private static void download(URI source, Path file, Limits limits) throws IOException {
    final Budget budget = new Budget(limits.maxDownloadBytes(),
        "download exceeds the ceiling of " + limits.maxDownloadBytes() + " bytes");
    final String scheme = source.getScheme();
    if (SCHEME_HTTP.equalsIgnoreCase(scheme) || SCHEME_HTTPS.equalsIgnoreCase(scheme)) {
      downloadHttp(source, file, limits, budget);
    } else {
      try (InputStream in = source.toURL().openStream()) {
        copyBounded(in, file, budget);
      }
    }
  }

  /**
   * Fetches an http or https source with connection and read timeouts, following at
   * most the allowed number of redirects under the redirect policy, checking any
   * declared content length against the download ceiling before reading the body, and
   * bounding the transferred bytes against the same ceiling.
   *
   * @param source The resource location as requested by the caller.
   * @param file The file receiving the downloaded bytes.
   * @param limits The limits to enforce.
   * @param budget The download budget shared with the caller.
   * @throws IOException Thrown if fetching fails, the server answers with a status
   *         other than 200, the redirect policy is violated, or a limit is exceeded.
   */
  private static void downloadHttp(URI source, Path file, Limits limits, Budget budget)
      throws IOException {
    URI current = source;
    int redirects = 0;
    while (true) {
      final HttpURLConnection connection =
          (HttpURLConnection) current.toURL().openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(timeoutMillis(limits.connectTimeout()));
      connection.setReadTimeout(timeoutMillis(limits.readTimeout()));
      try {
        final int status = connection.getResponseCode();
        if (isRedirect(status)) {
          if (redirects >= limits.maxRedirects()) {
            throw new IOException(
                "more than " + limits.maxRedirects() + " redirects: " + source);
          }
          current = resolveRedirect(current, connection.getHeaderField("Location"));
          redirects++;
          continue;
        }
        if (status != HttpURLConnection.HTTP_OK) {
          throw new IOException(
              "download failed with HTTP status " + status + ": " + current);
        }
        final long declared = connection.getContentLengthLong();
        if (declared > limits.maxDownloadBytes()) {
          throw new IOException("declared content length " + declared
              + " exceeds the download ceiling of " + limits.maxDownloadBytes()
              + " bytes");
        }
        try (InputStream in = connection.getInputStream()) {
          copyBounded(in, file, budget);
        }
        return;
      } finally {
        connection.disconnect();
      }
    }
  }

  /**
   * @param status The HTTP response status.
   * @return {@code true} if the status is one of the redirect statuses 301, 302, 303,
   *         307, or 308.
   */
  private static boolean isRedirect(int status) {
    return status == HttpURLConnection.HTTP_MOVED_PERM
        || status == HttpURLConnection.HTTP_MOVED_TEMP
        || status == HttpURLConnection.HTTP_SEE_OTHER
        || status == HTTP_TEMPORARY_REDIRECT
        || status == HTTP_PERMANENT_REDIRECT;
  }

  /**
   * Resolves a redirect location against the redirected request and enforces the
   * redirect policy: the target must be an http or https location, and an https
   * request must not be redirected to plain http.
   *
   * @param from The location that answered with the redirect.
   * @param location The Location header value, absolute or relative, or {@code null}
   *                 when the header is absent.
   * @return The resolved redirect target. Never {@code null}.
   * @throws IOException Thrown if the location is absent or malformed, leaves the
   *         http and https schemes, or downgrades https to http.
   */
  static URI resolveRedirect(URI from, String location) throws IOException {
    if (location == null || location.isEmpty()) {
      throw new IOException("redirect from " + from + " carries no Location header");
    }
    final URI target;
    try {
      target = from.resolve(location);
    } catch (IllegalArgumentException e) {
      throw new IOException(
          "redirect from " + from + " carries a malformed Location: " + location, e);
    }
    final String scheme = target.getScheme();
    final boolean https = SCHEME_HTTPS.equalsIgnoreCase(scheme);
    if (!https && !SCHEME_HTTP.equalsIgnoreCase(scheme)) {
      throw new IOException(
          "redirect target is not an http or https location: " + target);
    }
    if (SCHEME_HTTPS.equalsIgnoreCase(from.getScheme()) && !https) {
      throw new IOException("redirect downgrades https to http: " + target);
    }
    return target;
  }

  /**
   * @param timeout The timeout as a duration.
   * @return The timeout in milliseconds, clamped to the int range.
   */
  private static int timeoutMillis(Duration timeout) {
    return (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE);
  }

  /**
   * Computes the file's digest and compares it with the expected hex digest, ignoring
   * hex letter case. The digest length selects the algorithm: 64 characters SHA-256,
   * 128 characters SHA-512.
   *
   * @param file The file to digest.
   * @param expected The expected hex digest, already trimmed.
   * @throws IOException Thrown if the file cannot be read or the digests differ.
   */
  private static void verify(Path file, String expected) throws IOException {
    final String algorithm =
        expected.length() == SHA_512_HEX_LENGTH ? SHA_512 : SHA_256;
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(algorithm + " is unavailable in this runtime", e);
    }
    try (InputStream in = Files.newInputStream(file)) {
      final byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = in.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
    }
    final String actual = HexFormat.of().formatHex(digest.digest());
    if (!actual.equalsIgnoreCase(expected)) {
      throw new IOException(
          "checksum mismatch: expected " + expected + " but downloaded " + actual);
    }
  }

  /**
   * Unpacks the downloaded content into a hidden staging directory beneath the target
   * and promotes it into the target only after every entry unpacked cleanly. The
   * staging directory lives on the target's filesystem so promotion is a sequence of
   * renames, and it is removed whether the installation succeeds or fails.
   *
   * @param downloaded The fetched and verified file.
   * @param name The file name derived from the source location.
   * @param target The directory to install into.
   * @param limits The limits to enforce while unpacking.
   * @throws IOException Thrown if unpacking fails, a limit is exceeded, or promotion
   *         or staging cleanup fails.
   */
  private static void installStaged(Path downloaded, String name, Path target,
      Limits limits) throws IOException {
    final Path staging = Files.createTempDirectory(target, STAGING_PREFIX);
    try {
      unpack(downloaded, name, staging, limits);
      promote(staging, target);
    } catch (IOException e) {
      try {
        deleteRecursively(staging);
      } catch (IOException cleanup) {
        e.addSuppressed(cleanup);
      }
      throw e;
    }
    deleteRecursively(staging);
  }

  /**
   * Moves every staged regular file to its relative location beneath the target,
   * replacing existing files. Moves are attempted atomically and fall back to a plain
   * move where the filesystem does not support atomic replacement.
   *
   * @param staging The staging directory holding the fully unpacked content.
   * @param target The directory to install into.
   * @throws IOException Thrown if a move fails.
   */
  private static void promote(Path staging, Path target) throws IOException {
    final List<Path> files;
    try (Stream<Path> walk = Files.walk(staging)) {
      files = walk.filter(Files::isRegularFile).toList();
    }
    for (final Path file : files) {
      final Path destination = target.resolve(staging.relativize(file).toString());
      Files.createDirectories(destination.getParent());
      try {
        Files.move(file, destination, StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(file, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  /**
   * Removes the given directory tree, deepest entries first.
   *
   * @param root The directory to remove.
   * @throws IOException Thrown if a deletion fails.
   */
  private static void deleteRecursively(Path root) throws IOException {
    final List<Path> paths;
    try (Stream<Path> walk = Files.walk(root)) {
      paths = walk.sorted(Comparator.reverseOrder()).toList();
    }
    for (final Path path : paths) {
      Files.deleteIfExists(path);
    }
  }

  /**
   * Detects the content format from its leading bytes and unpacks accordingly,
   * bounding the total expanded bytes against the expansion ceiling. One exception: a
   * source named {@code *.bin} is stored verbatim even when its bytes are a zip
   * archive, because that is exactly what an OpenNLP model file is: a zipped artifact
   * that consumers load packed. Unpacking it would deliver its innards
   * ({@code manifest.properties}, {@code *.model}) where the operator asked for the
   * model.
   *
   * @param downloaded The fetched file.
   * @param name The file name derived from the source location.
   * @param staging The staging directory to unpack into.
   * @param limits The limits to enforce.
   * @throws IOException Thrown if reading or unpacking fails or the expansion ceiling
   *         is exceeded.
   */
  private static void unpack(Path downloaded, String name, Path staging, Limits limits)
      throws IOException {
    final Budget budget = new Budget(limits.maxExpandedBytes(),
        "expanded content exceeds the ceiling of " + limits.maxExpandedBytes()
            + " bytes");
    try (InputStream raw = new BufferedInputStream(Files.newInputStream(downloaded))) {
      raw.mark(MAGIC_LENGTH);
      final int first = raw.read();
      final int second = raw.read();
      raw.reset();
      if (name.endsWith(MODEL_SUFFIX)) {
        copyBounded(raw, safeChild(staging, name), budget);
      } else if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
        unpackGzip(raw, name, staging, budget);
      } else if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
        unpackZip(raw, staging, budget);
      } else {
        copyBounded(raw, safeChild(staging, name), budget);
      }
    }
  }

  /**
   * Unpacks gzip content: a tar archive inside when present, a plain file otherwise. A
   * plain file loses the {@code .gz} suffix of its source name.
   *
   * @param raw The gzip-compressed content.
   * @param name The file name derived from the source location.
   * @param staging The staging directory to unpack into.
   * @param budget The expansion budget.
   * @throws IOException Thrown if decompressing or unpacking fails or the expansion
   *         ceiling is exceeded.
   */
  private static void unpackGzip(InputStream raw, String name, Path staging,
      Budget budget) throws IOException {
    final InputStream decompressed =
        new BufferedInputStream(new GZIPInputStream(raw), BUFFER_SIZE);
    if (TarStream.startsWithHeader(decompressed)) {
      unpackTar(decompressed, staging, budget);
    } else {
      final String plainName = name.endsWith(GZIP_SUFFIX)
          ? name.substring(0, name.length() - GZIP_SUFFIX.length()) : name;
      copyBounded(decompressed, safeChild(staging, plainName), budget);
    }
  }

  /**
   * Unpacks every regular tar entry to its relative location beneath the staging
   * directory.
   *
   * @param decompressed The uncompressed tar content.
   * @param staging The staging directory to unpack into.
   * @param budget The expansion budget.
   * @throws IOException Thrown if the archive is malformed, an entry escapes the
   *         staging directory, or the expansion ceiling is exceeded.
   */
  private static void unpackTar(InputStream decompressed, Path staging, Budget budget)
      throws IOException {
    final TarStream entries = new TarStream(decompressed);
    while (entries.next()) {
      if (!entries.isFile()) {
        continue;
      }
      final Path file = safeChild(staging, entries.name());
      Files.createDirectories(file.getParent());
      copyBounded(entries.entryStream(), file, budget);
    }
  }

  /**
   * Unpacks every regular zip entry to its relative location beneath the staging
   * directory.
   *
   * @param raw The zip content.
   * @param staging The staging directory to unpack into.
   * @param budget The expansion budget.
   * @throws IOException Thrown if the archive is malformed, an entry escapes the
   *         staging directory, or the expansion ceiling is exceeded.
   */
  private static void unpackZip(InputStream raw, Path staging, Budget budget)
      throws IOException {
    final ZipInputStream zip = new ZipInputStream(raw);
    ZipEntry entry;
    while ((entry = zip.getNextEntry()) != null) {
      if (entry.isDirectory()) {
        continue;
      }
      final Path file = safeChild(staging, entry.getName());
      Files.createDirectories(file.getParent());
      copyBounded(zip, file, budget);
    }
  }

  /**
   * Copies the stream into the file, charging every byte against the budget before it
   * is written, so an oversized transfer aborts within one buffer of its ceiling.
   *
   * @param in The content to copy.
   * @param file The file to write.
   * @param budget The byte budget to charge.
   * @throws IOException Thrown if reading or writing fails or the budget is exceeded.
   */
  private static void copyBounded(InputStream in, Path file, Budget budget)
      throws IOException {
    try (OutputStream out = Files.newOutputStream(file)) {
      final byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = in.read(buffer)) >= 0) {
        budget.spend(read);
        out.write(buffer, 0, read);
      }
    }
  }

  /**
   * Resolves an archive entry inside the staging directory, rejecting escaping paths.
   *
   * @param staging The staging directory to unpack into.
   * @param entryName The entry name as stored in the archive.
   * @return The resolved path beneath the staging directory. Never {@code null}.
   * @throws IOException Thrown if the entry resolves outside the staging directory.
   */
  private static Path safeChild(Path staging, String entryName) throws IOException {
    final Path resolved = staging.resolve(entryName).normalize();
    if (!resolved.startsWith(staging.normalize())) {
      throw new IOException("archive entry escapes the target directory: " + entryName);
    }
    return resolved;
  }

  /**
   * Derives a file name from the source URI for non-archive content.
   *
   * @param source The resource location.
   * @return The last path segment, or {@code resource} if the location has none.
   */
  private static String sourceName(URI source) {
    final String path = source.getPath();
    if (path == null || path.isEmpty()) {
      return DEFAULT_RESOURCE_NAME;
    }
    final int slash = path.lastIndexOf('/');
    final String name = slash < 0 ? path : path.substring(slash + 1);
    return name.isEmpty() ? DEFAULT_RESOURCE_NAME : name;
  }

  /**
   * A byte budget: {@link #spend(long)} accumulates transferred bytes and fails once
   * the ceiling is crossed.
   */
  private static final class Budget {

    private final long ceiling;
    private final String message;
    private long used;

    /**
     * @param ceiling The largest total number of bytes accepted.
     * @param message The failure message raised when the ceiling is crossed.
     */
    Budget(long ceiling, String message) {
      this.ceiling = ceiling;
      this.message = message;
    }

    /**
     * Charges the given number of bytes against the budget.
     *
     * @param bytes The number of bytes to charge.
     * @throws IOException Thrown if the total charged bytes exceed the ceiling.
     */
    void spend(long bytes) throws IOException {
      used += bytes;
      if (used > ceiling) {
        throw new IOException(message);
      }
    }
  }
}
