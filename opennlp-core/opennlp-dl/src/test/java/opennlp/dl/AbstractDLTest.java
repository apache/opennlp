/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.io.File;
import java.io.FileNotFoundException;

import opennlp.tools.util.StringUtil;

public abstract class AbstractDLTest {

  public File getOpennlpDataDir() throws FileNotFoundException {
    final String dataDirectory = System.getProperty("OPENNLP_DATA_DIR");
    if (dataDirectory == null || StringUtil.isEmpty(dataDirectory)) {
      throw new IllegalArgumentException("The OPENNLP_DATA_DIR is not set.");
    }
    final File file = new File(dataDirectory);
    if (!file.exists()) {
      throw new FileNotFoundException("The OPENNLP_DATA_DIR path of " + dataDirectory + " was not found.");
    }
    return file;
  }

}
