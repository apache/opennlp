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

package opennlp.tools.cmdline;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractModelLoaderTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractModelLoaderTest.class);

  private static final String BASE_URL_MODELS_V15 = "https://opennlp.sourceforge.net/models-1.5/";
  private static final String BASE_URL_MODELS_V183 = "https://dlcdn.apache.org/opennlp/models/langdetect/1.8.3/";
  protected static final Path OPENNLP_DIR = Paths.get(System.getProperty("user.home") + "/.opennlp/");

  protected static void downloadVersion15Model(String modelName) throws IOException {
    downloadModel(new URL(BASE_URL_MODELS_V15 + modelName));
  }

  protected static void downloadVersion183Model(String modelName) throws IOException {
    downloadModel(new URL(BASE_URL_MODELS_V183 + modelName));
  }

  private static void downloadModel(URL url) throws IOException {
    if (!Files.isDirectory(OPENNLP_DIR)) {
      OPENNLP_DIR.toFile().mkdir();
    }
    final String filename = url.toString().substring(url.toString().lastIndexOf("/") + 1);
    final Path localFile = Paths.get(OPENNLP_DIR.toString(), filename);

    if (!Files.exists(localFile)) {
      logger.debug("Downloading model from {} to {}.", url, localFile);
      try (final InputStream in = new BufferedInputStream(url.openStream())) {
        Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
      }
      logger.debug("Download complete.");
    }
  }

}
