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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A structure to hold the letsmt document. The documents contains sentences and depending on the
 * source it either contains tokenized text (words) or an un-tokenized sentence string.
 * <p>
 * The format specification can be found
 * <a href="http://project.letsmt.eu/uploads/Deliverables/D2.1%20%20Specification%20of%20data%20formats%20v1%20final.pdf">here</a>.
 */
public class LetsmtDocument {

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

  // define a content handler to receive the sax events ...
  public static class LetsmtDocumentHandler extends DefaultHandler {

    private List<LetsmtSentence> sentences = new ArrayList<>();

    private StringBuilder chars = new StringBuilder();
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
        // place it came from
        case "s":
          LetsmtSentence sentence = new LetsmtSentence();

          if (tokens.size() > 0) {
            sentence.tokens = tokens.toArray(new String[tokens.size()]);
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

  private List<LetsmtSentence> sentences = new ArrayList<>();

  private LetsmtDocument(List<LetsmtSentence> sentences) {
    this.sentences = sentences;
  }

  public List<LetsmtSentence> getSentences() {
    return Collections.unmodifiableList(sentences);
  }

  static LetsmtDocument parse(InputStream letsmtXmlIn) throws IOException {
    SAXParserFactory spf = SAXParserFactory.newInstance();

    try {
      SAXParser saxParser = spf.newSAXParser();

      XMLReader xmlReader = saxParser.getXMLReader();
      LetsmtDocumentHandler docHandler = new LetsmtDocumentHandler();
      xmlReader.setContentHandler(docHandler);
      xmlReader.parse(new InputSource(letsmtXmlIn));
      return new LetsmtDocument(docHandler.sentences);
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    } catch (SAXException e) {
      throw new IOException("Failed to parse letsmt xml!", e);
    }
  }

  static LetsmtDocument parse(File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      return parse(in);
    }
  }
}
