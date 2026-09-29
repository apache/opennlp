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
   * Lists XML whitespace as it can separate two items of an attribute value: written
   * literally, repeated, or supplied through character references.
   *
   * @return The separators, each to be placed between two items.
   */
  static Stream<String> xmlWhitespaceSeparators() {
    return Stream.of(" ", "   ", "\t", "\n", "\r\n", "&#9;", "&#10;", "&#13;", " &#10; ");
  }

  /**
   * Supplies signed and Unicode decimal forms of the offsets zero and four.
   *
   * @return Anchor pairs representing the span from zero to four.
   */
  static Stream<String> equivalentAnchors() {
    return Stream.of("+0 4", "0 +4", "0 \u0664", "0 \uFF14", "\u0660 4", "-0 +4", "00 04");
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
