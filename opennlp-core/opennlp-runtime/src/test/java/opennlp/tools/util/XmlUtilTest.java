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

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class XmlUtilTest {

  private static final String SECRET = "s3cr3t-content";

  @TempDir
  Path tempDir;

  private String externalEntityPayload() throws IOException {
    Path secret = tempDir.resolve("secret.txt");
    Files.writeString(secret, SECRET, StandardCharsets.UTF_8);
    return "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
        + "<root>&xxe;</root>";
  }

  @Test
  void testDocumentBuilderDoesNotResolveExternalEntities() throws Exception {
    DocumentBuilder documentBuilder = XmlUtil.createDocumentBuilder();
    try {
      Document document = documentBuilder.parse(
          new InputSource(new StringReader(externalEntityPayload())));
      Assertions.assertFalse(document.getDocumentElement().getTextContent().contains(SECRET),
          "external entity must not be resolved");
    } catch (SAXParseException e) {
      // Rejecting the document outright is an acceptable outcome as well.
    }
  }

  @Test
  void testSaxParserDoesNotResolveExternalEntities() throws Exception {
    XMLReader reader = XmlUtil.createSaxParser().getXMLReader();
    StringBuilder text = new StringBuilder();
    reader.setContentHandler(new DefaultHandler() {
      @Override
      public void characters(char[] ch, int start, int length) {
        text.append(ch, start, length);
      }
    });
    try {
      reader.parse(new InputSource(new StringReader(externalEntityPayload())));
      Assertions.assertFalse(text.toString().contains(SECRET),
          "external entity must not be resolved");
    } catch (SAXParseException e) {
      // Rejecting the document outright is an acceptable outcome as well.
    }
  }

  @Test
  void testSaxParserIsNamespaceAware() throws Exception {
    XMLReader reader = XmlUtil.createSaxParser().getXMLReader();
    String[] namespace = new String[1];
    reader.setContentHandler(new DefaultHandler() {
      @Override
      public void startElement(String uri, String localName, String qName,
                               org.xml.sax.Attributes attributes) {
        namespace[0] = uri;
      }
    });
    reader.parse(new InputSource(new StringReader("<root xmlns=\"urn:test\"/>")));
    Assertions.assertEquals("urn:test", namespace[0]);
  }

  @Test
  void testParsersAreNotXIncludeAware() {
    Assertions.assertFalse(XmlUtil.createDocumentBuilder().isXIncludeAware());
    Assertions.assertFalse(XmlUtil.createSaxParser().isXIncludeAware());
  }

  @Test
  void testDocumentBuilderRejectsEntityExpansionBomb() {
    StringBuilder dtd = new StringBuilder("<!DOCTYPE root [<!ENTITY e0 \"lol\">");
    for (int i = 1; i < 10; i++) {
      dtd.append("<!ENTITY e").append(i).append(" \"");
      for (int j = 0; j < 10; j++) {
        dtd.append("&e").append(i - 1).append(';');
      }
      dtd.append("\">");
    }
    dtd.append("]>");
    String payload = dtd + "<root>&e9;</root>";
    DocumentBuilder documentBuilder = XmlUtil.createDocumentBuilder();
    Assertions.assertThrows(SAXException.class,
        () -> documentBuilder.parse(new InputSource(new StringReader(payload))));
  }

  @Test
  void testCreateDocumentBuilderWithUnsupportedSecurityOptions() throws Exception {
    String property = DocumentBuilderFactory.class.getName();
    String oldFactory = System.getProperty(property);
    System.setProperty(property, ThrowingSecurityOptionsDocumentBuilderFactory.class.getName());
    try {
      DocumentBuilder documentBuilder = XmlUtil.createDocumentBuilder();

      Assertions.assertEquals("root", documentBuilder.parse(
          new InputSource(new StringReader("<root/>"))).getDocumentElement().getTagName());
    } finally {
      if (oldFactory == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, oldFactory);
      }
    }
  }

  public static class ThrowingSecurityOptionsDocumentBuilderFactory
      extends DocumentBuilderFactory {

    private final DocumentBuilderFactory delegate = DocumentBuilderFactory.newDefaultInstance();

    @Override
    public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
      return delegate.newDocumentBuilder();
    }

    @Override
    public void setAttribute(String name, Object value) {
      if (XMLConstants.ACCESS_EXTERNAL_DTD.equals(name)) {
        throw new IllegalArgumentException(name);
      }
      delegate.setAttribute(name, value);
    }

    @Override
    public Object getAttribute(String name) {
      return delegate.getAttribute(name);
    }

    @Override
    public void setFeature(String name, boolean value) throws ParserConfigurationException {
      if ("http://apache.org/xml/features/disallow-doctype-decl".equals(name)) {
        throw new ParserConfigurationException(name);
      }
      delegate.setFeature(name, value);
    }

    @Override
    public void setXIncludeAware(boolean state) {
      throw new UnsupportedOperationException("XInclude");
    }

    @Override
    public boolean getFeature(String name) throws ParserConfigurationException {
      return delegate.getFeature(name);
    }
  }
}
