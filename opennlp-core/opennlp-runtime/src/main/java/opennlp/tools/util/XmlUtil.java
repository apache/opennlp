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

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.xpath.XPath;

import org.apache.commons.xml.secure.SecureDocumentBuilderFactory;
import org.apache.commons.xml.secure.SecureSAXParserFactory;
import org.apache.commons.xml.secure.SecureXMLInputFactory;
import org.apache.commons.xml.secure.SecureXPathFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

import opennlp.tools.util.model.UncloseableInputStream;

/**
 * Creates XML parsers that process untrusted input securely.
 * <p>
 * Every factory comes from
 * <a href="https://commons.apache.org/proper/commons-secure-xml/">Apache Commons Secure XML</a>.
 * The parsers it creates fetch no external resource unless an {@link EntityResolver}
 * explicitly allows it, and they bound entity expansion. Its
 * <a href="https://commons.apache.org/proper/commons-secure-xml/threat_model.html">threat model</a>
 * lists the settings a caller may still change without weakening these guarantees.
 *
 * @since 1.8.2
 */
public class XmlUtil {

  /**
   * Holds the shared StAX factory.
   * <p>
   * {@link XMLInputFactory} is thread-safe once configured, and the lazy holder keeps StAX out
   * of the static initialization of {@link XmlUtil}, so the DOM and SAX helpers keep working on
   * a platform like Android without a StAX implementation.
   */
  private static final class StaxHolder {

    private static final XMLInputFactory FACTORY = createFactory();

    private static XMLInputFactory createFactory() {
      final XMLInputFactory factory = SecureXMLInputFactory.newInstance();
      factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
      return factory;
    }
  }

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

  /**
   * Create a new {@link XMLStreamReader} which processes XML securely.
   * <p>
   * The reader coalesces adjacent text sections.
   * <p>
   * The returned reader respects the StAX contract and does <strong>not</strong> close the
   * underlying input stream, regardless of the implementation on the classpath.
   *
   * @param in A valid, open {@link InputStream} of XML.
   * @return A valid {@link XMLStreamReader} instance positioned before the first event.
   * @throws XMLStreamException Thrown if the stream does not start a well-formed document.
   * @since 3.0.0
   */
  public static XMLStreamReader createXmlStreamReader(InputStream in) throws XMLStreamException {
    return StaxHolder.FACTORY.createXMLStreamReader(new UncloseableInputStream(in));
  }

  /**
   * Create a new {@link XPath} which evaluates expressions securely,
   * including those given an {@link org.xml.sax.InputSource} to parse.
   *
   * @return A valid {@link XPath} instance.
   * @since 3.0.0
   */
  public static XPath createXPath() {
    return SecureXPathFactory.newInstance().newXPath();
  }
}
