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

import opennlp.tools.chunker.Chunker;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

public class ShallowParseCorefEnhancerStream extends FilterObjectStream<RawCorefSample, RawCorefSample> {

  private final POSTagger posTagger;
  private final Chunker chunker;
  
  public ShallowParseCorefEnhancerStream(POSTagger posTagger, Chunker chunker, ObjectStream<RawCorefSample> samples) {
    super(samples);
    this.posTagger = posTagger;
    this.chunker = chunker;
  }
  
  public RawCorefSample read() throws IOException {
    
    RawCorefSample sample = samples.read();
    
    if (sample != null) {
      
      List<Parse> enhancedParses = new ArrayList<Parse>();
      
      List<String[]> sentences = sample.getTexts();
      
      for (String sentence[] : sentences) {
        
        Parse p = FullParseCorefEnhancerStream.createIncompleteParse(sentence);
        p.setType(AbstractBottomUpParser.TOP_NODE);
        
        Parse parseTokens[] = p.getChildren();
        
        // construct incomplete parse here ..
        String tags[] = posTagger.tag(sentence);
        
        for (int i = 0; i < parseTokens.length; i++) {
          p.insert(new Parse(p.getText(), parseTokens[i].getSpan(), tags[i], 1d, parseTokens[i].getHeadIndex()));
        }
        
        // insert tags into incomplete parse
        Span chunks[] = chunker.chunkAsSpans(sentence, tags); 
        
        for (Span chunk : chunks) {
          if ("NP".equals(chunk.getType())) {
            p.insert(new Parse(p.getText(), new Span(0,0), chunk.getType(), 1d, p.getHeadIndex()));
          }
        }
        
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
