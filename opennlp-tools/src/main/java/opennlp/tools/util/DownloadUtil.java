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
      System.getProperty("OPENNLP_DOWNLOAD_MODEL_PATH", "models/ud-models-1.2/");
  private static final String OPENNLP_DOWNLOAD_HOME = "OPENNLP_DOWNLOAD_HOME";

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
          validateModel(new URL(url + ".sha512"), localFile);
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
   * exists in that directory, the model will not be re-downloaded.
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
      validateModel(new URL(url + ".sha512"), localFile);
      logger.debug("Download complete.");
    } else {
      logger.debug("Model file '{}' already exists. Skipping download.", filename);
    }

    try {
      return type.getConstructor(Path.class).newInstance(localFile);
    } catch (Exception e) {
      throw new IOException("Could not initialize Model of type " + type.getTypeName(), e);
    }
  }

  public static Map<String, Map<ModelType, String>> getAvailableModels() {
    if (availableModels == null) {
      try {
        availableModels = new DownloadParser(new URL(BASE_URL + MODEL_URI_PATH)).getAvailableModels();
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }
    return Collections.unmodifiableMap(availableModels);
  }

  /**
   * Validates a downloaded model via the specified {@link Path downloadedModel path}.
   *
   * @param sha512          the url to get the sha512 hash
   * @param downloadedModel the model file to check
   * @throws IOException thrown if the checksum could not be computed
   */
  private static void validateModel(URL sha512, Path downloadedModel) throws IOException {
    // Download SHA512 checksum file
    String expectedChecksum;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(sha512.openStream()))) {
      expectedChecksum = reader.readLine();

      if (expectedChecksum != null) {
        expectedChecksum = expectedChecksum.split("\\s")[0].trim();
      }
    }

    // Validate SHA512 checksum
    final String actualChecksum = calculateSHA512(downloadedModel);
    if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
      throw new IOException("SHA512 checksum validation failed for " + downloadedModel.getFileName() +
          ". Expected: " + expectedChecksum + ", but got: " + actualChecksum);
    }
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

    Map<String, Map<ModelType, String>> getAvailableModels() {
      final Matcher matcher = LINK_PATTERN.matcher(fetchPageIndex());

      final List<String> links = new ArrayList<>();
      while (matcher.find()) {
        links.add(matcher.group(1));
      }

      return toMap(links);
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
          } else if (link.contains("fi-ud")) { // Finnish
            addModel("fi", link, result);
          } else if (link.contains("hy-ud")) { // Armenian
            addModel("hy", link, result);
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
