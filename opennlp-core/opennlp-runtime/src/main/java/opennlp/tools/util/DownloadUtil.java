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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.commons.Internal;
import opennlp.tools.models.ModelType;
import opennlp.tools.util.model.BaseModel;

/**
 * Downloads remote resources into a local path: pretrained OpenNLP models, and any
 * other file fetched through {@link #download(URI, Path, String)} with an expected
 * SHA-512 digest.
 */
public class DownloadUtil {

  private static final Logger logger = LoggerFactory.getLogger(DownloadUtil.class);

  private static final String BASE_URL =
      System.getProperty("OPENNLP_DOWNLOAD_BASE_URL", "https://dlcdn.apache.org/opennlp/");
  private static final String MODEL_URI_PATH =
      System.getProperty("OPENNLP_DOWNLOAD_MODEL_PATH", "models/ud-models-1.3/");
  private static final String OPENNLP_DOWNLOAD_HOME = "OPENNLP_DOWNLOAD_HOME";
  private static final String CHECKSUM_EXTENSION = ".sha512";

  /**
   * System property that must be {@code true} before a
   * {@link DictionaryCatalog} entry may be fetched. Explicit
   * {@link #download(URI, Path, String)} calls do not require it: the caller already
   * supplied the URI and digest.
   */
  public static final String REMOTE_DOWNLOAD_PROPERTY = "opennlp.download.remote";

  /**
   * System property for overriding {@link #MAX_DOWNLOAD_BYTES}. Set at JVM startup,
   * e.g. {@code -Dopennlp.download.max.bytes=2147483648} for dictionaries larger than
   * the default ceiling. Falls back to the default if absent, non-numeric, or not
   * positive.
   */
  public static final String MAX_DOWNLOAD_BYTES_PROPERTY = "opennlp.download.max.bytes";

  /**
   * Inclusive ceiling on bytes buffered for one {@link #download(URI, Path, String)},
   * 64 MiB unless overridden via {@link #MAX_DOWNLOAD_BYTES_PROPERTY}.
   */
  public static final long MAX_DOWNLOAD_BYTES =
      configuredLimit(MAX_DOWNLOAD_BYTES_PROPERTY, 64L * 1024 * 1024);

  private static final int CONNECT_TIMEOUT_MS = 30_000;
  private static final int READ_TIMEOUT_MS = 300_000;
  private static final int SHA512_HEX_LENGTH = 128;
  private static final String DOWNLOAD_SUFFIX = ".download";

  private static Map<String, Map<ModelType, URL>> availableModels;

  /**
   * Checks if a model of the specified {@code modelType} has been downloaded already
   * for a particular {@code language}.
   *
   * @param language  The ISO language code of the requested model.
   * @param modelType The {@link ModelType type} of model.
   * @return {@code true} if a model exists locally, {@code false} otherwise.
   * @throws IOException Thrown if IO errors occurred or the computed hash sum
   *                     of an associated, local model file was incorrect.
   */
  static boolean existsModel(String language, ModelType modelType) throws IOException {
    Map<ModelType, URL> modelsByLanguage = getAvailableModels().get(language);
    if (modelsByLanguage == null) {
      return false;
    } else {
      final URL url = modelsByLanguage.get(modelType);
      if (url != null) {
        final Path homeDirectory = getDownloadHome();
        final String extUrl = url.toExternalForm();
        final String filename = extUrl.substring(extUrl.lastIndexOf("/") + 1);
        final Path localFile = homeDirectory.resolve(filename);
        boolean exists;
        if (Files.exists(localFile)) {
          // if this does not throw the requested model is valid!
          validateCachedModel(url + CHECKSUM_EXTENSION, localFile);
          exists = true;
        } else {
          exists = false;
        }
        return exists;
      } else {
        return false;
      }
    }
  }

  /**
   * Triggers a download for the specified {@link ModelType}.
   *
   * @param language  The ISO language code of the requested model.
   * @param modelType The {@link ModelType type} of model.
   * @param type      The class of the resulting model.
   * @param <T>       The generic type which is a subclass of {@link BaseModel}.
   * @return A model instance of type {@link T}.
   * @throws IOException Thrown if IO errors occurred or the model is invalid.
   */
  public static <T extends BaseModel> T downloadModel(String language, ModelType modelType,
                                                      Class<T> type) throws IOException {

    if (getAvailableModels().containsKey(language)) {
      final URL url = getAvailableModels().get(language).get(modelType);
      if (url != null) {
        return downloadModel(url, type);
      }
    }

    throw new IOException("There is no model available: " + language + " " + modelType.getName());
  }

