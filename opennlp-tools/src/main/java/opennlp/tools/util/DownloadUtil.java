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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.commons.Internal;
import opennlp.tools.util.model.BaseModel;

/**
 * This class facilitates the downloading of pretrained OpenNLP models.
 */
public class DownloadUtil {

  private static final Logger logger = LoggerFactory.getLogger(DownloadUtil.class);

  /**
   * The type of model.
   */
  public enum ModelType {
    TOKENIZER("token"),
    SENTENCE_DETECTOR("sent"),
    POS("pos-perceptron"),
    NAME_FINDER("ner"),
    CHUNKER("chunker"),
    PARSER("parser-chunking");

    private final String name;

    ModelType(String name) {
      this.name = name;
    }
  }

  private static final String BASE_URL = "https://dlcdn.apache.org/opennlp/";
  private static final String MODELS_UD_MODELS_1_0 = "models/ud-models-1.0/";

  public static final Map<String, Map<ModelType, String>> available_models;

  static {
    try {
      available_models = new DownloadParser(new URL(BASE_URL + MODELS_UD_MODELS_1_0)).getAvailableModels();
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Triggers a download for the specified {@link DownloadUtil.ModelType}.
   *
   * @param language The ISO language code of the requested model.
   * @param modelType The {@link DownloadUtil.ModelType type} of model.
   * @param type The class of the resulting model.
   * @param <T> The generic type which is a subclass of {@link BaseModel}.
   *
   * @return A model instance of type {@link T}.
   *
   * @throws IOException Thrown if IO errors occurred or the model is invalid.
   */
  public static <T extends BaseModel> T downloadModel(String language, ModelType modelType,
                                                      Class<T> type) throws IOException {

    if (available_models.containsKey(language)) {
      final String url = (available_models.get(language).get(modelType));
      if (url != null) {
        return downloadModel(new URL(url), type);
      }
    }

    throw new IOException("Invalid model.");
  }

  /**
   * Downloads a model from a {@link URL}.
   * <p>
   * The model is saved to an {@code .opennlp/} directory
   * located in the user's home directory. This directory will be created
   * if it does not already exist. If a model to be downloaded already
   * exists in that directory, the model will not be re-downloaded.
   *
   * @param url The model's {@link URL}.
   * @param type The class of the resulting model {@link T}.
   * @param <T> The generic type which is a subclass of {@link BaseModel}.
   *
   * @return A model instance of type {@link T}.
   *
   * @throws IOException Thrown if the model cannot be downloaded.
  */
  public static <T extends BaseModel> T downloadModel(URL url, Class<T> type) throws IOException {

    final Path homeDirectory = Paths.get(System.getProperty("user.home") + "/.opennlp/");
    if (!Files.isDirectory(homeDirectory)) {
      homeDirectory.toFile().mkdir();
    }

    final String filename = url.toString().substring(url.toString().lastIndexOf("/") + 1);
    final Path localFile = Paths.get(homeDirectory.toString(), filename);

    if (!Files.exists(localFile)) {
      logger.debug("Downloading model from {} to {}.", url, localFile);

      try (final InputStream in = url.openStream()) {
        Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
      }

      logger.debug("Download complete.");
    }

    try {
      return type.getConstructor(Path.class).newInstance(localFile);
    } catch (Exception e) {
      throw new IOException("Could not initialize Model of type " + type.getTypeName(), e);
    }
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
          if (link.contains("de-ud")) {
            addModel("de", link, result);
          } else if (link.contains("en-ud")) {
            addModel("en", link, result);
          } else if (link.contains("it-ud")) {
            addModel("it", link, result);
          } else if (link.contains("nl-ud")) {
            addModel("nl", link, result);
          } else if (link.contains("fr-ud")) {
            addModel("fr", link, result);
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
