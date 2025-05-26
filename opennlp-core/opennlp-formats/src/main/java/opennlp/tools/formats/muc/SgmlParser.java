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

package opennlp.tools.formats.muc;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringUtil;

/**
 * A SAX style <a href="https://www.w3.org/TR/WD-html40-970708/intro/sgmltut.html">SGML</a> parser.
 * 
 * @implNote The implementation is very limited, but good enough to parse the
 * <a href="https://catalog.ldc.upenn.edu/LDC2003T13">MUC corpora</a>.
 * Its must very likely be extended/improved/fixed to parse a different SGML corpora.
 */
public class SgmlParser {

  private static final char SYMBOL_CLOSE = '>';
  private static final char SYMBOL_OPEN = '<';
  private static final char SYMBOL_SLASH = '/';
  private static final char SYMBOL_EQUALS = '=';
  private static final char SYMBOL_QUOT = '"';

  /**
   * Defines methods to handle content produced by a {@link SgmlParser}.
   * A concrete implementation interprets the document specific details.
   */
  public static abstract class ContentHandler {

    /**
     * Handles a SGML start element.
     *
     * @param name The name of the element's start tag.
     * @param attributes The attributes supplied with the start tag. It may be empty.
     * @throws InvalidFormatException Thrown if parameters were invalid.
     */
    public abstract void startElement(String name, Map<String, String> attributes)
            throws InvalidFormatException;

    /**
     * Handles a set of characters between SGML start and end tag.
     * 
     * @param chars The characters to process.
     * @throws InvalidFormatException Thrown if parameters were invalid.
     */
    public abstract void characters(CharSequence chars)
            throws InvalidFormatException;

    /**
     * Handles a SGML end element.
     * @param name The name of the element's end tag.
     */
    public abstract void endElement(String name);
  }

  private static String extractTagName(CharSequence tagChars) throws InvalidFormatException {

    int fromOffset = 1;
    if (tagChars.length() > 1 && tagChars.charAt(1) == SYMBOL_SLASH) {
      fromOffset = 2;
    }

    for (int ci = 1; ci < tagChars.length(); ci++) {
      if (tagChars.charAt(ci) == SYMBOL_CLOSE || StringUtil.isWhitespace(tagChars.charAt(ci))) {
        return tagChars.subSequence(fromOffset, ci).toString();
      }
    }

    throw new InvalidFormatException("Failed to extract tag name!");
  }

  private static Map<String, String> getAttributes(CharSequence tagChars) {

    // format:
    // space
    // key
    // =
    // " <- begin
    // value chars
    // " <- end

    Map<String, String> attributes = new HashMap<>();

    StringBuilder key = new StringBuilder();
    StringBuilder value = new StringBuilder();

    boolean extractKey = false;
    boolean extractValue = false;

    for (int i = 0; i < tagChars.length(); i++) {

      // White space indicates begin of new key name
      if (StringUtil.isWhitespace(tagChars.charAt(i)) && !extractValue) {
        extractKey = true;
      }
      // Equals sign indicated end of key name
      else if (extractKey && (SYMBOL_EQUALS == tagChars.charAt(i) ||
              StringUtil.isWhitespace(tagChars.charAt(i)))) {
        extractKey = false;
      }
      // Inside key name, extract all chars
      else if (extractKey) {
        key.append(tagChars.charAt(i));
      }
      // " Indicates begin or end of value chars
      else if (SYMBOL_QUOT == tagChars.charAt(i)) {

        if (extractValue) {
          attributes.put(key.toString(), value.toString());

          // clear key and value buffers
          key.setLength(0);
          value.setLength(0);
        }
        extractValue = !extractValue;
      }
      // Inside value, extract all chars
      else if (extractValue) {
        value.append(tagChars.charAt(i));
      }
    }

    return attributes;
  }

  /**
   * Parses an SGML document available via the input in {@link Reader}.
   * The specified {@link ContentHandler} is responsible of how to interpret the document
   * specific details.
   *
   * @param in      A {@link Reader} that provides the data of the SGML document.
   * @param handler The {@link ContentHandler} to interpret the document with.
   *                
   * @throws IOException Thrown if IO errors occurred.
   * @throws InvalidFormatException Thrown if parameters were invalid.
   */
  public void parse(Reader in, ContentHandler handler) throws IOException {

    StringBuilder buffer = new StringBuilder();

    boolean isInsideTag = false;
    boolean isStartTag = true;

    int lastChar = -1;
    int c;
    while ((c = in.read()) != -1) {

      if (SYMBOL_OPEN == c) {
        if (isInsideTag) {
          throw new InvalidFormatException("Did not expect < char!");
        }
        if (!buffer.toString().trim().isEmpty()) {
          handler.characters(buffer.toString().trim());
        }
        buffer.setLength(0);
        isInsideTag = true;
        isStartTag = true;
      }
      buffer.appendCodePoint(c);

      if (SYMBOL_SLASH == c && lastChar == SYMBOL_OPEN) {
        isStartTag = false;
      }

      if (SYMBOL_CLOSE == c) {

        if (!isInsideTag) {
          throw new InvalidFormatException("Did not expect > char!");
        }
        if (isStartTag) {
          handler.startElement(extractTagName(buffer), getAttributes(buffer));
        }
        else {
          handler.endElement(extractTagName(buffer));
        }
        buffer.setLength(0);
        isInsideTag = false;
      }
      lastChar = c;
    }

    if (isInsideTag) {
      throw new InvalidFormatException("Did not find matching > char!");
    }
  }
}