  /**
   * Downloads a model from a {@link URL}.
   * <p>
   * The model is saved to an {@code .opennlp/} directory
   * located in the user's home directory. This directory will be created
   * if it does not already exist. If a model to be downloaded already
   * exists in that directory, the model will not be re-downloaded, but it is
   * verified against its SHA-512 checksum before it is loaded.
   *
   * @param url  The model's {@link URL}.
   * @param type The class of the resulting model {@link T}.
   * @param <T>  The generic type which is a subclass of {@link BaseModel}.
   * @return A model instance of type {@link T}.
   * @throws IOException Thrown if the model cannot be downloaded.
   */
  public static <T extends BaseModel> T downloadModel(URL url, Class<T> type) throws IOException {

    final Path homeDirectory = getDownloadHome();

    if (!Files.isDirectory(homeDirectory)) {
      try {
        Files.createDirectories(homeDirectory);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    final String filename = url.toString().substring(url.toString().lastIndexOf("/") + 1);
    final Path localFile = homeDirectory.resolve(filename);

    if (!Files.exists(localFile)) {
      logger.debug("Downloading model to {}.", localFile);

      try (final InputStream in = url.openStream()) {
        Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
      }
      validateModel(url + CHECKSUM_EXTENSION, localFile);
      logger.debug("Download complete.");
    } else {
      logger.debug("Model file '{}' already exists. Skipping download.", filename);
      validateCachedModel(url + CHECKSUM_EXTENSION, localFile);
    }

    try {
      return type.getConstructor(Path.class).newInstance(localFile);
    } catch (Exception e) {
      throw new IOException("Could not initialize Model of type " + type.getTypeName(), e);
    }
  }

  /**
   * Downloads {@code source} into {@code target} and requires the SHA-512 digest of the
   * stored bytes to equal {@code expectedSha512}. The download is written to a sibling
   * temporary file and moved into place only after the digest matches. The transfer is
   * capped at {@link #MAX_DOWNLOAD_BYTES}; remote {@code http} and {@code https} URIs
   * additionally use connect and read timeouts.
   *
   * @param source The absolute URI to fetch. Must not be {@code null}.
   * @param target The local file to create or replace. Must not be {@code null}.
   * @param expectedSha512 The expected SHA-512 digest as 128 lowercase or uppercase hex
   *                       digits. Must not be {@code null}.
   * @throws IOException Thrown if fetching fails, the size ceiling is exceeded, or the
   *         digest does not match.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}, {@code source}
   *         is not absolute, or {@code expectedSha512} is not 128 hex digits.
   */
  public static void download(URI source, Path target, String expectedSha512)
      throws IOException {
    download(source, target, expectedSha512, MAX_DOWNLOAD_BYTES);
  }

  /**
   * Downloads {@code source} into {@code target} under a caller-supplied byte ceiling.
   *
   * @param source The absolute URI to fetch. Must not be {@code null}.
   * @param target The local file to create or replace. Must not be {@code null}.
   * @param expectedSha512 The expected SHA-512 digest as 128 hex digits. Must not be
   *                       {@code null}.
   * @param maxBytes The inclusive ceiling on bytes read from {@code source}.
   * @throws IOException Thrown if fetching fails, {@code maxBytes} is exceeded, or the
   *         digest does not match.
   * @throws IllegalArgumentException Thrown if a parameter is invalid, see
   *         {@link #download(URI, Path, String)}.
   */
  static void download(URI source, Path target, String expectedSha512, long maxBytes)
      throws IOException {
    if (source == null) {
      throw new IllegalArgumentException("source must not be null");
    }
    if (target == null) {
      throw new IllegalArgumentException("target must not be null");
    }
    if (expectedSha512 == null) {
      throw new IllegalArgumentException("expectedSha512 must not be null");
    }
    if (!source.isAbsolute()) {
      throw new IllegalArgumentException("source must be an absolute URI");
    }
    final String normalized = normalizeSha512(expectedSha512);
    final Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    final Path partial = target.resolveSibling(target.getFileName() + DOWNLOAD_SUFFIX);
    Files.deleteIfExists(partial);
    try {
      long size = 0L;
      final MessageDigest digest = sha512Digest();
      final URLConnection connection = open(source);
      try (InputStream in = connection.getInputStream();
           DigestInputStream digester = new DigestInputStream(in, digest);
           OutputStream out = Files.newOutputStream(partial)) {
        final byte[] buffer = new byte[8192];
        int n;
        while ((n = digester.read(buffer)) >= 0) {
          size += n;
          if (size > maxBytes) {
            throw new IOException("download size exceeds safe limit of " + maxBytes);
          }
          out.write(buffer, 0, n);
        }
      } finally {
        if (connection instanceof HttpURLConnection http) {
          http.disconnect();
        }
      }
      final String actual = byteArrayToHexString(digest.digest());
      if (!actual.equals(normalized)) {
        throw new IOException("SHA512 checksum validation failed for " + target.getFileName()
            + ". Expected: " + normalized + ", but got: " + actual);
      }
      try {
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      Files.deleteIfExists(partial);
      throw e;
    }
  }

  /**
   * {@return {@code true} when {@link #REMOTE_DOWNLOAD_PROPERTY} is the string
   * {@code true}, ignoring case}
   */
  public static boolean isRemoteDownloadEnabled() {
    return Boolean.parseBoolean(System.getProperty(REMOTE_DOWNLOAD_PROPERTY));
  }

  /**
   * Reads a byte-budget override from a system property. Budget constants are
   * initialized from it once at class load, so overrides must be set at JVM startup.
   *
   * @param property The system property name to read.
   * @param fallback The value to use when the property is absent or invalid.
   * @return The property's value when it parses as a positive {@code long}, otherwise
   *     {@code fallback}.
   */
  public static long configuredLimit(String property, long fallback) {
    final String value = System.getProperty(property, "").trim();
    if (!value.isEmpty()) {
      try {
        final long parsed = Long.parseLong(value);
        if (parsed > 0) {
          return parsed;
        }
      } catch (NumberFormatException ignore) {
        // Fall through to the default.
      }
    }
    return fallback;
  }

  /**
   * Opens a connection to {@code source} with connect and read timeouts applied.
   *
   * @param source The absolute URI to connect to.
   * @return The configured, not yet connected, connection.
   * @throws IOException Thrown if no connection can be created for {@code source}.
   */
  private static URLConnection open(URI source) throws IOException {
    final URLConnection connection = source.toURL().openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    return connection;
  }

  /**
   * Trims and lowercases a SHA-512 hex digest.
   *
   * @param expectedSha512 The digest to normalize.
   * @return The digest as 128 lowercase hex digits.
   * @throws IllegalArgumentException Thrown if the digest is not 128 hex digits.
   */
  private static String normalizeSha512(String expectedSha512) {
    final String hex = expectedSha512.trim().toLowerCase(Locale.ROOT);
    if (hex.length() != SHA512_HEX_LENGTH || !hex.chars().allMatch(
        c -> c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
      throw new IllegalArgumentException(
          "expectedSha512 must be 128 hexadecimal digits");
    }
    return hex;
  }

  /**
   * {@return a fresh SHA-512 {@link MessageDigest}}
   *
   * @throws IOException Thrown if the JVM does not provide the algorithm.
   */
  private static MessageDigest sha512Digest() throws IOException {
    try {
      return MessageDigest.getInstance("SHA-512");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-512 algorithm not found", e);
    }
  }

  public static Map<String, Map<ModelType, URL>> getAvailableModels() {
    if (availableModels == null) {
      try {
        DownloadParser p = new DownloadParser(new URI(BASE_URL + MODEL_URI_PATH).toURL());
        availableModels = p.getAvailableModels();
      } catch (MalformedURLException | URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
    return Collections.unmodifiableMap(availableModels);
  }

  /**
   * Validates a freshly downloaded model via the specified {@link Path downloadedModel path}
   * and stores the expected checksum next to it, so that subsequent loads of the cached file
   * can be verified without contacting the CDN again.
   *
   * @param sha512          the url to get the sha512 hash
   * @param downloadedModel the model file to check
   * @throws IOException thrown if the checksum could not be computed or did not match
   */
  private static void validateModel(String sha512, Path downloadedModel) throws IOException {
    final String checksumFile = downloadChecksumFile(sha512, downloadedModel);
    verifyChecksum(downloadedModel, parseChecksum(checksumFile));
    storeChecksumFile(downloadedModel, checksumFile);
  }

  /**
   * Validates a model that is already present in the download home.
   * <p>
   * The expected checksum stored by a previous download is used, so no network access is
   * required on this path. When no checksum was stored - the download home was populated by
   * an OpenNLP version that predates this check - the published checksum is fetched once and
   * then stored. If it cannot be retrieved, the model is loaded and a warning is logged,
   * which preserves the behaviour of those earlier versions for offline environments.
   *
   * @param sha512      the url to get the sha512 hash
   * @param cachedModel the cached model file to check
   * @throws IOException thrown if the checksum could not be computed or did not match
   */
  private static void validateCachedModel(String sha512, Path cachedModel) throws IOException {
    final Path checksumFile = checksumPathFor(cachedModel);

    if (Files.exists(checksumFile)) {
      verifyChecksum(cachedModel, parseChecksum(Files.readString(checksumFile, StandardCharsets.UTF_8)));
      return;
    }

    final String publishedChecksumFile;
    try {
      publishedChecksumFile = downloadChecksumFile(sha512, cachedModel);
    } catch (IOException e) {
      logger.warn("Could not retrieve the expected checksum for cached model '{}'. "
          + "Its integrity has not been verified.", cachedModel.getFileName(), e);
      return;
    }

    verifyChecksum(cachedModel, parseChecksum(publishedChecksumFile));
    storeChecksumFile(cachedModel, publishedChecksumFile);
  }

  /**
   * Retrieves the published {@code ".sha512"} file. Its content is returned unmodified so that
   * the copy stored next to the model stays interchangeable with the published one.
   */
  private static String downloadChecksumFile(String sha512, Path model) throws IOException {
    try {
      // Download SHA512 checksum file
      final URL hashSum = new URI(sha512).toURL();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(hashSum.openStream()))) {
        return reader.readLine();
      }
    } catch (URISyntaxException use) {
      throw new IOException("Expected SHA512 checksum could not be retrieved for " +
          model.getFileName(), use);
    }
  }

  /**
   * Extracts the hash from the content of a checksum file, which holds the hash followed by the
   * name of the file it applies to.
   */
  private static String parseChecksum(String checksumFileContent) {
    if (checksumFileContent == null) {
      return null;
    }
    final String trimmed = checksumFileContent.trim();
    return trimmed.isEmpty() ? null : trimmed.split("\\s")[0];
  }

  private static void verifyChecksum(Path model, String expectedChecksum) throws IOException {
    final String actualChecksum = calculateSHA512(model);
    if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
      throw new IOException("SHA512 checksum validation failed for " + model.getFileName() +
          ". Expected: " + expectedChecksum + ", but got: " + actualChecksum);
    }
  }

  /**
   * Stores the published checksum file alongside the model. A failure to do so is not fatal:
   * it only means the next load falls back to retrieving the published checksum again.
   */
  private static void storeChecksumFile(Path model, String checksumFileContent) {
    final Path checksumFile = checksumPathFor(model);
    try {
      Files.writeString(checksumFile, checksumFileContent, StandardCharsets.UTF_8);
    } catch (IOException e) {
      logger.warn("Could not store the expected checksum at {}.", checksumFile, e);
    }
  }

  private static Path checksumPathFor(Path model) {
    return model.resolveSibling(model.getFileName() + CHECKSUM_EXTENSION);
  }

  private static String calculateSHA512(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-512");
      try (InputStream fis = Files.newInputStream(file);
           DigestInputStream dis = new DigestInputStream(fis, digest)) {
        byte[] buffer = new byte[4096];
        //noinspection StatementWithEmptyBody
        while (dis.read(buffer) != -1) {
          // Reading the file to update the digest
        }
      }
      return byteArrayToHexString(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-512 algorithm not found", e);
    }
  }

  private static String byteArrayToHexString(byte[] bytes) {
    try (Formatter formatter = new Formatter()) {
      for (byte b : bytes) {
        formatter.format("%02x", b);
      }
      return formatter.toString();
    }
  }

  private static Path getDownloadHome() {
    return Paths.get(System.getProperty(OPENNLP_DOWNLOAD_HOME,
            System.getProperty("user.home"))).resolve(".opennlp");
  }

  @Internal
  static class DownloadParser {

    private static final Pattern LINK_PATTERN = Pattern.compile("<a href=\\\"(.*?)\\\">(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private final URL indexUrl;

    DownloadParser(URL indexUrl) {
      Objects.requireNonNull(indexUrl);
      this.indexUrl = indexUrl;
    }

    Map<String, Map<ModelType, URL>> getAvailableModels()
        throws MalformedURLException, URISyntaxException {
      final Matcher matcher = LINK_PATTERN.matcher(fetchPageIndex());

      final List<String> links = new ArrayList<>();
      while (matcher.find()) {
        links.add(matcher.group(1));
      }

      return toMap(links);
    }

    private Map<String, Map<ModelType, URL>> toMap(List<String> links)
        throws MalformedURLException, URISyntaxException {
      final Map<String, Map<ModelType, URL>> result = new HashMap<>();
      for (String link : links) {
        if (link.endsWith(".bin")) {
          if (link.contains("de-ud")) { // German
            addModel("de", link, result);
          } else if (link.contains("en-ud")) { // English
            addModel("en", link, result);
          } else if (link.contains("it-ud")) { // Italian
            addModel("it", link, result);
          } else if (link.contains("nl-ud")) { // Dutch
            addModel("nl", link, result);
          } else if (link.contains("fr-ud")) { // French
            addModel("fr", link, result);
          } else if (link.contains("af-ud")) { // Afrikaans
            addModel("af", link, result);
          } else if (link.contains("bg-ud")) { // Bulgarian
            addModel("bg", link, result);
          } else if (link.contains("ca-ud")) { // Catalan
            addModel("ca", link, result);
          } else if (link.contains("cs-ud")) { // Czech
            addModel("cs", link, result);
          } else if (link.contains("hr-ud")) { // Croatian
            addModel("hr", link, result);
          } else if (link.contains("da-ud")) { // Danish
            addModel("da", link, result);
          } else if (link.contains("el-ud")) { // Greek
            addModel("el", link, result);
          } else if (link.contains("es-ud")) { // Spanish
            addModel("es", link, result);
          } else if (link.contains("et-ud")) { // Estonian
            addModel("et", link, result);
          } else if (link.contains("eu-ud")) { // Basque
            addModel("eu", link, result);
          } else if (link.contains("fa-ud")) { // Persian
            addModel("fa", link, result);
          } else if (link.contains("fi-ud")) { // Finnish
            addModel("fi", link, result);
          } else if (link.contains("ga-ud")) { // Irish
            addModel("ga", link, result);
          } else if (link.contains("hy-ud")) { // Armenian
            addModel("hy", link, result);
          } else if (link.contains("id-ud")) { // Indonesian
            addModel("id", link, result);
          } else if (link.contains("is-ud")) { // Icelandic
            addModel("is", link, result);
          } else if (link.contains("ka-ud")) { // Georgian
            addModel("ka", link, result);
          } else if (link.contains("kk-ud")) { // Kazakh
            addModel("kk", link, result);
          } else if (link.contains("ko-ud")) { // Korean
            addModel("ko", link, result);
          } else if (link.contains("lv-ud")) { // Latvian
            addModel("lv", link, result);
          } else if (link.contains("no-ud")) { // Norwegian
            addModel("no", link, result);
          } else if (link.contains("pl-ud")) { // Polish
            addModel("pl", link, result);
          } else if (link.contains("pt-ud")) { // Portuguese
            addModel("pt", link, result);
          } else if (link.contains("ro-ud")) { // Romanian
            addModel("ro", link, result);
          } else if (link.contains("ru-ud")) { // Russian
            addModel("ru", link, result);
          } else if (link.contains("sr-ud")) { // Serbian
            addModel("sr", link, result);
          } else if (link.contains("sk-ud")) { // Slovak
            addModel("sk", link, result);
          } else if (link.contains("sl-ud")) { // Slovenian
            addModel("sl", link, result);
          } else if (link.contains("sv-ud")) { // Swedish
            addModel("sv", link, result);
          } else if (link.contains("tr-ud")) { // Turkish
            addModel("tr", link, result);
          } else if (link.contains("uk-ud")) { // Ukrainian
            addModel("uk", link, result);
          }
        }
      }
      return result;
    }

    private void addModel(String locale, String link, Map<String, Map<ModelType, URL>> result)
        throws URISyntaxException, MalformedURLException {
      final Map<ModelType, URL> models = result.getOrDefault(locale, new HashMap<>());
      final String combined = (indexUrl.toString().endsWith("/") ? indexUrl : indexUrl + "/") + link;
      final URL url = new URI(combined).toURL();
      if (link.contains("sentence")) {
        models.put(ModelType.SENTENCE_DETECTOR, url);
      } else if (link.contains("tokens")) {
        models.put(ModelType.TOKENIZER, url);
      } else if (link.contains("lemma")) {
        models.put(ModelType.LEMMATIZER, url);
      } else if (link.contains("pos")) {
        models.put(ModelType.POS, url);
      }

      result.putIfAbsent(locale, models);
    }

    private String fetchPageIndex() {
      final StringBuilder html = new StringBuilder();
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(indexUrl.openStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          html.append(line);
        }
      } catch (IOException e) {
        logger.error("Could not read page index from {}", indexUrl, e);
      }

      return html.toString();
    }
  }
}
