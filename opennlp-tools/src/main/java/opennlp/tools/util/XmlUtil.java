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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class XmlUtil {

  private static final Logger logger = LoggerFactory.getLogger(XmlUtil.class);

  /**
   * Create a new {@link DocumentBuilder} which processes XML securely.
   *
   * @return A valid {@link DocumentBuilder} instance.
   *
   * @throws IllegalStateException Thrown if errors occurred creating the builder.
   */
  public static DocumentBuilder createDocumentBuilder() {
    try {
      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      try {
        documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      } catch (ParserConfigurationException e) {
        /// {@link XMLConstants.FEATURE_SECURE_PROCESSING} is not supported on Android.
        /// See {@link DocumentBuilderFactory#setFeature}
        logger.warn("Failed to enable XMLConstants.FEATURE_SECURE_PROCESSING, it's unsupported on" +
                " this platform.", e);
      }
      return documentBuilderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Create a new {@link SAXParser} which processes XML securely.
   *
   * @return A valid {@link SAXParser} instance.
   *
   * @throws IllegalStateException Thrown if errors occurred creating the parser.
   */
  public static SAXParser createSaxParser() {
    SAXParserFactory spf = SAXParserFactory.newInstance();
    try {
      spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      return spf.newSAXParser();
    } catch (ParserConfigurationException | SAXException e) {
      throw new IllegalStateException(e);
    }
  }
}
