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

package opennlp.morfologik;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import morfologik.stemming.DictionaryMetadata;

import opennlp.morfologik.builder.MorfologikDictionaryBuilder;

public abstract class AbstractMorfologikTest {

  protected static Path createMorfologikDictionary() throws Exception {
    Path tabFilePath = File.createTempFile(AbstractMorfologikTest.class.getName(), ".txt").toPath();
    tabFilePath.toFile().deleteOnExit();
    Path infoFilePath = DictionaryMetadata.getExpectedMetadataLocation(tabFilePath);
    infoFilePath.toFile().deleteOnExit();

    Files.copy(getResourceStream("/dictionaryWithLemma.txt"), tabFilePath,
            StandardCopyOption.REPLACE_EXISTING);
    Files.copy(getResourceStream("/dictionaryWithLemma.info"), infoFilePath,
            StandardCopyOption.REPLACE_EXISTING);

    MorfologikDictionaryBuilder builder = new MorfologikDictionaryBuilder();

    return builder.build(tabFilePath);
  }

  private static InputStream getResourceStream(String name) {
    return AbstractMorfologikTest.class.getResourceAsStream(name);
  }

  protected static URL getResource(String name) {
    return AbstractMorfologikTest.class.getResource(name);
  }
}
