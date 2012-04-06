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
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class FullParseCorefEnhancerStream extends FilterObjectStream<RawCorefSample, RawCorefSample> {

  private final Parser parser;

  public FullParseCorefEnhancerStream(Parser parser, ObjectStream<RawCorefSample> samples) {
    super(samples);
    this.parser = parser;
  }
  
  static Parse createIncompleteParse(String tokens[]) {
    
    // produce text
    Span tokenSpans[] = new Span[tokens.length];
    StringBuilder textBuilder = new StringBuilder();
    
    for (int i = 0; i < tokens.length; i++) {
      
      if (textBuilder.length() > 0) {
        textBuilder.append(' ');
      }
      
      int startOffset = textBuilder.length();
      textBuilder.append(tokens[i]);
      tokenSpans[i] = new Span(startOffset, textBuilder.length());
    }
    
    String text = textBuilder.toString();
    
    Parse p = new Parse(text, new Span(0, text.length()), AbstractBottomUpParser.INC_NODE, 0, 0);
    
    for (int i = 0; i < tokenSpans.length; i++) {
      Span tokenSpan = tokenSpans[i];
      p.insert(new Parse(text, new Span(tokenSpan.getStart(), tokenSpan.getEnd()), AbstractBottomUpParser.TOK_NODE, 0, i));
    }
    
    return p;
  }
  
  public RawCorefSample read() throws IOException {
    
    RawCorefSample sample = samples.read();
    
    if (sample != null) {

      List<Parse> enhancedParses = new ArrayList<Parse>();
      
      List<String[]> sentences = sample.getTexts();
      
      for (int i = 0; i < sentences.size(); i++) {
        
        String sentence[] = sentences.get(i);
        
        Parse incompleteParse = createIncompleteParse(sentence);
        Parse p = parser.parse(incompleteParse);
        
        // What to do when a parse cannot be found ?!
        
        enhancedParses.add(p);
      }
      
      sample.setParses(enhancedParses);
      
      return sample;
    }
    else {
      return null;
    }
  }
}
