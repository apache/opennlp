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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

// Note:
// Take care for special @ sign handling (identifies a table or something else that should be ignored)
class MucCorefContentHandler extends SgmlParser.ContentHandler {

  static class CorefMention {
    Span span;
    int id;
    String min;
    
    CorefMention(Span span, int id, String min) {
      this.span = span;
      this.id = id;
      this.min = min;
    }
  }
  
  static final String COREF_ELEMENT = "COREF";
  
  private final Tokenizer tokenizer;
  private final List<RawCorefSample> samples;
  
  boolean isInsideContentElement = false;
  private final List<String> text = new ArrayList<String>();
  private Stack<CorefMention> mentionStack = new Stack<CorefMention>();
  private List<CorefMention> mentions = new ArrayList<MucCorefContentHandler.CorefMention>();

  private Map<Integer, Integer> idMap = new HashMap<Integer, Integer>();

  private RawCorefSample sample;
  
  MucCorefContentHandler(Tokenizer tokenizer, List<RawCorefSample> samples) {
    this.tokenizer = tokenizer;
    this.samples = samples;
  }
  
  /**
   * Resolve an id via the references to the root id.
   * 
   * @param id the id or reference to be resolved
   * 
   * @return the resolved id or -1 if id cannot be resolved
   */
  private int resolveId(int id) {
    
    Integer refId = idMap.get(id);
    
    if (refId != null) {
      if (id == refId) {
        return id;
      }
      else {
        return resolveId(refId);
      }
    }
    else {
      return -1;
    }
  }
  
  @Override
  public void startElement(String name, Map<String, String> attributes) {
    
    if (MucElementNames.DOC_ELEMENT.equals(name)) {
      idMap.clear();
      sample = new RawCorefSample(new ArrayList<String>(),
          new ArrayList<MucCorefContentHandler.CorefMention[]>());
    }
    
    if (MucElementNames.CONTENT_ELEMENTS.contains(name)) {
      isInsideContentElement = true;
    }
    
    if (COREF_ELEMENT.equals(name)) {
      int beginOffset = text.size();
      
      String idString = attributes.get("ID");
      String refString = attributes.get("REF");
      
      int id;
      if (idString != null) {
        id = Integer.parseInt(idString); // might fail
        
        if (refString == null) {
          idMap.put(id, id);
        }
        else {
          int ref = Integer.parseInt(refString);
          idMap.put(id, ref);
        }
      }
      else {
        id = -1;
        // throw invalid format exception ...
      }
        
      mentionStack.push(new CorefMention(new Span(beginOffset, beginOffset), id, attributes.get("MIN")));
    }
  }
  
  @Override
  public void characters(CharSequence chars) {
    if (isInsideContentElement) {
      
      String tokens [] = tokenizer.tokenize(chars.toString());
      
      text.addAll(Arrays.asList(tokens));
    }
  }
  
  @Override
  public void endElement(String name) {
    
    if (COREF_ELEMENT.equals(name)) {
      CorefMention mention = mentionStack.pop();
      mention.span = new Span(mention.span.getStart(), text.size());
      mentions.add(mention);
    }
    
    if (MucElementNames.CONTENT_ELEMENTS.contains(name)) {
      
      sample.getTexts().add(text.toArray(new String[text.size()]));
      sample.getMentions().add(mentions.toArray(new CorefMention[mentions.size()]));
      
      mentions.clear();
      text.clear();
      isInsideContentElement = false;
    }
    
    if (MucElementNames.DOC_ELEMENT.equals(name)) {
      
      for (CorefMention mentions[] : sample.getMentions()) {
        for (int i = 0; i < mentions.length; i++) {
          mentions[i].id = resolveId(mentions[i].id);
        }
      }
      
      samples.add(sample);
    }
  }
}
