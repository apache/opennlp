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

package opennlp.tools.entitylinker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Properties wrapper for {@link EntityLinker} implementations.
 *
 * @see EntityLinkerFactory
 */
public class EntityLinkerProperties {

  private Properties props;

  /**
   * Initializes {@link EntityLinkerProperties} via a {@link File} reference.
   *
   * @param propertiesFile The {@link File} that references the {@code *.properties}
   *                       configuration.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public EntityLinkerProperties(File propertiesFile) throws IOException {
    try (InputStream stream = new BufferedInputStream(new FileInputStream(propertiesFile))) {
      init(stream);
    }
  }

  /**
   * Initializes {@link EntityLinkerProperties} via a {@link InputStream} reference.
   *
   * @param propertiesIn The {@link InputStream} that references the {@code *.properties}
   *                     configuration.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public EntityLinkerProperties(InputStream propertiesIn) throws IOException {
    init(propertiesIn);
  }

  private void init(InputStream propertiesIn) throws IOException {
    props = new Properties();
    props.load(propertiesIn);
  }

  /**
   * Retrieves a property value for a given {@code key}.
   *
   * @param key          The key to the desired item in the properties configuration
   *                     {@code key=value}
   * @param defaultValue A default value in case the {@code key}, or the value are
   *                     missing
   * @return A property value as a {@link String}.

   * @throws IOException Thrown if the properties object was not initialized properly.
   */
  public String getProperty(String key, String defaultValue) throws IOException {

    if (props != null) {
      return props.getProperty(key, defaultValue);
    } else {
      throw new IOException("EntityLinkerProperties was not successfully initialized");
    }
  }
}
