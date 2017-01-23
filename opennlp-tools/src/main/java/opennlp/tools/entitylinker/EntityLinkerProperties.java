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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Properties wrapper for the EntityLinker framework
 *
 */
public class EntityLinkerProperties {

  private Properties props;

  /**
   * Constructor takes location of properties file as arg
   *
   * @param propertiesfile the properties file
   * @throws IOException
   */
  public EntityLinkerProperties(File propertiesfile) throws IOException {
    InputStream stream = null;
    try {
      stream = new FileInputStream(propertiesfile);
      init(stream);
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
  }

  /**
   *
   * @param propertiesIn inputstream of properties file. Stream will not be
   *                       closed
   * @throws IOException
   *
   */
  public EntityLinkerProperties(InputStream propertiesIn) throws IOException {
    init(propertiesIn);
  }

  private void init(InputStream propertiesIn) throws IOException {
    props = new Properties();
    props.load(propertiesIn);
  }

  /**
   * Gets a property from the props file.
   *
   * @param key          the key to the desired item in the properties file
   *                     (key=value)
   * @param defaultValue a default value in case the key, or the value are
   *                     missing
   * @return a property value in the form of a string

   * @throws IOException when the  properties object was somehow not initialized properly
   */
  public String getProperty(String key, String defaultValue) throws IOException {

    if (props != null) {
      return props.getProperty(key, defaultValue);
    } else {
      throw new IOException("EntityLinkerProperties was not successfully initialized");
    }
  }
}
