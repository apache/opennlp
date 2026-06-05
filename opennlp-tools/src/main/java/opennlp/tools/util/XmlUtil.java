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
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

public class XmlUtil {

  private static final Logger logger = LoggerFactory.getLogger(XmlUtil.class);

  /**
   * Create a new {@link DocumentBuilder} which processes XML securely.
   *
   * @return A valid {@link DocumentBuilder} instance.
   * @throws IllegalStateException Thrown if errors occurred creating the builder.
   */
  public static DocumentBuilder createDocumentBuilder() {
    final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    try {
      documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    } catch (ParserConfigurationException e) {
      /// {@link XMLConstants.FEATURE_SECURE_PROCESSING} is not supported on Android.
      /// See {@link DocumentBuilderFactory#setFeature}
      logger.warn("Failed to enable XMLConstants.FEATURE_SECURE_PROCESSING, it's unsupported on" +
          " this platform.", e);
    }
    try {
      setAttributeIfSupported(documentBuilderFactory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
      setAttributeIfSupported(documentBuilderFactory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      setFeatureIfSupported(documentBuilderFactory,
          "http://apache.org/xml/features/disallow-doctype-decl", true);
      setFeatureIfSupported(documentBuilderFactory,
          "http://xml.org/sax/features/external-general-entities", false);
      setFeatureIfSupported(documentBuilderFactory,
          "http://xml.org/sax/features/external-parameter-entities", false);
      setFeatureIfSupported(documentBuilderFactory,
          "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      setXIncludeAwareIfSupported(documentBuilderFactory, false);
      documentBuilderFactory.setExpandEntityReferences(false);
      return documentBuilderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Create a new {@link SAXParser} which processes XML securely.
   *
   * @return A valid {@link SAXParser} instance.
   * @throws IllegalStateException Thrown if errors occurred creating the parser.
   */
  public static SAXParser createSaxParser() {
    final SAXParserFactory spf = SAXParserFactory.newInstance();
    spf.setNamespaceAware(true);
    setXIncludeAwareIfSupported(spf, false);
    try {
      spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    } catch (ParserConfigurationException | SAXException e) {
      /// {@link XMLConstants.FEATURE_SECURE_PROCESSING} is not supported on Android.
      /// See {@link SAXParserFactory#setFeature}
      logger.warn("Failed to enable XMLConstants.FEATURE_SECURE_PROCESSING, it's unsupported on" +
          " this platform.", e);
    }
    setFeatureIfSupported(spf, "http://apache.org/xml/features/disallow-doctype-decl", true);
    setFeatureIfSupported(spf, "http://xml.org/sax/features/external-general-entities", false);
    setFeatureIfSupported(spf, "http://xml.org/sax/features/external-parameter-entities", false);
    setFeatureIfSupported(spf, "http://apache.org/xml/features/nonvalidating/load-external-dtd",
        false);
    try {
      final SAXParser parser = spf.newSAXParser();
      setPropertyIfSupported(parser, XMLConstants.ACCESS_EXTERNAL_DTD, "");
      setPropertyIfSupported(parser, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return parser;
    } catch (ParserConfigurationException | SAXException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void setFeatureIfSupported(DocumentBuilderFactory factory, String name,
                                            boolean value) {
    try {
      factory.setFeature(name, value);
    } catch (ParserConfigurationException e) {
      logger.warn("Failed to set XML parser feature {}, it's unsupported on this platform.",
          name, e);
    }
  }

  private static void setAttributeIfSupported(DocumentBuilderFactory factory, String name,
                                             Object value) {
    try {
      factory.setAttribute(name, value);
    } catch (IllegalArgumentException e) {
      logger.warn("Failed to set XML parser attribute {}, it's unsupported on this platform.",
          name, e);
    }
  }

  private static void setXIncludeAwareIfSupported(DocumentBuilderFactory factory, boolean state) {
    try {
      factory.setXIncludeAware(state);
    } catch (UnsupportedOperationException e) {
      logger.warn("Failed to set XML parser XInclude awareness, it's unsupported on " +
          "this platform.", e);
    }
  }

  private static void setPropertyIfSupported(SAXParser parser, String name, Object value) {
    try {
      parser.setProperty(name, value);
    } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
      logger.warn("Failed to set XML parser property {}, it's unsupported on this platform.",
          name, e);
    }
  }

  private static void setFeatureIfSupported(SAXParserFactory factory, String name, boolean value) {
    try {
      factory.setFeature(name, value);
    } catch (ParserConfigurationException | SAXException e) {
      logger.warn("Failed to set XML parser feature {}, it's unsupported on this platform.",
          name, e);
    }
  }

  private static void setXIncludeAwareIfSupported(SAXParserFactory factory, boolean state) {
    try {
      factory.setXIncludeAware(state);
    } catch (UnsupportedOperationException e) {
      logger.warn("Failed to set XML parser XInclude awareness, it's unsupported on " +
          "this platform.", e);
    }
  }
}
