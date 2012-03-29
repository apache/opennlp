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

import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Constituent;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.Span;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class ConstitDocumentHandler extends DefaultHandler {
  
  private static final String SENT_ELEMENT_NAME = "SENT";
  private static final String WORD_ELEMENT_NAME = "w";
  private static final String COMPOUND_ATTR_NAME = "compound";
  
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
  private final Stack<Constituent> stack = new Stack<Constituent>();
  private final List<Constituent> cons = new LinkedList<Constituent>();
  
  ConstitDocumentHandler(List<Parse> parses) {
    this.parses = parses;
  }
  
  private String compoundCat;
  
  @Override
  public void startElement(String uri, String localName, String qName,
      Attributes attributes) throws SAXException {
    
    String type = qName;

    boolean isCompoundWord = false;
    
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
      
      // insideCompoundElement
      if (attributes.getValue(COMPOUND_ATTR_NAME) != null) {
        isCompoundWord = "yes".equals(COMPOUND_ATTR_NAME);
      }
      
      String cat = attributes.getValue("cat");
      
      if (isCompoundWord) {
        compoundCat = cat;
      }
      
      if (cat != null) {
        String subcat = attributes.getValue("subcat");
        type = cat + (subcat != null ? subcat : "");
      }
      else {
        String catint = attributes.getValue("catint");
        type = compoundCat + (catint != null ? catint : "");
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
          cons.add(new Constituent(unfinishedCon.getLabel(), new Span(start, offset-1)));
        }
      }
      
      if (SENT_ELEMENT_NAME.equals(qName)) {
        // Finished parsing sentence, now put everything together and create
        // a Parse object
        
        String txt = text.toString();
        int tokenIndex = -1;
        Parse p = new Parse(txt, new Span(0, txt.length()), AbstractBottomUpParser.TOP_NODE, 1,0);
        for (int ci=0;ci < cons.size();ci++) {
          Constituent con = cons.get(ci);
          String type = con.getLabel();
          if (!type.equals(AbstractBottomUpParser.TOP_NODE)) {
            if (type == AbstractBottomUpParser.TOK_NODE) {
              tokenIndex++;
            }
            Parse c = new Parse(txt, con.getSpan(), type, 1,tokenIndex);
            p.insert(c);
          }
        }
        parses.add(p);
        
        insideSentenceElement = false;
      }
    }
  }
}
