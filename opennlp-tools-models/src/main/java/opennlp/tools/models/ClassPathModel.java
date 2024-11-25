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
package opennlp.tools.models;

import java.util.Properties;

public record ClassPathModel(Properties properties, byte[] model) {

  /**
   * @return Retrieves the value of the {@code model.version} property
   *         or {@code "unknown"} if it could not be read.
   */
  public String getModelVersion() {
    return properties != null ? properties.getProperty("model.version", "unknown") : "unknown";
  }

  /**
   * @return Retrieves the value of the {@code model.name} property
   *         or {@code "unknown"} if it could not be read.
   */
  public String getModelName() {
    return properties != null ? properties.getProperty("model.name", "unknown") : "unknown";
  }

  /**
   * @return Retrieves the value of the {@code model.sha256} property
   *         or {@code "unknown"} if it could not be read.
   */
  public String getModelSHA256() {
    return properties != null ? properties.getProperty("model.sha256", "unknown") : "unknown";
  }

  /**
   * @return Retrieves the value of the {@code model.language} property
   *         or {@code "unknown"} if it could not  be read.
   */
  public String getModelLanguage() {
    return properties != null ? properties.getProperty("model.language", "unknown") : "unknown";
  }

}
