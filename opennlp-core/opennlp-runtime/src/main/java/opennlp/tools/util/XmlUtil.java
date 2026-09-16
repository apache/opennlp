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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

import org.apache.commons.xml.secure.SecureDocumentBuilderFactory;
import org.apache.commons.xml.secure.SecureSAXParserFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

/**
 * Creates XML parsers that process untrusted input securely.
 * <p>
 * Every factory comes from
 * <a href="https://commons.apache.org/proper/commons-secure-xml/">Apache Commons Secure XML</a>.
 * The parsers it creates fetch no external resource unless an {@link EntityResolver}
 * explicitly allows it, and they bound entity expansion. Its
 * <a href="https://commons.apache.org/proper/commons-secure-xml/threat_model.html">threat model</a>
 * lists the settings a caller may still change without weakening these guarantees.
 */
public class XmlUtil {

  /**
   * Create a new {@link DocumentBuilder} which processes XML securely.
   *
   * @return A valid {@link DocumentBuilder} instance.
   * @throws IllegalStateException Thrown if errors occurred creating the builder.
   */
  public static DocumentBuilder createDocumentBuilder() {
    try {
      return SecureDocumentBuilderFactory.newInstance().newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      // Not expected from any known JAXP implementation: the factory is fully configured.
      throw new IllegalStateException("Failed to create a secure DocumentBuilder.", e);
    }
  }

  /**
   * Create a new namespace-aware {@link SAXParser} which processes XML securely.
   *
   * @return A valid {@link SAXParser} instance.
   * @throws IllegalStateException Thrown if errors occurred creating the parser.
   */
  public static SAXParser createSaxParser() {
    try {
      return SecureSAXParserFactory.newNSInstance().newSAXParser();
    } catch (ParserConfigurationException | SAXException e) {
      // Not expected from any known JAXP implementation: the factory is fully configured.
      throw new IllegalStateException("Failed to create a secure SAXParser.", e);
    }
  }
}
