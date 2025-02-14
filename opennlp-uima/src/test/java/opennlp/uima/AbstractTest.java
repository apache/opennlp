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

package opennlp.uima;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class AbstractTest {

  protected static final String FILE_URL = "fileUrl";

  protected static final String TARGET_DIR;

  protected static final String PATH_DESCRIPTORS;

  static {
    String targetDir;
    String descriptorsDir;
    try {
      targetDir = Path.of(AbstractTest.class.getProtectionDomain().
              getCodeSource().getLocation().toURI()).toString();
      descriptorsDir = Paths.get(targetDir, "test-descriptors/").toString();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    TARGET_DIR = targetDir;
    PATH_DESCRIPTORS = descriptorsDir;
  }

  protected static final Path OPENNLP_DIR = Paths.get(System.getProperty("OPENNLP_DOWNLOAD_HOME",
          System.getProperty("user.home"))).resolve(".opennlp");

}
