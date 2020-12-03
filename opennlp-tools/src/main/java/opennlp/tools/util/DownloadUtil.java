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

import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.model.BaseModel;

/**
 * This class facilitates the downloading of OpenNLP models
 * from SourceForge to the local computer. Downloaded models
 * are stored in the user's home directory under an
 * .opennlp/ subdirectory.
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

  /**
   * The entity type of the NER model.
   */
  public enum EntityType {
    DATE("date"),
    PERSON("person"),
    ORGANIZATION("organization"),
    LOCATION("location"),
    MISC("misc"),
    MONEY("money"),
    PERCENTAGE("percentage");

    private String entityType;

    EntityType(String entityType) {
      this.entityType = entityType;
    }
  }

  private static final String baseUrl = "http://opennlp.sourceforge.net/models-1.5/";

  public static BaseModel downloadModel(ModelType modelType, EntityType entityType,
                                                   String language) throws IOException {
    final String modelFileName = language + "-" + modelType.name  + "-" + entityType.entityType + ".bin";
    return downloadModel(new URL(baseUrl + modelFileName), TokenNameFinderModel.class);
  }

  public static BaseModel downloadModel(ModelType modelType, String language, Class type)
          throws IOException {
    final String modelFileName = language + "-" + modelType.name + ".bin";
    return downloadModel(new URL(baseUrl + modelFileName), type);
  }

  /**
   * Downloads a model from a URL.
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

      System.out.println("Downloading model " + url.toString() + " to " + localFile.toString());

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
