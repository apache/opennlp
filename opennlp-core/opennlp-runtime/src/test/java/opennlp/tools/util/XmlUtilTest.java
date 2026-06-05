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

import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class XmlUtilTest {

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
