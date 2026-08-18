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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
 * that resource's license; no locations are built in and no data is bundled. Only
 * {@code http}, {@code https}, and {@code file} locations are accepted.
 *
 * <p>A checksum is required for http and https sources and optional for file sources.
 * It is verified against the downloaded bytes before anything is unpacked: a
 * 64-character hex digest selects SHA-256, a 128-character one SHA-512.
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
 * abort once the download or the expanded content crosses its size ceiling or the
 * archive crosses its entry ceiling. The defaults in {@link Limits#DEFAULT} apply when
 * no limits are given, and {@link Limits#builder()} starts from them.</p>
 *
 * <p>Installation is staged: content is unpacked into a hidden staging directory on
 * the same filesystem and moved into the target only after the download was verified
 * and every entry unpacked cleanly. A fetch, verification, or unpacking failure
 * therefore leaves the target directory as it was, without partially written files.
 * Promotion refuses to replace a file that already exists in the target and detects
 * the collision before moving anything, so refreshing a resource means removing its
 * old files first.</p>
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
  private static final String DOWNLOAD_PREFIX = ".opennlp-download";
  private static final String DOWNLOAD_SUFFIX = ".part";
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
  private static final String SCHEME_FILE = "file";

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
   * @param maxExpandedBytes The largest expanded byte count accepted. For gzip content,
   *                         this counts the entire decompressed stream; otherwise, it
   *                         counts installed file content. Must be positive.
   * @param maxEntries The largest number of archive entries accepted, counting every
   *                   entry including directories, so an archive of many tiny files
   *                   cannot exhaust directory entries while staying under the byte
   *                   ceilings. Must be positive.
   */
  public record Limits(Duration connectTimeout, Duration readTimeout, int maxRedirects,
                       long maxDownloadBytes, long maxExpandedBytes, long maxEntries) {

    /** The system property overriding the default download ceiling in bytes. */
    public static final String MAX_DOWNLOAD_BYTES_PROPERTY = "opennlp.download.max.bytes";

    /** The system property overriding the default expansion ceiling in bytes. */
    public static final String MAX_EXPANDED_BYTES_PROPERTY =
        "opennlp.install.max.total.bytes";

    /** The system property overriding the default archive entry ceiling. */
    public static final String MAX_ENTRIES_PROPERTY = "opennlp.install.max.entries";

    /**
     * The limits applied when none are given: 20 second connect timeout, 60 second
     * read timeout, at most 5 redirects, a 1 GiB download ceiling, a 4 GiB expansion
     * ceiling, and 100000 archive entries. Each ceiling can be raised or lowered at
     * startup through its system property ({@link #MAX_DOWNLOAD_BYTES_PROPERTY},
     * {@link #MAX_EXPANDED_BYTES_PROPERTY}, {@link #MAX_ENTRIES_PROPERTY}), read once
     * at class load; a value that is absent, not a number, or not positive falls back
     * to the built-in default.
     */
    public static final Limits DEFAULT = new Limits(Duration.ofSeconds(20),
        Duration.ofSeconds(60), 5,
        longProperty(MAX_DOWNLOAD_BYTES_PROPERTY, 1L << 30),
        longProperty(MAX_EXPANDED_BYTES_PROPERTY, 4L << 30),
        longProperty(MAX_ENTRIES_PROPERTY, 100_000L));

    /**
     * Reads a ceiling override from a system property, trimmed before parsing.
     *
     * @param name The property name.
     * @param fallback The built-in default.
     * @return The property's value, or {@code fallback} when the property is absent,
     *         not a number, or not positive.
     */
    static long longProperty(String name, long fallback) {
      final String value = System.getProperty(name);
      if (value == null) {
        return fallback;
      }
      final long parsed;
      try {
        parsed = Long.parseLong(value.trim());
      } catch (NumberFormatException e) {
        return fallback;
      }
      return parsed > 0 ? parsed : fallback;
    }

    /**
     * Validates the limit values before constructing an instance.
     *
     * @param connectTimeout How long to wait for a connection to be established.
     * @param readTimeout How long to wait for data on an established connection.
     * @param maxRedirects How many http redirects to follow before failing.
     * @param maxDownloadBytes The largest download accepted, in bytes.
     * @param maxExpandedBytes The largest expanded byte count accepted.
     * @param maxEntries The largest number of archive entries accepted.
     * @throws IllegalArgumentException Thrown if either timeout is {@code null}, zero,
     *         or negative, a ceiling is not positive, or the redirect limit is
     *         negative.
     */
    public Limits(Duration connectTimeout, Duration readTimeout, int maxRedirects,
        long maxDownloadBytes, long maxExpandedBytes, long maxEntries) {
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
      if (maxEntries <= 0) {
        throw new IllegalArgumentException("maxEntries must be positive");
      }
      this.connectTimeout = connectTimeout;
      this.readTimeout = readTimeout;
      this.maxRedirects = maxRedirects;
      this.maxDownloadBytes = maxDownloadBytes;
      this.maxExpandedBytes = maxExpandedBytes;
      this.maxEntries = maxEntries;
    }

    /**
     * Starts from {@link #DEFAULT} so a caller can state only the limits that differ
     * from it, instead of repeating all six in the canonical constructor.
     *
     * @return A builder holding the default limits. Never {@code null}.
     */
    public static Builder builder() {
      return new Builder();
    }

    /**
     * Collects limit values and validates them on {@link #build()}. Each setter returns
     * this builder. Not thread safe; the {@link Limits} it builds is immutable.
     */
    public static final class Builder {

      private Duration connectTimeout = DEFAULT.connectTimeout();
      private Duration readTimeout = DEFAULT.readTimeout();
      private int maxRedirects = DEFAULT.maxRedirects();
      private long maxDownloadBytes = DEFAULT.maxDownloadBytes();
      private long maxExpandedBytes = DEFAULT.maxExpandedBytes();
      private long maxEntries = DEFAULT.maxEntries();

      private Builder() {
      }

      /**
       * Sets how long to wait for a connection to be established.
       *
       * @param connectTimeout How long to wait for a connection to be established.
       *                       Must be positive.
       * @return This builder. Never {@code null}.
       */
      public Builder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
      }

      /**
       * Sets how long to wait for data on an established connection.
       *
       * @param readTimeout How long to wait for data on an established connection.
       *                    Must be positive.
       * @return This builder. Never {@code null}.
       */
      public Builder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
      }

      /**
       * Sets how many http redirects to follow before failing.
       *
       * @param maxRedirects How many http redirects to follow before failing. Must not
       *                     be negative; zero refuses all redirects.
       * @return This builder. Never {@code null}.
       */
      public Builder maxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
      }

      /**
       * Sets the largest download accepted.
       *
       * @param maxDownloadBytes The largest download accepted, in bytes. Must be
       *                         positive.
       * @return This builder. Never {@code null}.
       */
      public Builder maxDownloadBytes(long maxDownloadBytes) {
        this.maxDownloadBytes = maxDownloadBytes;
        return this;
      }

      /**
       * Sets the largest expanded byte count accepted.
       *
       * @param maxExpandedBytes The largest expanded byte count accepted. For gzip
       *                         content, this counts the entire decompressed stream;
       *                         otherwise, it counts installed file content. Must be
       *                         positive.
       * @return This builder. Never {@code null}.
       */
      public Builder maxExpandedBytes(long maxExpandedBytes) {
        this.maxExpandedBytes = maxExpandedBytes;
        return this;
      }

      /**
       * Sets the largest number of archive entries accepted.
       *
       * @param maxEntries The largest number of archive entries accepted, counting
       *                   every entry including directories. Must be positive.
       * @return This builder. Never {@code null}.
       */
      public Builder maxEntries(long maxEntries) {
        this.maxEntries = maxEntries;
        return this;
      }

      /**
       * Builds the limits.
       *
       * @return The limits collected so far. Never {@code null}.
       * @throws IllegalArgumentException Thrown if any value is outside its documented
       *         range, exactly as the canonical constructor rejects it.
       */
      public Limits build() {
        return new Limits(connectTimeout, readTimeout, maxRedirects, maxDownloadBytes,
            maxExpandedBytes, maxEntries);
      }
    }
  }

  private ResourceInstaller() {
  }

  /**
   * Unpacks a resource without checksum verification, under {@link Limits#DEFAULT}.
   * This overload treats the source as trusted caller input and performs no
   * cryptographic integrity verification, so it accepts only {@code file} sources; an
   * http or https source must go through an overload that takes its checksum.
   *
   * @param source The resource location, a {@code file} URI. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @return The target directory. Never {@code null}.
   * @throws IOException Thrown if fetching or unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source} or
   *         {@code targetDirectory} is {@code null}, or {@code source} carries a scheme
   *         other than {@code file}.
   */
  public static Path install(URI source, Path targetDirectory) throws IOException {
    return install(source, targetDirectory, null);
  }

  /**
   * Fetches, verifies, and unpacks a resource under {@link Limits#DEFAULT}.
   *
   * @param source The resource location, an {@code http}, {@code https}, or
   *               {@code file} URI. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @param checksum The expected digest of the downloaded bytes as a hex string,
   *                 compared case-insensitively and ignoring leading and trailing
   *                 whitespace: 64 characters select SHA-256, 128 characters SHA-512.
   *                 Required for an http or https source; pass {@code null} to skip
   *                 verification for a {@code file} source.
   * @return The target directory. Never {@code null}.
   * @throws IOException Thrown if fetching fails, the checksum does not match, or
   *         unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source} or
   *         {@code targetDirectory} is {@code null}, {@code source} carries a scheme
   *         other than {@code http}, {@code https}, or {@code file}, {@code checksum}
   *         is neither a 64-character nor a 128-character hex string, or an http or
   *         https source carries no checksum.
   */
  public static Path install(URI source, Path targetDirectory, String checksum)
      throws IOException {
    return install(source, targetDirectory, checksum, Limits.DEFAULT);
  }

  /**
   * Fetches, verifies, and unpacks a resource under the given {@link Limits}.
   *
   * @param source The resource location, an {@code http}, {@code https}, or
   *               {@code file} URI. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @param checksum The expected digest of the downloaded bytes as a hex string,
   *                 compared case-insensitively and ignoring leading and trailing
   *                 whitespace: 64 characters select SHA-256, 128 characters SHA-512.
   *                 Required for an http or https source; pass {@code null} to skip
   *                 verification for a {@code file} source.
   * @param limits The timeouts, redirect allowance, and size and entry ceilings to
   *               enforce. Must not be {@code null}.
   * @return The target directory. Never {@code null}.
   * @throws IOException Thrown if fetching fails, a limit is exceeded, the checksum
   *         does not match, or unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source}, {@code targetDirectory},
   *         or {@code limits} is {@code null}, {@code source} carries a scheme other
   *         than {@code http}, {@code https}, or {@code file}, {@code checksum} is
   *         neither a 64-character nor a 128-character hex string, or an http or https
   *         source carries no checksum.
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
    validateSource(source);
    final String expected = validateChecksum(checksum);
    if (expected == null && isHttp(source.getScheme())) {
      throw new IllegalArgumentException(
          "checksum must be given for an http or https source: " + source);
    }
    Files.createDirectories(targetDirectory);
    final Path downloaded = createDownloadFile(targetDirectory);
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
   * Creates the file the download is written to. It is placed on the target's
   * filesystem, not in the system temporary directory, so a large download cannot
   * exhaust the system temporary directory while the target has room. It is hidden, so
   * a leaked one is visible as residue rather than as an installed file.
   *
   * @param targetDirectory The directory to install into. Must already exist.
   * @return The newly created, empty download file. Never {@code null}.
   * @throws IOException Thrown if the file cannot be created.
   */
  static Path createDownloadFile(Path targetDirectory) throws IOException {
    return Files.createTempFile(targetDirectory, DOWNLOAD_PREFIX, DOWNLOAD_SUFFIX);
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
   * Checks whether a string is made up entirely of hexadecimal digits.
   *
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
   * https locations are fetched with timeouts and the redirect policy; a {@code file}
   * location is read directly. The scheme was accepted by {@link #validateSource(URI)}
   * at the public boundary.
   *
   * @param source The resource location.
   * @param file The file receiving the downloaded bytes.
   * @param limits The limits to enforce.
   * @throws IOException Thrown if fetching fails or a limit is exceeded.
   */
  private static void download(URI source, Path file, Limits limits) throws IOException {
    final Budget budget = new Budget(limits.maxDownloadBytes(),
        "download exceeds the ceiling of " + limits.maxDownloadBytes() + " bytes");
    if (isHttp(source.getScheme())) {
      downloadHttp(source, file, limits, budget);
    } else {
      try (InputStream in = Files.newInputStream(localFile(source))) {
        copyBounded(in, file, budget);
      }
    }
  }

  /**
   * Rejects a source the installer will not fetch. Only {@code http}, {@code https}, and
   * {@code file} are accepted: any other scheme would be handed to whichever URL handler
   * the runtime happens to have installed, outside the connection timeout, read timeout,
   * and redirect policy this class enforces.
   *
   * @param source The resource location as given by the caller.
   * @throws IllegalArgumentException Thrown if the scheme is absent or unsupported.
   */
  private static void validateSource(URI source) {
    final String scheme = source.getScheme();
    if (!isHttp(scheme) && !SCHEME_FILE.equalsIgnoreCase(scheme)) {
      throw new IllegalArgumentException(
          "source scheme must be http, https, or file, but was: " + source);
    }
  }

  /**
   * Classifies a scheme as one the http fetch path handles.
   *
   * @param scheme The URI scheme, or {@code null} when the location has none.
   * @return {@code true} for {@code http} and {@code https}, ignoring case.
   */
  private static boolean isHttp(String scheme) {
    return SCHEME_HTTP.equalsIgnoreCase(scheme) || SCHEME_HTTPS.equalsIgnoreCase(scheme);
  }

  /**
   * Resolves a {@code file} location to a path on the default filesystem.
   *
   * @param source The {@code file} location, already validated as such.
   * @return The local path. Never {@code null}.
   * @throws IOException Thrown if the location does not name a file this runtime can
   *         open, such as a {@code file} URI naming a remote host.
   */
  private static Path localFile(URI source) throws IOException {
    try {
      return Path.of(source);
    } catch (IllegalArgumentException | FileSystemNotFoundException e) {
      throw new IOException("not a readable local file location: " + source, e);
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
   * Classifies a response status as a redirect the installer follows.
   *
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
   * Converts a timeout to the millisecond form the connection setters take. A positive
   * timeout shorter than a millisecond becomes one millisecond rather than zero, because
   * {@link HttpURLConnection#setReadTimeout(int) zero means no timeout at all}, and a
   * timeout too large for the int range is capped instead of overflowing.
   *
   * @param timeout The timeout as a duration. Must be positive.
   * @return The timeout in milliseconds, at least {@code 1} and at most
   *         {@link Integer#MAX_VALUE}.
   */
  private static int timeoutMillis(Duration timeout) {
    final long millis;
    try {
      millis = timeout.toMillis();
    } catch (ArithmeticException e) {
      return Integer.MAX_VALUE;
    }
    return Math.clamp(millis, 1, Integer.MAX_VALUE);
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
   * refusing to replace anything that already exists there. All destinations are
   * checked before the first move, so a collision leaves the target without a mix of
   * old and new files. Moves are attempted atomically and fall back to a plain move
   * where the filesystem does not support it.
   *
   * @param staging The staging directory holding the fully unpacked content.
   * @param target The directory to install into.
   * @throws IOException Thrown if a destination already exists, a move fails, or a
   *         directory on the way to a destination is an existing symbolic link.
   */
  private static void promote(Path staging, Path target) throws IOException {
    final List<Path> files;
    try (Stream<Path> walk = Files.walk(staging)) {
      files = walk.filter(Files::isRegularFile).toList();
    }
    for (final Path file : files) {
      ensureVacant(target, staging.relativize(file));
    }
    for (final Path file : files) {
      final Path destination = destination(target, staging.relativize(file));
      try {
        Files.move(file, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(file, destination);
      }
    }
  }

  /**
   * Checks that one staged file's destination is free to receive it, without creating
   * anything: an existing filesystem object at the destination is a collision, and a
   * symbolic link or a non-directory on the way to it is refused exactly as
   * {@link #destination(Path, Path)} refuses it during the move pass. A missing
   * directory on the way proves the destination vacant.
   *
   * @param target The directory to install into.
   * @param relative The staged file's path relative to the staging directory.
   * @throws IOException Thrown if the destination already exists, or a directory on
   *         the way is a symbolic link or exists as something other than a directory.
   */
  private static void ensureVacant(Path target, Path relative) throws IOException {
    Path directory = target;
    for (int i = 0; i < relative.getNameCount() - 1; i++) {
      directory = directory.resolve(relative.getName(i));
      if (Files.isSymbolicLink(directory)) {
        throw new IOException(
            "installation path crosses a symbolic link: " + directory);
      }
      if (!Files.exists(directory)) {
        return;
      }
      if (!Files.isDirectory(directory)) {
        throw new IOException(
            "installation path crosses an existing file: " + directory);
      }
    }
    final Path destination = directory.resolve(relative.getFileName());
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("target already contains: " + destination);
    }
  }

  /**
   * Resolves one staged file's destination beneath the target, creating the directories
   * leading to it one at a time and refusing to descend through a symbolic link that is
   * already there. An entry name that stays inside the staging directory can still land
   * outside the target if a directory below the target is a link to somewhere else.
   *
   * <p>This covers links present when the installation runs. It is not a defense against
   * a link created concurrently, between the check here and the move that follows.</p>
   *
   * @param target The directory to install into.
   * @param relative The staged file's path relative to the staging directory.
   * @return The destination path beneath the target. Never {@code null}.
   * @throws IOException Thrown if a directory on the way is a symbolic link or exists as
   *         something other than a directory, or if a directory cannot be created.
   */
  private static Path destination(Path target, Path relative) throws IOException {
    Path directory = target;
    for (int i = 0; i < relative.getNameCount() - 1; i++) {
      directory = directory.resolve(relative.getName(i));
      if (Files.isSymbolicLink(directory)) {
        throw new IOException(
            "installation path crosses a symbolic link: " + directory);
      }
      if (!Files.exists(directory)) {
        Files.createDirectory(directory);
      } else if (!Files.isDirectory(directory)) {
        throw new IOException(
            "installation path crosses an existing file: " + directory);
      }
    }
    return directory.resolve(relative.getFileName());
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
    final Budget entryBudget = new Budget(limits.maxEntries(),
        "archive entry count exceeds the ceiling of " + limits.maxEntries()
            + " entries");
    try (InputStream raw = new BufferedInputStream(Files.newInputStream(downloaded))) {
      raw.mark(MAGIC_LENGTH);
      final int first = raw.read();
      final int second = raw.read();
      raw.reset();
      if (name.endsWith(MODEL_SUFFIX)) {
        copyBounded(raw, safeChild(staging, name), budget);
      } else if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
        unpackGzip(raw, name, staging, budget, entryBudget);
      } else if (first == ZIP_MAGIC_FIRST && second == ZIP_MAGIC_SECOND) {
        unpackZip(raw, staging, budget, entryBudget);
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
   * @param entryBudget The entry-count budget.
   * @throws IOException Thrown if decompressing or unpacking fails or a ceiling is
   *         exceeded.
   */
  private static void unpackGzip(InputStream raw, String name, Path staging,
      Budget budget, Budget entryBudget) throws IOException {
    final InputStream decompressed = new BufferedInputStream(
        new BudgetInputStream(new GZIPInputStream(raw), budget), BUFFER_SIZE);
    if (TarStream.startsWithHeader(decompressed)) {
      unpackTar(decompressed, staging, entryBudget);
    } else {
      final String plainName = name.endsWith(GZIP_SUFFIX)
          ? name.substring(0, name.length() - GZIP_SUFFIX.length()) : name;
      copy(decompressed, safeChild(staging, plainName));
    }
  }

  /**
   * Unpacks every regular tar entry to its relative location beneath the staging
   * directory.
   *
   * @param decompressed The uncompressed tar content.
   * @param staging The staging directory to unpack into.
   * @param entryBudget The entry-count budget, charged for every entry including
   *                    directories.
   * @throws IOException Thrown if the archive is malformed, an entry escapes the
   *         staging directory, or the entry ceiling is exceeded.
   */
  private static void unpackTar(InputStream decompressed, Path staging,
      Budget entryBudget) throws IOException {
    final TarStream entries = new TarStream(decompressed);
    while (entries.next()) {
      entryBudget.spend(1);
      if (!entries.isFile()) {
        continue;
      }
      final Path file = safeChild(staging, entries.name());
      Files.createDirectories(file.getParent());
      copy(entries.entryStream(), file);
    }
  }

  /**
   * Unpacks every regular zip entry to its relative location beneath the staging
   * directory.
   *
   * @param raw The zip content.
   * @param staging The staging directory to unpack into.
   * @param budget The expansion budget.
   * @param entryBudget The entry-count budget, charged for every entry including
   *                    directories.
   * @throws IOException Thrown if the archive is malformed, an entry escapes the
   *         staging directory, or a ceiling is exceeded.
   */
  private static void unpackZip(InputStream raw, Path staging, Budget budget,
      Budget entryBudget) throws IOException {
    final ZipInputStream zip = new ZipInputStream(raw);
    ZipEntry entry;
    while ((entry = zip.getNextEntry()) != null) {
      entryBudget.spend(1);
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
   * Copies the stream into the file. The caller is responsible for applying any byte
   * ceiling to the input stream.
   *
   * @param in The content to copy.
   * @param file The file to write.
   * @throws IOException Thrown if reading or writing fails.
   */
  private static void copy(InputStream in, Path file) throws IOException {
    try (OutputStream out = Files.newOutputStream(file)) {
      in.transferTo(out);
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
   * A unit budget, counting bytes or archive entries: {@link #spend(long)} accumulates
   * spent units and fails once the ceiling is crossed.
   */
  private static final class Budget {

    private final long ceiling;
    private final String message;
    private long used;

    /**
     * Creates a budget that has spent nothing yet.
     *
     * @param ceiling The largest total number of units accepted.
     * @param message The failure message raised when the ceiling is crossed.
     */
    Budget(long ceiling, String message) {
      this.ceiling = ceiling;
      this.message = message;
    }

    /**
     * Charges the given number of units against the budget.
     *
     * @param units The number of units to charge.
     * @throws IOException Thrown if the total charged units exceed the ceiling.
     */
    void spend(long units) throws IOException {
      used += units;
      if (used > ceiling) {
        throw new IOException(message);
      }
    }
  }

  /**
   * Charges every byte read or skipped from an expanded stream against a shared budget.
   */
  private static final class BudgetInputStream extends FilterInputStream {

    private final Budget budget;

    /**
     * Initializes a budgeted stream.
     *
     * @param in The expanded stream to read.
     * @param budget The budget to charge.
     */
    BudgetInputStream(InputStream in, Budget budget) {
      super(in);
      this.budget = budget;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
      final int value = super.read();
      if (value >= 0) {
        budget.spend(1);
      }
      return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      final int read = super.read(buffer, offset, length);
      if (read > 0) {
        budget.spend(read);
      }
      return read;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long bytes) throws IOException {
      final long skipped = super.skip(bytes);
      budget.spend(skipped);
      return skipped;
    }
  }
}
