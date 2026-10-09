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
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import opennlp.tools.commons.Internal;
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
 * tar archives and zip archives are unpacked with their relative structure. Invalid,
 * escaping, and duplicate file paths are rejected. Plain gzip files are decompressed,
 * and other content is stored as a file under the source name. One name rule overrides
 * byte detection: a {@code *.bin} source is always stored packed because an OpenNLP
 * model file is itself a zip archive that its consumers load packed.</p>
 *
 * <p>Each installation is bounded by {@link Limits}: http and https fetches use
 * connection and read timeouts, follow at most a fixed number of redirects, reject
 * redirects that leave the http and https schemes or downgrade https to http, and
 * abort once the download or the expanded content crosses its size limit or the
 * archive crosses its entry limit. Compressed content, gzip and zip alike, may
 * expand to at most {@link Limits#maxExpansionRatio()} times its compressed size,
 * with a floor of {@value #MIN_EXPANSION_BYTES} bytes for small sources. The defaults in
 * {@link Limits#DEFAULT} apply when no limits are given, and {@link Limits#builder()}
 * starts from them.</p>
 *
 * <p>Installation is staged: content is unpacked into a hidden staging directory on
 * the same filesystem and moved into the target only after the download was verified
 * and every entry unpacked cleanly. A fetch, verification, or unpacking failure
 * promotes no files into the target directory.
 * Promotion does not replace a file that already exists in the target and detects
 * the collision before moving anything, so refreshing a resource means removing its
 * old files first. Work files left in the target by an installation that was killed
 * are removed at the start of the next installation into that target, so concurrent
 * installations into one target directory are not supported.</p>
 *
 * @see DownloadUtil
 * @since 3.0.0
 */
public final class ResourceInstaller {

  private static final String GZIP_SUFFIX = ".gz";

  /** OpenNLP model files are packed zip archives; install them packed. */
  private static final String MODEL_SUFFIX = ".bin";
  private static final String DEFAULT_RESOURCE_NAME = "resource";
  private static final String STAGING_PREFIX = ".opennlp-staging";
  private static final String DOWNLOAD_PREFIX = ".opennlp-download";
  private static final String DOWNLOAD_SUFFIX = ".part";
  /** The path segment that names the parent directory. */
  private static final String PARENT_DIRECTORY = "..";
  /** The path separator used on all platforms. */
  private static final char SLASH = '/';
  /** The path separator used on Windows. */
  private static final char BACKSLASH = '\\';
  /** The NUL character, which no file system accepts in a name. */
  private static final char NUL = '\0';
  /** The buffer size, in bytes, for streaming copies and digests in this package. */
  static final int BUFFER_SIZE = 8192;
  private static final int MAGIC_LENGTH = 4;
  private static final int GZIP_MAGIC_FIRST = 0x1F;
  private static final int GZIP_MAGIC_SECOND = 0x8B;
  private static final int ZIP_MAGIC_FIRST = 'P';
  private static final int ZIP_MAGIC_SECOND = 'K';
  private static final int ZIP_LOCAL_HEADER_THIRD = 3;
  private static final int ZIP_LOCAL_HEADER_FOURTH = 4;
  private static final int ZIP_END_HEADER_THIRD = 5;
  private static final int ZIP_END_HEADER_FOURTH = 6;
  private static final int ZIP_END_HEADER_LENGTH = 22;
  private static final int ZIP_DISK_OFFSET = 4;
  private static final int ZIP_CENTRAL_DISK_OFFSET = 6;
  private static final int ZIP_DISK_ENTRIES_OFFSET = 8;
  private static final int ZIP_TOTAL_ENTRIES_OFFSET = 10;
  private static final int ZIP_CENTRAL_SIZE_OFFSET = 12;
  private static final int ZIP_CENTRAL_OFFSET_OFFSET = 16;
  private static final int ZIP_COMMENT_LENGTH_OFFSET = 20;

  /** The expanded size every compressed source may reach regardless of the ratio. */
  private static final long MIN_EXPANSION_BYTES = 1L << 20;
  private static final int HTTP_TEMPORARY_REDIRECT = 307;
  private static final int HTTP_PERMANENT_REDIRECT = 308;
  private static final String SCHEME_HTTP = "http";
  private static final String SCHEME_HTTPS = "https";
  private static final String SCHEME_FILE = "file";
  private static final String MALFORMED_ZIP_ERROR = "malformed zip archive";
  private static final String ZIP_MISMATCH_ERROR =
      "zip local headers and central directory list different files";

  /**
   * Safety limits and network behavior for one installation.
   *
   * @param connectTimeout How long to wait for a connection to be established. Must
   *                       be positive.
   * @param readTimeout How long to wait for data on an established connection. Must
   *                    be positive.
   * @param maxRedirects How many http redirects to follow before failing. Must not be
   *                     negative; zero rejects all redirects.
   * @param maxDownloadBytes The largest download accepted, in bytes. Must be positive.
   * @param maxExpandedBytes The largest expanded byte count accepted. For gzip content,
   *                         this counts the entire decompressed stream; otherwise, it
   *                         counts installed file content. Must be positive.
   * @param maxEntries The largest number of archive entries accepted, counting every
   *                   entry including directories, so an archive of many tiny files
   *                   cannot exhaust directory entries while staying under the byte
   *                   limits. Must be positive.
   * @param maxExpansionRatio The largest expanded size accepted per compressed byte of
   *                          a source, so a small archive cannot expand to the whole
   *                          expansion limit. Applies to gzip and zip content; a source
   *                          may always expand to {@value #MIN_EXPANSION_BYTES} bytes
   *                          regardless of it. Must be positive.
   */
  public record Limits(Duration connectTimeout, Duration readTimeout, int maxRedirects,
                       long maxDownloadBytes, long maxExpandedBytes, long maxEntries,
                       long maxExpansionRatio) {

    /** The system property overriding the default download limit in bytes. */
    public static final String MAX_DOWNLOAD_BYTES_PROPERTY = "opennlp.download.max.bytes";

    /** The system property overriding the default expansion limit in bytes. */
    public static final String MAX_EXPANDED_BYTES_PROPERTY =
        "opennlp.install.max.total.bytes";

    /** The system property overriding the default archive entry limit. */
    public static final String MAX_ENTRIES_PROPERTY = "opennlp.install.max.entries";

    /** The system property overriding the default expansion ratio. */
    public static final String MAX_EXPANSION_RATIO_PROPERTY =
        "opennlp.install.max.expansion.ratio";

    /**
     * The limits applied when none are given: 20 second connect timeout, 60 second
     * read timeout, at most 5 redirects, a 1 GiB download limit, a 4 GiB expansion
     * limit, 100000 archive entries, and an expansion ratio of 100. Each limit can be
     * raised or lowered at startup through its system property
     * ({@link #MAX_DOWNLOAD_BYTES_PROPERTY}, {@link #MAX_EXPANDED_BYTES_PROPERTY},
     * {@link #MAX_ENTRIES_PROPERTY}, {@link #MAX_EXPANSION_RATIO_PROPERTY}), read once
     * at class load; a value that is absent, not a number, or not positive falls back
     * to the built-in default.
     */
    public static final Limits DEFAULT = new Limits(Duration.ofSeconds(20),
        Duration.ofSeconds(60), 5,
        ResourceLimits.initLimit(MAX_DOWNLOAD_BYTES_PROPERTY, 1L << 30),
        ResourceLimits.initLimit(MAX_EXPANDED_BYTES_PROPERTY, 4L << 30),
        ResourceLimits.initLimit(MAX_ENTRIES_PROPERTY, 100_000L),
        ResourceLimits.initLimit(MAX_EXPANSION_RATIO_PROPERTY, 100L));

    /**
     * Validates the limit values before constructing an instance.
     *
     * @param connectTimeout How long to wait for a connection to be established.
     * @param readTimeout How long to wait for data on an established connection.
     * @param maxRedirects How many http redirects to follow before failing.
     * @param maxDownloadBytes The largest download accepted, in bytes.
     * @param maxExpandedBytes The largest expanded byte count accepted.
     * @param maxEntries The largest number of archive entries accepted.
     * @param maxExpansionRatio The largest expanded size accepted per compressed byte.
     * @throws IllegalArgumentException Thrown if either timeout is {@code null}, zero,
     *         or negative, a limit is not positive, or the redirect limit is
     *         negative.
     */
    public Limits(Duration connectTimeout, Duration readTimeout, int maxRedirects,
        long maxDownloadBytes, long maxExpandedBytes, long maxEntries,
        long maxExpansionRatio) {
      ParamChecks.requireNonNullArg(connectTimeout, "connectTimeout");
      if (connectTimeout.isZero() || connectTimeout.isNegative()) {
        throw new IllegalArgumentException("connectTimeout must be positive");
      }
      ParamChecks.requireNonNullArg(readTimeout, "readTimeout");
      if (readTimeout.isZero() || readTimeout.isNegative()) {
        throw new IllegalArgumentException("readTimeout must be positive");
      }
      ParamChecks.requireNonNegative(maxRedirects, "maxRedirects");
      if (maxDownloadBytes <= 0) {
        throw new IllegalArgumentException("maxDownloadBytes must be positive");
      }
      if (maxExpandedBytes <= 0) {
        throw new IllegalArgumentException("maxExpandedBytes must be positive");
      }
      if (maxEntries <= 0) {
        throw new IllegalArgumentException("maxEntries must be positive");
      }
      if (maxExpansionRatio <= 0) {
        throw new IllegalArgumentException("maxExpansionRatio must be positive");
      }
      this.connectTimeout = connectTimeout;
      this.readTimeout = readTimeout;
      this.maxRedirects = maxRedirects;
      this.maxDownloadBytes = maxDownloadBytes;
      this.maxExpandedBytes = maxExpandedBytes;
      this.maxEntries = maxEntries;
      this.maxExpansionRatio = maxExpansionRatio;
    }

    /**
     * Starts from {@link #DEFAULT} so a caller can state only the limits that differ
     * from it, instead of repeating all seven in the canonical constructor.
     *
     * @return A builder holding the default limits. Not {@code null}.
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
      private long maxExpansionRatio = DEFAULT.maxExpansionRatio();

      /** Initializes a builder with {@link Limits#DEFAULT}. */
      private Builder() {
      }

      /**
       * Sets how long to wait for a connection to be established.
       *
       * @param connectTimeout How long to wait for a connection to be established.
       *                       Must be positive.
       * @return This builder. Not {@code null}.
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
       * @return This builder. Not {@code null}.
       */
      public Builder readTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
        return this;
      }

      /**
       * Sets how many http redirects to follow before failing.
       *
       * @param maxRedirects How many http redirects to follow before failing. Must not
       *                     be negative; zero rejects all redirects.
       * @return This builder. Not {@code null}.
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
       * @return This builder. Not {@code null}.
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
       * @return This builder. Not {@code null}.
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
       * @return This builder. Not {@code null}.
       */
      public Builder maxEntries(long maxEntries) {
        this.maxEntries = maxEntries;
        return this;
      }

      /**
       * Sets the largest expanded size accepted per compressed byte of a source.
       *
       * @param maxExpansionRatio The largest expanded size accepted per compressed
       *                          byte of a source. Must be positive.
       * @return This builder. Not {@code null}.
       */
      public Builder maxExpansionRatio(long maxExpansionRatio) {
        this.maxExpansionRatio = maxExpansionRatio;
        return this;
      }

      /**
       * Builds the limits.
       *
       * @return The limits collected so far. Not {@code null}.
       * @throws IllegalArgumentException Thrown if any value is outside its documented
       *         range.
       */
      public Limits build() {
        return new Limits(connectTimeout, readTimeout, maxRedirects, maxDownloadBytes,
            maxExpandedBytes, maxEntries, maxExpansionRatio);
      }
    }
  }

  /**
   * Writes content into the staging directory of
   * {@link #installSelected(Path, StagingStep, Function)}.
   */
  @Internal
  @FunctionalInterface
  public interface StagingStep {

    /**
     * Writes content into the staging directory.
     *
     * @param staging The empty staging directory. Not {@code null}.
     * @throws IOException Thrown if fetching or writing the content fails.
     */
    void fill(Path staging) throws IOException;
  }

  /** Prevents construction of this utility class. */
  private ResourceInstaller() {
  }

  /**
   * Unpacks a resource without checksum verification, under {@link Limits#DEFAULT}.
   * This overload treats the source as trusted caller input and performs no
   * cryptographic integrity verification, so it accepts only {@code file} sources; an
   * http or https source must go through an overload that takes its checksum.
   *
   * @param source The resource location, a {@code file} URI. Not {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @return The target directory. Not {@code null}.
   * @throws IOException Thrown if fetching or unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source} or
   *         {@code targetDirectory} is {@code null}, {@code source} contains a scheme
   *         other than {@code file}, or its last path segment is not a valid local file
   *         name.
   */
  public static Path install(URI source, Path targetDirectory) throws IOException {
    return install(source, targetDirectory, null);
  }

  /**
   * Fetches, verifies, and unpacks a resource under {@link Limits#DEFAULT}.
   *
   * @param source The resource location, an {@code http}, {@code https}, or
   *               {@code file} URI. Not {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @param checksum The expected digest of the downloaded bytes as a hex string,
   *                 compared case-insensitively and ignoring leading and trailing
   *                 whitespace: 64 characters select SHA-256, 128 characters SHA-512.
   *                 Required for an http or https source; pass {@code null} to skip
   *                 verification for a {@code file} source.
   * @return The target directory. Not {@code null}.
   * @throws IOException Thrown if fetching fails, the checksum does not match, or
   *         unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source} or
   *         {@code targetDirectory} is {@code null}, {@code source} contains a scheme
   *         other than {@code http}, {@code https}, or {@code file}, {@code checksum}
   *         is not a 64-character or 128-character hex string, an http or https source
   *         contains no checksum, or the source does not provide a valid local file
   *         name.
   */
  public static Path install(URI source, Path targetDirectory, String checksum)
      throws IOException {
    return install(source, targetDirectory, checksum, Limits.DEFAULT);
  }

  /**
   * Fetches, verifies, and unpacks a resource under the given {@link Limits}.
   *
   * @param source The resource location, an {@code http}, {@code https}, or
   *               {@code file} URI. Not {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @param checksum The expected digest of the downloaded bytes as a hex string,
   *                 compared case-insensitively and ignoring leading and trailing
   *                 whitespace: 64 characters select SHA-256, 128 characters SHA-512.
   *                 Required for an http or https source; pass {@code null} to skip
   *                 verification for a {@code file} source.
   * @param limits The timeouts, redirect allowance, and size and entry limits to
   *               enforce. Not {@code null}.
   * @return The target directory. Not {@code null}.
   * @throws IOException Thrown if fetching fails, a limit is exceeded, the checksum
   *         does not match, or unpacking fails.
   * @throws IllegalArgumentException Thrown if {@code source}, {@code targetDirectory},
   *         or {@code limits} is {@code null}, {@code source} contains a scheme other
   *         than {@code http}, {@code https}, or {@code file}, {@code checksum} is
   *         not a 64-character or 128-character hex string, an http or https source
   *         contains no checksum, or the source does not provide a valid local file
   *         name.
   */
  public static Path install(URI source, Path targetDirectory, String checksum,
      Limits limits) throws IOException {
    return install(source, targetDirectory, checksum, null, limits);
  }

  /**
   * Validates the request, downloads and verifies the resource, and installs it from a
   * staging directory.
   *
   * @param source The resource location.
   * @param targetDirectory The directory to install into.
   * @param checksum The expected digest, or {@code null} for a file source.
   * @param name The preferred name for non-archive content, or {@code null} to use the
   *             source name.
   * @param limits The limits to enforce.
   * @return The target directory. Not {@code null}.
   * @throws IOException Thrown if fetching, verification, or installation fails.
   * @throws IllegalArgumentException Thrown if an argument is invalid.
   */
  static Path install(URI source, Path targetDirectory, String checksum,
      String name, Limits limits) throws IOException {
    ParamChecks.requireNonNullArg(source, "source");
    ParamChecks.requireNonNullArg(targetDirectory, "targetDirectory");
    ParamChecks.requireNonNullArg(limits, "limits");
    validateSource(source);
    final String expected = validateChecksum(checksum);
    if (expected == null && isHttp(source.getScheme())) {
      throw new IllegalArgumentException(
          "checksum must be given for an http or https source: " + source);
    }
    final String resourceName = validateSourceName(
        name == null ? sourceName(source) : name);
    final boolean createdTarget = Files.notExists(targetDirectory);
    Files.createDirectories(targetDirectory);
    removeStaleWorkFiles(targetDirectory);
    final Path downloaded = createDownloadFile(targetDirectory);
    try {
      download(source, downloaded, limits);
      if (expected != null) {
        verify(downloaded, expected);
      }
      installStaged(targetDirectory,
          staging -> unpack(downloaded, resourceName, staging, limits), Function.identity());
      return targetDirectory;
    } catch (IOException e) {
      Files.deleteIfExists(downloaded);
      if (createdTarget) {
        removeIfEmpty(targetDirectory, e);
      }
      throw e;
    } finally {
      Files.deleteIfExists(downloaded);
    }
  }

  /**
   * Installs a selection of the files that a caller-supplied step writes into a staging
   * directory. The step runs against an empty hidden staging directory beneath the
   * target, typically by calling {@link #install(URI, Path, String)} with the staging
   * directory as its target. The selector then maps each staged regular file, by its
   * path relative to the staging directory, to its path relative to the target, or to
   * {@code null} to leave it out. The selected files are promoted under the same rules
   * as {@link #install(URI, Path, String)}: nothing is promoted when the step fails,
   * and an existing destination aborts the promotion before the first move.
   *
   * @param targetDirectory The directory to install into; created when absent. Must not
   *                        be {@code null}.
   * @param step Writes the content into the staging directory. Must not be
   *             {@code null}.
   * @param selector Maps a staged file's relative path to its relative destination
   *                 beneath the target, or to {@code null} to skip the file. A
   *                 destination must be relative and normalized. Must not be
   *                 {@code null}.
   * @return The number of files installed.
   * @throws IOException Thrown if the step fails, a destination is absolute or not
   *         normalized, two staged files map to the same destination, a destination
   *         already exists, or moving fails.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  @Internal
  public static int installSelected(Path targetDirectory, StagingStep step,
      Function<Path, Path> selector) throws IOException {
    ParamChecks.requireNonNullArg(targetDirectory, "targetDirectory");
    ParamChecks.requireNonNullArg(step, "step");
    ParamChecks.requireNonNullArg(selector, "selector");
    final boolean createdTarget = Files.notExists(targetDirectory);
    Files.createDirectories(targetDirectory);
    removeStaleWorkFiles(targetDirectory);
    try {
      return installStaged(targetDirectory, step, selector);
    } catch (IOException | RuntimeException e) {
      if (createdTarget) {
        removeIfEmpty(targetDirectory, e);
      }
      throw e;
    }
  }

  /**
   * Removes download files and staging directories that an earlier installation left in
   * the target because its process ended before cleanup. Only entries with the hidden
   * work-file prefixes are touched.
   *
   * @param targetDirectory The directory to install into.
   * @throws IOException Thrown if listing or deleting fails.
   */
  private static void removeStaleWorkFiles(Path targetDirectory) throws IOException {
    final List<Path> stale;
    try (Stream<Path> entries = Files.list(targetDirectory)) {
      stale = entries.filter(ResourceInstaller::isWorkFile).toList();
    }
    for (final Path entry : stale) {
      if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
        deleteRecursively(entry);
      } else {
        Files.deleteIfExists(entry);
      }
    }
  }

  /**
   * Classifies a target directory entry as a work file of this class.
   *
   * @param entry The entry to inspect.
   * @return {@code true} if the entry name carries a work-file prefix.
   */
  private static boolean isWorkFile(Path entry) {
    final String fileName = entry.getFileName().toString();
    return fileName.startsWith(STAGING_PREFIX) || fileName.startsWith(DOWNLOAD_PREFIX);
  }

  /**
   * Removes a target directory this installation created when the failed installation
   * left nothing in it, so a failed first attempt leaves the filesystem as it was.
   *
   * @param targetDirectory The directory this installation created.
   * @param failure The failure being reported; a cleanup error is added to it.
   */
  private static void removeIfEmpty(Path targetDirectory, Exception failure) {
    try (Stream<Path> entries = Files.list(targetDirectory)) {
      if (entries.findAny().isEmpty()) {
        Files.deleteIfExists(targetDirectory);
      }
    } catch (IOException cleanup) {
      failure.addSuppressed(cleanup);
    }
  }

  /**
   * Creates the file the download is written to. It is placed on the target's
   * filesystem, not in the system temporary directory, so a large download cannot
   * exhaust the system temporary directory while the target has room. Its hidden prefix
   * distinguishes it from installed content.
   *
   * @param targetDirectory The directory to install into. Must already exist.
   * @return The newly created, empty download file. Not {@code null}.
   * @throws IOException Thrown if the file cannot be created.
   */
  static Path createDownloadFile(Path targetDirectory) throws IOException {
    return Files.createTempFile(targetDirectory, DOWNLOAD_PREFIX, DOWNLOAD_SUFFIX);
  }

  /**
   * Validates the checksum argument and normalizes it for comparison.
   *
   * @param checksum The digest as given by the caller, or {@code null} to skip.
   * @return The stripped digest, or {@code null} when verification is skipped.
   * @throws IllegalArgumentException Thrown if the digest is not a 64-character or
   *         128-character hex string.
   */
  private static String validateChecksum(String checksum) {
    if (checksum == null) {
      return null;
    }
    final String trimmed = checksum.strip();
    if (Checksums.isHexDigest(trimmed, Checksums.SHA_256_HEX_LENGTH)
        || Checksums.isHexDigest(trimmed, Checksums.SHA_512_HEX_LENGTH)) {
      return trimmed;
    }
    throw new IllegalArgumentException(
        "checksum must be 64 (SHA-256) or 128 (SHA-512) hex characters; pass null to skip");
  }

  /**
   * Fetches the source into the given file, bounded by the download limit. Http and
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
        "download exceeds the limit of " + limits.maxDownloadBytes() + " bytes");
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
   * {@code file} are accepted: any other scheme would be passed to any URL handler
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
   * @return The local path. Not {@code null}.
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
   * declared content length against the download limit before reading the body, and
   * bounding the transferred bytes against the same limit.
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
              + " exceeds the download limit of " + limits.maxDownloadBytes()
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
   * @param from The location that returned the redirect.
   * @param location The Location header value, absolute or relative, or {@code null}
   *                 when the header is absent.
   * @return The resolved redirect target. Not {@code null}.
   * @throws IOException Thrown if the location is absent or malformed, leaves the
   *         http and https schemes, or downgrades https to http.
   */
  static URI resolveRedirect(URI from, String location) throws IOException {
    if (location == null || location.isEmpty()) {
      throw new IOException("redirect from " + from + " contains no Location header");
    }
    final URI target;
    try {
      target = from.resolve(location);
    } catch (IllegalArgumentException e) {
      throw new IOException(
          "redirect from " + from + " contains a malformed Location: " + location, e);
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
   * timeout shorter than a millisecond becomes one millisecond because
   * {@link HttpURLConnection#setReadTimeout(int) zero disables the timeout}. A timeout
   * too large for the int range is capped.
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
    final String algorithm = expected.length() == Checksums.SHA_512_HEX_LENGTH
        ? Checksums.SHA_512 : Checksums.SHA_256;
    final String actual = Checksums.hexDigest(file, algorithm);
    if (!actual.equalsIgnoreCase(expected)) {
      throw new IOException(
          "checksum mismatch: expected " + expected + " but downloaded " + actual);
    }
  }

  /**
   * Runs a staging step in a hidden staging directory beneath the target and promotes
   * the selected files into the target only after the step completed. The staging
   * directory lives on the target's filesystem so promotion is a sequence of renames,
   * and it is removed whether the installation succeeds or fails.
   *
   * @param target The directory to install into.
   * @param step Writes the content into the staging directory.
   * @param selector Maps a staged file's relative path to its relative destination, or
   *                 to {@code null} to skip it.
   * @return The number of files promoted.
   * @throws IOException Thrown if the step fails, a limit is exceeded, or promotion or
   *         staging cleanup fails.
   */
  private static int installStaged(Path target, StagingStep step,
      Function<Path, Path> selector) throws IOException {
    final Path staging = Files.createTempDirectory(target, STAGING_PREFIX);
    final int promoted;
    try {
      step.fill(staging);
      promoted = promote(staging, target, selector);
    } catch (IOException | RuntimeException e) {
      try {
        deleteRecursively(staging);
      } catch (IOException cleanup) {
        e.addSuppressed(cleanup);
      }
      throw e;
    }
    deleteRecursively(staging);
    return promoted;
  }

  /**
   * Moves the selected staged regular files to their destinations beneath the target
   * without replacing anything that already exists there. All destinations are
   * checked before the first move, so a collision leaves the target without a mix of
   * old and new files, and the move itself refuses an existing destination as well.
   *
   * @param staging The staging directory holding the fully unpacked content.
   * @param target The directory to install into.
   * @param selector Maps a staged file's relative path to its relative destination, or
   *                 to {@code null} to skip it.
   * @return The number of files moved.
   * @throws IOException Thrown if a destination leaves the target, two files map to the
   *         same destination, a destination already exists, a move fails, or a directory
   *         on the way to a destination is an existing symbolic link.
   */
  private static int promote(Path staging, Path target, Function<Path, Path> selector)
      throws IOException {
    final List<Path> files;
    try (Stream<Path> walk = Files.walk(staging)) {
      files = walk.filter(Files::isRegularFile).toList();
    }
    final Map<Path, Path> moves = new LinkedHashMap<>();
    for (final Path file : files) {
      final Path relative = selector.apply(staging.relativize(file));
      if (relative == null) {
        continue;
      }
      requireInsideTarget(relative);
      if (moves.putIfAbsent(relative, file) != null) {
        throw new IOException("two staged files install to the same path: " + relative);
      }
    }
    for (final Path relative : moves.keySet()) {
      ensureVacant(target, relative);
    }
    for (final Map.Entry<Path, Path> move : moves.entrySet()) {
      moveIntoPlace(move.getValue(), destination(target, move.getKey()));
    }
    return moves.size();
  }

  /**
   * Checks that a selected destination stays beneath the target: it must be relative
   * without a root, non-empty, and normalized without a leading {@code ..}, so it has no
   * {@code .} or {@code ..} segments. A root without a drive, such as {@code \x} on
   * Windows, is not absolute but still resolves outside the target.
   *
   * @param relative The file's destination path relative to the target.
   * @throws IOException Thrown if the destination is absolute, has a root, is empty, or is
   *     not normalized.
   */
  private static void requireInsideTarget(Path relative) throws IOException {
    if (relative.isAbsolute() || relative.getRoot() != null || relative.toString().isEmpty()
        || !relative.normalize().equals(relative) || relative.startsWith(PARENT_DIRECTORY)) {
      throw new IOException("selected destination leaves the target: " + relative);
    }
  }

  /**
   * Moves one staged file to its destination without replacing an existing file. The
   * move is not requested atomically: on POSIX filesystems an atomic move renames over
   * an existing destination, which would void the vacancy check.
   *
   * @param file The staged file.
   * @param destination The destination beneath the target.
   * @throws IOException Thrown if the destination exists or the move fails.
   */
  static void moveIntoPlace(Path file, Path destination) throws IOException {
    Files.move(file, destination);
  }

  /**
   * Checks that one staged file's destination is free to receive it, without creating
   * anything. A missing directory on the way proves the destination vacant.
   *
   * @param target The directory to install into.
   * @param relative The file's destination path relative to the target.
   * @throws IOException Thrown if the destination already exists, or a directory on
   *         the way is a symbolic link or exists as something other than a directory.
   */
  private static void ensureVacant(Path target, Path relative) throws IOException {
    final Path destination = destination(target, relative, false);
    if (destination != null && Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("target already contains: " + destination);
    }
  }

  /**
   * Resolves one staged file's destination beneath the target, creating the directories
   * leading to it one at a time.
   *
   * @param target The directory to install into.
   * @param relative The file's destination path relative to the target.
   * @return The destination path beneath the target. Not {@code null}.
   * @throws IOException Thrown if a directory on the way is a symbolic link or exists as
   *         something other than a directory, or if a directory cannot be created.
   */
  private static Path destination(Path target, Path relative) throws IOException {
    return destination(target, relative, true);
  }

  /**
   * Walks the directories leading to one staged file's destination without descending
   * through a symbolic link that is already there. An entry name that stays inside the
   * staging directory can still land outside the target if a directory below the target
   * is a link to somewhere else.
   *
   * <p>This covers links present when the installation runs. It is not a defense against
   * a link created concurrently, between the check here and the move that follows.</p>
   *
   * @param target The directory to install into.
   * @param relative The file's destination path relative to the target.
   * @param create Whether to create a missing directory on the way; when {@code false},
   *               a missing directory ends the walk.
   * @return The destination path beneath the target, or {@code null} when a directory
   *         on the way is missing and {@code create} is {@code false}.
   * @throws IOException Thrown if a directory on the way is a symbolic link or exists as
   *         something other than a directory, or if a directory cannot be created.
   */
  private static Path destination(Path target, Path relative, boolean create)
      throws IOException {
    Path directory = target;
    for (int i = 0; i < relative.getNameCount() - 1; i++) {
      directory = directory.resolve(relative.getName(i));
      if (Files.isSymbolicLink(directory)) {
        throw new IOException(
            "installation path crosses a symbolic link: " + directory);
      }
      if (!Files.exists(directory)) {
        if (!create) {
          return null;
        }
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
   * bounding the total expanded bytes against the expansion limit. One exception: a
   * source named {@code *.bin}, in any letter case, is stored verbatim even when its
   * bytes are a zip archive. OpenNLP model consumers load the packed zip artifact. Unpacking it would
   * place the internal entries ({@code manifest.properties}, {@code *.model}) in the
   * target instead of the model.
   *
   * @param downloaded The fetched file.
   * @param name The file name derived from the source location.
   * @param staging The staging directory to unpack into.
   * @param limits The limits to enforce.
   * @throws IOException Thrown if reading or unpacking fails or the expansion limit
   *         is exceeded.
   */
  private static void unpack(Path downloaded, String name, Path staging, Limits limits)
      throws IOException {
    final Budget budget = new Budget(limits.maxExpandedBytes(),
        "expanded content exceeds the limit of " + limits.maxExpandedBytes()
            + " bytes");
    final Budget entryBudget = new Budget(limits.maxEntries(),
        "archive entry count exceeds the limit of " + limits.maxEntries()
            + " entries");
    try (InputStream raw = new BufferedInputStream(Files.newInputStream(downloaded))) {
      raw.mark(MAGIC_LENGTH);
      final byte[] magic = raw.readNBytes(MAGIC_LENGTH);
      raw.reset();
      if (endsWithIgnoreCase(name, MODEL_SUFFIX)) {
        copyBounded(raw, safeChild(staging, name), budget);
      } else if (hasMagic(magic, GZIP_MAGIC_FIRST, GZIP_MAGIC_SECOND)) {
        unpackGzip(raw, name, staging,
            expansionBudget(Files.size(downloaded), limits, budget), entryBudget);
      } else if (hasMagic(magic, ZIP_MAGIC_FIRST, ZIP_MAGIC_SECOND,
          ZIP_LOCAL_HEADER_THIRD, ZIP_LOCAL_HEADER_FOURTH)) {
        final Set<String> unpacked = unpackZip(raw, staging,
            expansionBudget(Files.size(downloaded), limits, budget), entryBudget);
        validateZip(downloaded, unpacked);
      } else if (hasMagic(magic, ZIP_MAGIC_FIRST, ZIP_MAGIC_SECOND,
          ZIP_END_HEADER_THIRD, ZIP_END_HEADER_FOURTH)) {
        validateEmptyZip(raw);
      } else {
        copyBounded(raw, safeChild(staging, name), budget);
      }
    }
  }

  /**
   * Bounds expansion by the ratio as well as the absolute limit, so a small source
   * cannot expand to the whole absolute limit. Deflate reaches roughly 1000 to 1, so
   * the absolute limit alone lets a few megabytes fill the target filesystem.
   *
   * @param compressedSize The size of the compressed source in bytes.
   * @param limits The limits holding the accepted expansion ratio.
   * @param budget The expansion budget under the absolute limit.
   * @return The tighter of the two budgets. Not {@code null}.
   */
  private static Budget expansionBudget(long compressedSize, Limits limits,
      Budget budget) {
    final long ratio = limits.maxExpansionRatio();
    final long ratioCeiling = compressedSize > Long.MAX_VALUE / ratio
        ? Long.MAX_VALUE
        : Math.max(MIN_EXPANSION_BYTES, compressedSize * ratio);
    if (ratioCeiling >= budget.limit()) {
      return budget;
    }
    return new Budget(ratioCeiling, "content expands beyond "
        + ratio + " times its compressed size");
  }

  /**
   * Compares a name's ending with a suffix, ignoring letter case.
   *
   * @param name The file name.
   * @param suffix The suffix to look for.
   * @return {@code true} if the name ends with the suffix in any letter case.
   */
  private static boolean endsWithIgnoreCase(String name, String suffix) {
    return name.length() >= suffix.length() && name.regionMatches(true,
        name.length() - suffix.length(), suffix, 0, suffix.length());
  }

  /**
   * Checks whether the bytes at the start of a resource match the given signature.
   *
   * @param actual The bytes read from the resource.
   * @param expected The unsigned byte values in the signature.
   * @return {@code true} when the resource begins with the expected values.
   */
  private static boolean hasMagic(byte[] actual, int... expected) {
    if (actual.length < expected.length) {
      return false;
    }
    for (int i = 0; i < expected.length; i++) {
      if ((actual[i] & 0xFF) != expected[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks that a zip archive contains a valid central directory listing the same files
   * the local headers delivered, before staged content is promoted. The two listings
   * can disagree in a crafted archive, and the local headers are what was unpacked.
   *
   * @param archive The downloaded archive.
   * @param unpacked The names of the file entries read from the local headers.
   * @throws IOException Thrown if the archive is malformed, the listings differ, or the
   *         archive cannot be read.
   */
  private static void validateZip(Path archive, Set<String> unpacked) throws IOException {
    final Set<String> listed = new HashSet<>();
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      final Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        final ZipEntry entry = entries.nextElement();
        if (!entry.isDirectory()) {
          listed.add(entry.getName());
        }
      }
    } catch (UnsupportedOperationException e) {
      listed.addAll(listZipOnNonDefaultFileSystem(archive));
    } catch (ZipException e) {
      throw new IOException(MALFORMED_ZIP_ERROR, e);
    }
    if (!listed.equals(unpacked)) {
      throw new IOException(ZIP_MISMATCH_ERROR);
    }
  }

  /**
   * Lists the file entries of an archive stored by a file-system provider that cannot
   * supply a {@link java.io.File} to {@link ZipFile}.
   *
   * @param archive The downloaded archive.
   * @return The file entry names from the central directory. Not {@code null}.
   * @throws IOException Thrown if the archive is malformed or cannot be read.
   */
  private static Set<String> listZipOnNonDefaultFileSystem(Path archive)
      throws IOException {
    final Set<String> listed = new HashSet<>();
    try (FileSystem zip = FileSystems.newFileSystem(archive)) {
      for (final Path root : zip.getRootDirectories()) {
        try (Stream<Path> walk = Files.walk(root)) {
          walk.filter(Files::isRegularFile)
              .map(path -> root.relativize(path).toString())
              .forEach(listed::add);
        }
      }
    } catch (ZipException | ProviderNotFoundException e) {
      throw new IOException(MALFORMED_ZIP_ERROR, e);
    }
    return listed;
  }

  /**
   * Validates an empty zip archive from its end-of-central-directory record. An empty
   * archive has no local entry headers for {@link ZipInputStream} to validate.
   *
   * @param raw The zip content, positioned at its first byte.
   * @throws IOException Thrown if the record is truncated, declares entries or central
   *         directory data, or has bytes beyond its declared comment.
   */
  private static void validateEmptyZip(InputStream raw) throws IOException {
    final byte[] header = raw.readNBytes(ZIP_END_HEADER_LENGTH);
    if (header.length != ZIP_END_HEADER_LENGTH
        || littleEndianShort(header, ZIP_DISK_OFFSET) != 0
        || littleEndianShort(header, ZIP_CENTRAL_DISK_OFFSET) != 0
        || littleEndianShort(header, ZIP_DISK_ENTRIES_OFFSET) != 0
        || littleEndianShort(header, ZIP_TOTAL_ENTRIES_OFFSET) != 0
        || littleEndianInt(header, ZIP_CENTRAL_SIZE_OFFSET) != 0
        || littleEndianInt(header, ZIP_CENTRAL_OFFSET_OFFSET) != 0) {
      throw new IOException(MALFORMED_ZIP_ERROR);
    }
    final int commentLength = littleEndianShort(header, ZIP_COMMENT_LENGTH_OFFSET);
    if (raw.readNBytes(commentLength).length != commentLength || raw.read() >= 0) {
      throw new IOException(MALFORMED_ZIP_ERROR);
    }
  }

  /**
   * Reads an unsigned 16-bit little-endian value.
   *
   * @param bytes The source bytes.
   * @param offset The first byte to read.
   * @return The decoded value.
   */
  private static int littleEndianShort(byte[] bytes, int offset) {
    return bytes[offset] & 0xFF | (bytes[offset + 1] & 0xFF) << 8;
  }

  /**
   * Reads an unsigned 32-bit little-endian value.
   *
   * @param bytes The source bytes.
   * @param offset The first byte to read.
   * @return The decoded value.
   */
  private static long littleEndianInt(byte[] bytes, int offset) {
    return littleEndianShort(bytes, offset)
        | (long) littleEndianShort(bytes, offset + 2) << 16;
  }

  /**
   * Unpacks gzip content: a tar archive inside when present, a plain file otherwise. A
   * plain file omits the {@code .gz} suffix of its source name, in any letter case. If the source name is
   * only that suffix, the installed file is named {@value #DEFAULT_RESOURCE_NAME}.
   *
   * @param raw The gzip-compressed content.
   * @param name The file name derived from the source location.
   * @param staging The staging directory to unpack into.
   * @param budget The expansion budget.
   * @param entryBudget The entry-count budget.
   * @throws IOException Thrown if decompressing or unpacking fails or a limit is
   *         exceeded.
   */
  private static void unpackGzip(InputStream raw, String name, Path staging,
      Budget budget, Budget entryBudget) throws IOException {
    final InputStream decompressed = new BufferedInputStream(
        new BudgetInputStream(new GZIPInputStream(raw), budget), BUFFER_SIZE);
    if (TarStream.startsWithHeader(decompressed)
        || TarStream.isEmptyArchive(decompressed)) {
      unpackTar(decompressed, staging, entryBudget);
      decompressed.transferTo(OutputStream.nullOutputStream());
    } else {
      final String strippedName = endsWithIgnoreCase(name, GZIP_SUFFIX)
          ? name.substring(0, name.length() - GZIP_SUFFIX.length()) : name;
      final String plainName = strippedName.isEmpty()
          ? DEFAULT_RESOURCE_NAME : strippedName;
      copy(decompressed, safeChild(staging, plainName));
    }
  }

  /**
   * Unpacks every regular tar entry to its relative location beneath the staging
   * directory.
   *
   * @param decompressed The uncompressed tar content.
   * @param staging The staging directory to unpack into.
   * @param entryBudget The archive-header limit.
   * @throws IOException Thrown if the archive is malformed, an entry escapes the
   *         staging directory, or the entry limit is exceeded.
   */
  private static void unpackTar(InputStream decompressed, Path staging,
      Budget entryBudget) throws IOException {
    final TarStream entries = new TarStream(decompressed, entryBudget.limit());
    while (entries.next()) {
      if (!entries.isFile()) {
        safeChild(staging, entries.name());
        continue;
      }
      final Path file = newArchiveFile(staging, entries.name());
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
   * @return The names of the file entries unpacked. Not {@code null}.
   * @throws IOException Thrown if the archive is malformed, an entry escapes the
   *         staging directory, or a limit is exceeded.
   */
  private static Set<String> unpackZip(InputStream raw, Path staging, Budget budget,
      Budget entryBudget) throws IOException {
    final ZipInputStream zip = new ZipInputStream(raw);
    final Set<String> unpacked = new HashSet<>();
    boolean foundEntry = false;
    ZipEntry entry;
    while ((entry = zip.getNextEntry()) != null) {
      foundEntry = true;
      entryBudget.spend(1);
      if (entry.isDirectory()) {
        safeChild(staging, entry.getName());
        consumeBounded(zip, budget);
        continue;
      }
      final Path file = newArchiveFile(staging, entry.getName());
      copyBounded(zip, file, budget);
      unpacked.add(entry.getName());
    }
    if (!foundEntry) {
      throw new IOException(MALFORMED_ZIP_ERROR);
    }
    return unpacked;
  }

  /**
   * Resolves a file entry beneath the staging directory and creates its parent
   * directories. A second entry that normalizes to the same path is rejected.
   *
   * @param staging The staging directory.
   * @param entryName The path stored in the archive.
   * @return The new file path. Not {@code null}.
   * @throws IOException Thrown if the path escapes the staging directory, duplicates
   *         another file entry, or its parent directories cannot be created.
   */
  private static Path newArchiveFile(Path staging, String entryName) throws IOException {
    final Path file = safeChild(staging, entryName);
    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("archive contains duplicate file entry: " + entryName);
    }
    Files.createDirectories(file.getParent());
    return file;
  }

  /**
   * Copies the stream into the file, charging every byte against the budget before it
   * is written, so an oversized transfer aborts within one buffer of its limit.
   *
   * @param in The content to copy.
   * @param file The file to write.
   * @param budget The byte budget to charge.
   * @throws IOException Thrown if reading or writing fails or the budget is exceeded.
   */
  private static void copyBounded(InputStream in, Path file, Budget budget)
      throws IOException {
    try (OutputStream out = Files.newOutputStream(file)) {
      new BudgetInputStream(in, budget).transferTo(out);
    }
  }

  /**
   * Reads and discards an entry while charging each byte against the expansion limit.
   *
   * @param in The entry content.
   * @param budget The byte budget to charge.
   * @throws IOException Thrown if reading fails or the budget is exceeded.
   */
  private static void consumeBounded(InputStream in, Budget budget) throws IOException {
    new BudgetInputStream(in, budget).transferTo(OutputStream.nullOutputStream());
  }

  /**
   * Copies the stream into the file. The input stream must already enforce any byte
   * limit.
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
   * @return The resolved path beneath the staging directory. Not {@code null}.
   * @throws IOException Thrown if the entry resolves outside the staging directory.
   */
  private static Path safeChild(Path staging, String entryName) throws IOException {
    final Path resolved;
    try {
      resolved = staging.resolve(entryName).normalize();
    } catch (InvalidPathException e) {
      throw new IOException("archive entry has an invalid path: " + entryName, e);
    }
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
   * Rejects names that are empty, path-like, or contain a NUL character.
   *
   * @param name The candidate local file name.
   * @return The validated name.
   * @throws IllegalArgumentException Thrown if {@code name} is not a file name.
   */
  static String validateSourceName(String name) {
    if (name.isEmpty() || ".".equals(name) || PARENT_DIRECTORY.equals(name)
        || containsPathCharacter(name)) {
      throw new IllegalArgumentException("name must be a file name");
    }
    return name;
  }

  /**
   * Checks a name for a path separator or a NUL character.
   *
   * @param name The candidate local file name.
   * @return {@code true} if {@code name} contains a slash, a backslash, or NUL.
   */
  private static boolean containsPathCharacter(String name) {
    for (int i = 0; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (c == SLASH || c == BACKSLASH || c == NUL) {
        return true;
      }
    }
    return false;
  }

  /**
   * A unit budget, counting bytes or archive entries: {@link #spend(long)} accumulates
   * spent units and fails once the limit is crossed.
   */
  private static final class Budget {

    private final long limit;
    private final String message;
    private long used;

    /**
     * Creates a budget that has spent zero units.
     *
     * @param limit The largest total number of units accepted.
     * @param message The failure message raised when the limit is crossed.
     */
    Budget(long limit, String message) {
      this.limit = limit;
      this.message = message;
    }

    /** {@return the maximum number of units accepted} */
    long limit() {
      return limit;
    }

    /**
     * Charges the given number of units against the budget.
     *
     * @param units The number of units to charge.
     * @throws IOException Thrown if the total charged units exceed the limit.
     */
    void spend(long units) throws IOException {
      used += units;
      if (used > limit) {
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
