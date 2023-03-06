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

package opennlp.tools.formats.letsmt;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.SAXParser;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import opennlp.tools.util.XmlUtil;

/**
 * A structure to hold the letsmt document. The documents contain sentences and depending on the
 * source it either contains tokenized text (words) or an un-tokenized sentence string.
 * <p>
 * The format specification can be found
 * <a href="http://project.letsmt.eu/uploads/Deliverables/D2.1%20%20Specification%20of%20data%20formats%20v1%20final.pdf">
 *   here</a>.
 */
public class LetsmtDocument {

  private static final String ORG_XML_FEATURES_DISALLOW_DOCTYPE_DECL =
          "http://apache.org/xml/features/disallow-doctype-decl";

  public static class LetsmtSentence {
    private String nonTokenizedText;
    private String[] tokens;

    public String getNonTokenizedText() {
      return nonTokenizedText;
    }

    public String[] getTokens() {
      if (tokens != null) {
        return Arrays.copyOf(tokens, tokens.length);
      }

      return null;
    }
  }

  /**
   * A {@link DefaultHandler content handler} to receive and process SAX events.
   */
  public static class LetsmtDocumentHandler extends DefaultHandler {

    private final List<LetsmtSentence> sentences = new ArrayList<>();

    private final StringBuilder chars = new StringBuilder();
    private List<String> tokens = new ArrayList<>();

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      chars.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      super.endElement(uri, localName, qName);

      // Note:
      // words are optional in sentences, if there are no words just the chars have to be captured

      switch (qName) {
        case "w":
          tokens.add(chars.toString().trim());
          chars.setLength(0);
          break;

        // TODO: The sentence should contain the id, so it can be tracked back to the
        //       place it came from
        case "s":
          LetsmtSentence sentence = new LetsmtSentence();

          if (tokens.size() > 0) {
            sentence.tokens = tokens.toArray(new String[0]);
            tokens = new ArrayList<>();
          }
          else {
            sentence.nonTokenizedText = chars.toString().trim();
          }

          sentences.add(sentence);

          chars.setLength(0);
      }
    }
  }

  private final List<LetsmtSentence> sentences;

  private LetsmtDocument(List<LetsmtSentence> sentences) {
    this.sentences = sentences;
  }

  /**
   * @return Retrieves the sentences of a {@link LetsmtDocument}.
   */
  public List<LetsmtSentence> getSentences() {
    return Collections.unmodifiableList(sentences);
  }

  /**
   * @param letsmtXmlIn The {@link InputStream} referencing the document to parse.
   *
   * @return A valid {@link LetsmtDocument} instance.
   * @throws IOException Thrown if IO errors occurred during loading or parsing.
   */
  static LetsmtDocument parse(InputStream letsmtXmlIn) throws IOException {
    SAXParser saxParser = XmlUtil.createSaxParser();

    try {
      XMLReader xmlReader = saxParser.getXMLReader();
      LetsmtDocumentHandler docHandler = new LetsmtDocumentHandler();
      xmlReader.setContentHandler(docHandler);
      xmlReader.setFeature(ORG_XML_FEATURES_DISALLOW_DOCTYPE_DECL, true);
      xmlReader.parse(new InputSource(letsmtXmlIn));
      return new LetsmtDocument(docHandler.sentences);
    } catch (SAXException e) {
      throw new IOException("Failed to parse letsmt xml!", e);
    }
  }

  /**
   * @param file The {@link File} referencing the document to parse.
   *
   * @return A valid {@link LetsmtDocument} instance.
   * @throws IOException Thrown if IO errors occurred during loading or parsing.
   */
  static LetsmtDocument parse(File file) throws IOException {
    try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
      return parse(in);
    }
  }
}
