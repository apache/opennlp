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

package opennlp.tools.formats.frenchtreebank;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Constituent;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.Span;

class ConstitDocumentHandler extends DefaultHandler {

  private static final String SENT_ELEMENT_NAME = "SENT";
  private static final String WORD_ELEMENT_NAME = "w";

  private static final String SENT_TYPE_NAME = "S";

  private final List<Parse> parses;

  private boolean insideSentenceElement;

  /**
   * A token buffer, a token might be build up by multiple
   * {@link #characters(char[], int, int)} calls.
   */
  private final StringBuilder tokenBuffer = new StringBuilder();

  private final StringBuilder text = new StringBuilder();

  private int offset;
  private final Stack<Constituent> stack = new Stack<>();
  private final List<Constituent> cons = new LinkedList<>();

  ConstitDocumentHandler(List<Parse> parses) {
    this.parses = parses;
  }

  private String cat;
  private String subcat;

  @Override
  public void startElement(String uri, String localName, String qName,
      Attributes attributes) throws SAXException {

    String type = qName;

    if (SENT_ELEMENT_NAME.equals(qName)) {
      // Clear everything to be ready for the next sentence
      text.setLength(0);
      offset = 0;
      stack.clear();
      cons.clear();

      type = SENT_TYPE_NAME;

      insideSentenceElement = true;
    }
    else if (WORD_ELEMENT_NAME.equals(qName)) {

      // Note:
      // If there are compound words they are represented in a couple
      // of ways in the training data.
      // Many of them are marked with the compound attribute, but not
      // all of them. Thats why it is not used in the code to detect
      // a compound word.
      // Compounds are detected by the fact that a w tag is appearing
      // inside a w tag.
      //
      // The type of a compound word can be encoded either cat of the compound
      // plus the catint of each word of the compound.
      // Or all compound words have the cat plus subcat of the compound, in this
      // case they have an empty cat attribute.
      //
      // This implementation hopefully decodes these cases correctly!

      String newCat = attributes.getValue("cat");
      if (newCat != null && newCat.length() > 0) {
        cat = newCat;
      }

      String newSubcat = attributes.getValue("subcat");
      if (newSubcat != null && newSubcat.length() > 0) {
        subcat = newSubcat;
      }

      if (cat != null) {
        type = cat + (subcat != null ? subcat : "");
      }
      else {
        String catint = attributes.getValue("catint");
        if (catint != null) {
          type = cat + (catint != null ? catint : "");
        }
        else {
          type = cat + subcat;
        }
      }
    }

    stack.push(new Constituent(type, new Span(offset, offset)));

    tokenBuffer.setLength(0);
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    tokenBuffer.append(ch, start, length);
  }

  @Override
  public void endElement(String uri, String localName, String qName)
      throws SAXException {

    boolean isCreateConstituent = true;

    if (insideSentenceElement) {
      if (WORD_ELEMENT_NAME.equals(qName)) {
        String token = tokenBuffer.toString().trim();

        if (token.length() > 0) {
          cons.add(new Constituent(AbstractBottomUpParser.TOK_NODE,
              new Span(offset, offset + token.length())));

          text.append(token).append(" ");
          offset += token.length() + 1;
        }
        else {
          isCreateConstituent = false;
        }
      }

      Constituent unfinishedCon = stack.pop();

      if (isCreateConstituent) {
        int start = unfinishedCon.getSpan().getStart();
        if (start < offset) {
          cons.add(new Constituent(unfinishedCon.getLabel(), new Span(start, offset - 1)));
        }
      }

      if (SENT_ELEMENT_NAME.equals(qName)) {
        // Finished parsing sentence, now put everything together and create
        // a Parse object

        String txt = text.toString();
        int tokenIndex = -1;
        Parse p = new Parse(txt, new Span(0, txt.length()), AbstractBottomUpParser.TOP_NODE, 1,0);
        for (int ci = 0; ci < cons.size(); ci++) {
          Constituent con = cons.get(ci);
          String type = con.getLabel();
          if (!type.equals(AbstractBottomUpParser.TOP_NODE)) {
            if (AbstractBottomUpParser.TOK_NODE.equals(type)) {
              tokenIndex++;
            }
            Parse c = new Parse(txt, con.getSpan(), type, 1,tokenIndex);
            p.insert(c);
          }
        }
        parses.add(p);

        insideSentenceElement = false;
      }

      tokenBuffer.setLength(0);
    }
  }
}
