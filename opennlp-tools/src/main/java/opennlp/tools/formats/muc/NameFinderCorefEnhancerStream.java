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

import opennlp.tools.coref.CorefSample;
import opennlp.tools.coref.mention.DefaultParse;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * Adds names to the Coref Sample Stream.
 */
public class NameFinderCorefEnhancerStream extends FilterObjectStream<CorefSample, CorefSample> {

  private TokenNameFinder nameFinders[];
  private String tags[];
  
  // TODO: Should be updated to use tag from span instead!
  protected NameFinderCorefEnhancerStream(TokenNameFinder nameFinders[], String tags[], ObjectStream<CorefSample> samples) {
    super(samples);
    this.nameFinders = nameFinders;
    this.tags = tags;
  }

  public CorefSample read() throws IOException {
    
    CorefSample sample = samples.read();
    
    if (sample != null) {

      for (TokenNameFinder namefinder : nameFinders) {
        namefinder.clearAdaptiveData();
      }
      
      List<Parse> parses = new ArrayList<Parse>();
      
      for (opennlp.tools.coref.mention.Parse corefParse : sample.getParses()) {
        Parse p = ((DefaultParse) corefParse).getParse();
        
        Parse parseTokens[] = p.getTagNodes();
        String tokens[] = new String[parseTokens.length];
        
        for (int i = 0; i < tokens.length; i++) {
          tokens[i] = parseTokens[i].toString();
        }
        
        for (int i = 0; i < nameFinders.length; i++) {
          Span names[] = nameFinders[i].find(tokens);
          Parse.addNames(tags[i], names, parseTokens);
        }
        
        parses.add(p);
      }
      
      return new CorefSample(parses);
    }
    else {
      return null;
    }
  }
}
