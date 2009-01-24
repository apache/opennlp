/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Properties;

/**
 * This class tries it best to map a language to an encoding.
 */
public class EncodingMapper {

  /**
   * Maps the language code to an actual encoding. If
   * it cannot be mapped the default encoding is returned.
   *
   * @param languageCode
   *
   * @return
   */
  static String getEncoding(String languageCode) {

    // get encoding properties from properties file
    InputStream in = EncodingMapper.class.getResourceAsStream(
        "/opennlp/tools/cmdline/encoding.properties");

    Properties encodingMap = new Properties();

    try {
      encodingMap.load(in);
    }
    catch (IOException e) {
      // loading from classpath should not fail
    }
    finally {
      try {
        in.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    String encoding = encodingMap.getProperty(languageCode);

    if (encoding == null) {
      encoding = Charset.defaultCharset().name();
    }

    return encoding;
  }
}
