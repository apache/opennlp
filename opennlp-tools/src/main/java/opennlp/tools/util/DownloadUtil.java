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
import java.net.MalformedURLException;
import java.net.URL;
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
 * This class facilitates the downloading of pretrained OpenNLP models.
 */
public class DownloadUtil {

  private static final Logger logger = LoggerFactory.getLogger(DownloadUtil.class);

  private static final String BASE_URL =
          System.getProperty("OPENNLP_DOWNLOAD_BASE_URL", "https://dlcdn.apache.org/opennlp/");
  private static final String MODEL_URI_PATH =
          System.getProperty("OPENNLP_DOWNLOAD_MODEL_PATH", "models/ud-models-1.3/");
  private static final String OPENNLP_DOWNLOAD_HOME = "OPENNLP_DOWNLOAD_HOME";
  private static final String CHECKSUM_EXTENSION = ".sha512";
  private static final String INDEX_EXTENSION = ".index.html";
  private static final String TEMP_EXTENSION = ".tmp";

  private static Map<String, Map<ModelType, String>> availableModels;

  /**
   * Checks if a model of the specified {@code modelType} has been downloaded already
   * for a particular {@code language}.
   *
   * @param language  The ISO language code of the requested model.
   * @param modelType The {@link ModelType type} of model.
   * @return {@code true} if a model exists locally, {@code false} otherwise.
   * @throws IOException Thrown if IO errors occurred or the computed hash sum
   * of an associated, local model file was incorrect.
   */
  static boolean existsModel(String language, ModelType modelType) throws IOException {
    Map<ModelType, String> modelsByLanguage = getAvailableModels().get(language);
    if (modelsByLanguage == null) {
      return false;
    } else {
      final String url = modelsByLanguage.get(modelType);
      if (url != null) {
        final Path homeDirectory = getDownloadHome();
        final String filename = url.substring(url.lastIndexOf("/") + 1);
        final Path localFile = homeDirectory.resolve(filename);
        boolean exists;
        if (Files.exists(localFile)) {
          // if this does not throw the requested model is valid!
          validateCachedModel(new URL(url + CHECKSUM_EXTENSION), localFile);
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
      final String url = getAvailableModels().get(language).get(modelType);
      if (url != null) {
        return downloadModel(new URL(url), type);
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
      validateModel(new URL(url + CHECKSUM_EXTENSION), localFile);
      logger.debug("Download complete.");
    } else {
      logger.debug("Model file '{}' already exists. Skipping download.", filename);
      validateCachedModel(new URL(url + CHECKSUM_EXTENSION), localFile);
    }

    try {
      return type.getConstructor(Path.class).newInstance(localFile);
    } catch (Exception e) {
      throw new IOException("Could not initialize Model of type " + type.getTypeName(), e);
    }
  }

  /**
   * Lists the models published in the model index. A copy of the last index read is kept in
   * the download directory and used when the index cannot be read. An empty result is not
   * kept, so the next call reads the index again.
   *
   * @return The model URLs per language and {@link ModelType}. Empty if neither the index nor
   *     its local copy lists models.
   */
  public static Map<String, Map<ModelType, String>> getAvailableModels() {
    if (availableModels == null) {
      try {
        final DownloadParser p = new DownloadParser(new URL(BASE_URL + MODEL_URI_PATH),
            getDownloadHome().resolve(indexFileName(MODEL_URI_PATH)));
        final Map<String, Map<ModelType, String>> models = p.getAvailableModels();
        if (models.isEmpty()) {
          return Collections.emptyMap();
        }
        availableModels = models;
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
    return Collections.unmodifiableMap(availableModels);
  }

  /**
   * Validates a downloaded model against its published SHA-512 checksum and stores the
   * checksum file next to the model, so that later loads of the cached model can be verified
   * without network access.
   *
   * @param sha512          The {@link URL} of the published checksum file.
   * @param downloadedModel The model file to check.
   * @throws IOException Thrown if the checksum could not be retrieved or computed, or if it
   *     does not match.
   */
  private static void validateModel(URL sha512, Path downloadedModel) throws IOException {
    final String checksumFile = downloadChecksumFile(sha512);
    verifyChecksum(downloadedModel, parseChecksum(checksumFile));
    storeChecksumFile(downloadedModel, checksumFile);
  }

  /**
   * Validates a model that is already present in the download directory.
   * <p>
   * The checksum file stored by a previous download is used, so no network access is required.
   * If no checksum file is stored, the published checksum is retrieved, verified and stored.
   * If it cannot be retrieved, the model is accepted without verification and a warning is
   * logged.
   *
   * @param sha512      The {@link URL} of the published checksum file.
   * @param cachedModel The cached model file to check.
   * @throws IOException Thrown if the checksum could not be computed or does not match.
   */
  private static void validateCachedModel(URL sha512, Path cachedModel) throws IOException {
    final Path checksumFile = checksumPathFor(cachedModel);

    if (Files.exists(checksumFile)) {
      verifyChecksum(cachedModel,
          parseChecksum(Files.readString(checksumFile, StandardCharsets.UTF_8)));
      return;
    }

    final String publishedChecksumFile;
    try {
      publishedChecksumFile = downloadChecksumFile(sha512);
    } catch (IOException e) {
      logger.warn("Could not retrieve the expected checksum for cached model '{}'. "
          + "Its integrity has not been verified.", cachedModel.getFileName(), e);
      return;
    }

    verifyChecksum(cachedModel, parseChecksum(publishedChecksumFile));
    storeChecksumFile(cachedModel, publishedChecksumFile);
  }

  /**
   * Retrieves the first line of the published checksum file without modification.
   *
   * @param sha512 The {@link URL} of the published checksum file.
   * @return The first line, or {@code null} if the file is empty.
   * @throws IOException Thrown if the file could not be read.
   */
  private static String downloadChecksumFile(URL sha512) throws IOException {
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(sha512.openStream(), StandardCharsets.UTF_8))) {
      return reader.readLine();
    }
  }

  /**
   * Extracts the hash from the content of a checksum file, which holds the hash followed by
   * the name of the file it applies to.
   *
   * @param checksumFileContent The content of the checksum file, may be {@code null}.
   * @return The hash, or {@code null} if the content holds none.
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
   * Stores the checksum file next to the model. A failure is only logged; the next load then
   * retrieves the published checksum again.
   *
   * @param model               The model file.
   * @param checksumFileContent The content of the checksum file.
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

  /**
   * Names the local copy of the model index after the last segment of the model path, for
   * example {@code ud-models-1.3.index.html} for {@code models/ud-models-1.3/}. The base URL
   * is not part of the name, so hosts that serve the same model path share the copy.
   *
   * @param modelPath The model path, with or without trailing slashes.
   * @return The file name.
   */
  static String indexFileName(String modelPath) {
    String path = modelPath;
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path.substring(path.lastIndexOf('/') + 1) + INDEX_EXTENSION;
  }

  private static Path getDownloadHome() {
    return Paths.get(System.getProperty(OPENNLP_DOWNLOAD_HOME,
            System.getProperty("user.home"))).resolve(".opennlp");
  }

  @Internal
  static class DownloadParser {

    private static final Pattern LINK_PATTERN = Pattern.compile("<a href=\\\"(.*?)\\\">(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private final URL indexUrl;
    private final Path localIndex;

    /**
     * Initializes a parser that keeps no copy of the index page.
     *
     * @param indexUrl The index page.
     * @throws NullPointerException Thrown if {@code indexUrl} is {@code null}.
     */
    DownloadParser(URL indexUrl) {
      this(indexUrl, null);
    }

    /**
     * Initializes a parser that keeps a copy of the index page.
     *
     * @param indexUrl   The index page.
     * @param localIndex The file that keeps a copy of the index page, or {@code null} to
     *     keep no copy.
     * @throws NullPointerException Thrown if {@code indexUrl} is {@code null}.
     */
    DownloadParser(URL indexUrl, Path localIndex) {
      this.indexUrl = Objects.requireNonNull(indexUrl);
      this.localIndex = localIndex;
    }

    /**
     * Lists the models of the index page. If the page lists models, it is stored as the
     * local copy. Otherwise, for example if the page cannot be read, the local copy is used.
     *
     * @return The model URLs per language and {@link ModelType}. Empty if neither the page
     *     nor the local copy lists models.
     */
    Map<String, Map<ModelType, String>> getAvailableModels() {
      final String page = fetchPageIndex();
      if (page != null) {
        final Map<String, Map<ModelType, String>> models = toMap(extractLinks(page));
        if (!models.isEmpty()) {
          storeLocalIndex(page);
          return models;
        }
      }
      final String local = readLocalIndex();
      if (local == null) {
        return new HashMap<>();
      }
      logger.info("Using the local copy of the model index at {}.", localIndex);
      return toMap(extractLinks(local));
    }

    private List<String> extractLinks(String page) {
      final Matcher matcher = LINK_PATTERN.matcher(page);
      final List<String> links = new ArrayList<>();
      while (matcher.find()) {
        links.add(matcher.group(1));
      }
      return links;
    }

    /**
     * Reads the local copy of the index page.
     *
     * @return The page content, or {@code null} if there is no readable copy.
     */
    private String readLocalIndex() {
      if (localIndex == null || !Files.isRegularFile(localIndex)) {
        return null;
      }
      try {
        return Files.readString(localIndex, StandardCharsets.UTF_8);
      } catch (IOException e) {
        logger.warn("Could not read the local copy of the model index at {}.", localIndex, e);
        return null;
      }
    }

    /**
     * Stores the index page as the local copy. The page is written to a temporary file that
     * then replaces the copy, so a reader never sees a partly written copy. A failure is only
     * logged.
     *
     * @param page The page content.
     */
    private void storeLocalIndex(String page) {
      if (localIndex == null) {
        return;
      }
      try {
        final Path directory = localIndex.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        final Path temporary =
            Files.createTempFile(directory, localIndex.getFileName().toString(), TEMP_EXTENSION);
        try {
          Files.writeString(temporary, page, StandardCharsets.UTF_8);
          try {
            Files.move(temporary, localIndex, StandardCopyOption.ATOMIC_MOVE);
          } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, localIndex, StandardCopyOption.REPLACE_EXISTING);
          }
        } finally {
          Files.deleteIfExists(temporary);
        }
      } catch (IOException e) {
        logger.warn("Could not store the local copy of the model index at {}.", localIndex, e);
      }
    }

    private Map<String, Map<ModelType, String>> toMap(List<String> links) {
      final Map<String, Map<ModelType, String>> result = new HashMap<>();
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
          } else if (link.contains("is-ud")) { // Icelandic
            addModel("is", link, result);
          } else if (link.contains("id-ud")) { // Indonesian
            addModel("id", link, result);
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

    private void addModel(String locale, String link, Map<String, Map<ModelType, String>> result) {
      final Map<ModelType, String> models = result.getOrDefault(locale, new HashMap<>());
      final String url = (indexUrl.toString().endsWith("/") ? indexUrl : indexUrl + "/") + link;

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

    /**
     * Reads the index page from its URL, joining its lines.
     *
     * @return The page content, or {@code null} if the page could not be read completely.
     */
    private String fetchPageIndex() {
      final StringBuilder html = new StringBuilder();
      try (BufferedReader br = new BufferedReader(
              new InputStreamReader(indexUrl.openStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          html.append(line);
        }
      } catch (IOException e) {
        logger.warn("Could not read the model index from {}: {}", indexUrl, e.getMessage());
        return null;
      }

      return html.toString();
    }
  }
}
