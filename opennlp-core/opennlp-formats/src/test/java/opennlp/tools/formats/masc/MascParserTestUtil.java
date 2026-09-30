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

package opennlp.tools.formats.masc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import opennlp.tools.util.XmlUtil;

/**
 * Feeds MASC annotation text to a handler or a document parser.
 */
final class MascParserTestUtil {

  private MascParserTestUtil() {
  }

  /**
   * Parses {@code xml} with {@code handler}.
   *
   * @param xml The document text.
   * @param handler The handler to feed.
   * @return {@code handler}, after the parse.
   * @throws Exception Thrown if the document is not well-formed or the handler rejects it.
   */
  static <T extends DefaultHandler> T parse(String xml, T handler) throws Exception {
    XmlUtil.createSaxParser().parse(
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);
    return handler;
  }

  /**
   * Checks that {@code handler} rejects {@code xml} with a {@link SAXException} whose cause is
   * the {@link IllegalArgumentException} that names the malformed value.
   *
   * @param xml The malformed annotation XML.
   * @param handler The parser expected to reject it.
   * @return The parsing exception.
   */
  static SAXException assertRejected(String xml, DefaultHandler handler) {
    SAXException e = Assertions.assertThrows(SAXException.class, () -> parse(xml, handler));
    Assertions.assertInstanceOf(IllegalArgumentException.class, e.getCause());
    return e;
  }

  /**
   * Lists XML whitespace as it can separate two items of an attribute value: written
   * literally, repeated, or supplied through character references.
   *
   * @return The separators, each to be placed between two items.
   */
  static Stream<String> xmlWhitespaceSeparators() {
    return Stream.of(" ", "   ", "\t", "\n", "\r\n", "&#9;", "&#10;", "&#13;", " &#10; ");
  }

  /**
   * Wraps {@code text} as a UTF-8 input stream.
   *
   * @param text The document text.
   * @return A stream over the UTF-8 bytes of {@code text}.
   */
  static InputStream input(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }
}
