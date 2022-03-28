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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.model.BaseModel;

/**
 * This class facilitates the downloading of pretrained OpenNLP models.
 */
public class DownloadUtil {

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

    private String name;

    ModelType(String name) {
      this.name = name;
    }
  }

  private static final String baseUrl = "https://dlcdn.apache.org/opennlp/";

  public static Map<String, Map<ModelType, String>> available_models = new HashMap<>();

  static {

    final Map<ModelType, String> frenchModels = new HashMap<>();
    frenchModels.put(ModelType.SENTENCE_DETECTOR,
        baseUrl + "models/ud-models-1.0/opennlp-1.0-1.9.3fr-ud-ftb-sentence-1.0-1.9.3.bin");
    frenchModels.put(ModelType.POS,
        baseUrl + "models/ud-models-1.0/opennlp-fr-ud-ftb-pos-1.0-1.9.3.bin");
    frenchModels.put(ModelType.TOKENIZER,
        baseUrl + "models/ud-models-1.0/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin");
    available_models.put("fr", frenchModels);

    final Map<ModelType, String> germanModels = new HashMap<>();
    germanModels.put(ModelType.SENTENCE_DETECTOR,
        baseUrl + "models/ud-models-1.0/opennlp-de-ud-gsd-sentence-1.0-1.9.3.bin");
    germanModels.put(ModelType.POS,
        baseUrl + "models/ud-models-1.0/opennlp-de-ud-gsd-pos-1.0-1.9.3.bin");
    germanModels.put(ModelType.TOKENIZER,
        baseUrl + "models/ud-models-1.0/opennlp-de-ud-gsd-tokens-1.0-1.9.3.bin");
    available_models.put("de", germanModels);

    final Map<ModelType, String> englishModels = new HashMap<>();
    englishModels.put(ModelType.SENTENCE_DETECTOR,
        baseUrl + "models/ud-models-1.0/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin");
    englishModels.put(ModelType.POS,
        baseUrl + "models/ud-models-1.0/opennlp-en-ud-ewt-pos-1.0-1.9.3.bin");
    englishModels.put(ModelType.TOKENIZER,
        baseUrl + "models/ud-models-1.0/opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin");
    available_models.put("en", englishModels);

    final Map<ModelType, String> italianModels = new HashMap<>();
    italianModels.put(ModelType.SENTENCE_DETECTOR,
        baseUrl + "models/ud-models-1.0/opennlp-it-ud-vit-sentence-1.0-1.9.3.bin");
    italianModels.put(ModelType.POS,
        baseUrl + "models/ud-models-1.0/opennlp-it-ud-vit-pos-1.0-1.9.3.bin");
    italianModels.put(ModelType.TOKENIZER,
        baseUrl + "models/ud-models-1.0/opennlp-it-ud-vit-sentence-1.0-1.9.3.bin");
    available_models.put("it", italianModels);

    final Map<ModelType, String> dutchModels = new HashMap<>();
    dutchModels.put(ModelType.SENTENCE_DETECTOR,
        baseUrl + "models/opennlp-nl-ud-alpino-sentence-1.0-1.9.3.bin");
    dutchModels.put(ModelType.POS,
        baseUrl + "models/ud-models-1.0/opennlp-nl-ud-alpino-pos-1.0-1.9.3.bin");
    dutchModels.put(ModelType.TOKENIZER,
        baseUrl + "models/ud-models-1.0/opennlp-nl-ud-alpino-tokens-1.0-1.9.3.bin");
    available_models.put("nl", dutchModels);

  }

  public static BaseModel downloadModel(String language, ModelType modelType, Class type)
          throws IOException {

    if (available_models.containsKey(language)) {
      final String url = (available_models.get(language).get(modelType));
      if (url != null) {
        return downloadModel(new URL(url), type);
      }
    }

    throw new IOException("Invalid model.");
  }

  /**
   * Downloads a model from a URL. The model is saved to an .opennlp/ directory
   * located under the user's home directory. This directory will be created
   * if it does not already exist. If a model to be downloaded already
   * exists in that directory, the model will not be re-downloaded.
   *
   * @param url The model's URL.
   * @return A {@link TokenNameFinderModel}.
   * @throws IOException Thrown if the model cannot be downloaded.
  */
  public static BaseModel downloadModel(URL url, Class type) throws IOException {

    final Path homeDirectory = Paths.get(System.getProperty("user.home") + "/.opennlp/");
    if (!Files.isDirectory(homeDirectory)) {
      homeDirectory.toFile().mkdir();
    }

    final String filename = url.toString().substring(url.toString().lastIndexOf("/") + 1);
    final Path localFile = Paths.get(homeDirectory.toString(), filename);

    if (!Files.exists(localFile)) {

      System.out.println("Downloading model " + url + " to " + localFile);

      try (final InputStream in = url.openStream()) {
        Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
      }

      System.out.println("Download complete.");

    }

    if (type == TokenizerModel.class) {
      return new TokenizerModel(localFile);
    } else if (type == ChunkerModel.class) {
      return new ChunkerModel(localFile);
    } else if (type == SentenceModel.class) {
      return new SentenceModel(localFile);
    } else if (type == POSModel.class) {
      return new POSModel(localFile);
    } else {
      return new TokenNameFinderModel(localFile);
    }

  }

}
