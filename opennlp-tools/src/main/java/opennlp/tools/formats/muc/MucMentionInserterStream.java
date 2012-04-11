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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.coref.CorefSample;
import opennlp.tools.coref.mention.DefaultParse;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.coref.mention.MentionFinder;
import opennlp.tools.coref.mention.PTBHeadFinder;
import opennlp.tools.coref.mention.PTBMentionFinder;
import opennlp.tools.formats.muc.MucCorefContentHandler.CorefMention;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * The mention insert is responsible to insert the mentions from the training data
 * into the parse trees.
 */
public class MucMentionInserterStream extends FilterObjectStream<RawCorefSample, CorefSample> {

  private static Set<String> entitySet = new HashSet<String>(Arrays.asList(DefaultParse.NAME_TYPES));
  
  private final MentionFinder mentionFinder;
  
  protected MucMentionInserterStream(ObjectStream<RawCorefSample> samples) {
    super(samples);
    
    mentionFinder = PTBMentionFinder.getInstance(PTBHeadFinder.getInstance());
  }

  private static Span getMinSpan(Parse p, CorefMention mention) {
    String min = mention.min;
    
    if (min != null) {
      
      int startOffset = p.toString().indexOf(min);
      int endOffset = startOffset + min.length();
      
      Parse tokens[] = p.getTagNodes();
      
      int beginToken = -1;
      int endToken = -1;
      
      for (int i = 0; i < tokens.length; i++) {
        if (tokens[i].getSpan().getStart() == startOffset) {
          beginToken = i;
        }
        
        if (tokens[i].getSpan().getEnd() == endOffset) {
          endToken = i + 1;
          break;
        }
      }
      
      if (beginToken != -1 && endToken != -1) {
        return new Span(beginToken, endToken);
      }
    }
    
    return null;
  }
  
  public static boolean addMention(int id, Span mention, Parse[] tokens) {

	boolean failed = false;
    
    Parse startToken = tokens[mention.getStart()];
    Parse endToken = tokens[mention.getEnd() - 1];
    Parse commonParent = startToken.getCommonParent(endToken);
    
    if (commonParent != null) {
//      Span mentionSpan = new Span(startToken.getSpan().getStart(), endToken.getSpan().getEnd());
      
      if (entitySet.contains(commonParent.getType())) {
        commonParent.getParent().setType("NP#" + id);            
      }
      else if (commonParent.getType().equals("NML")) {
        commonParent.setType("NML#" + id);
      }
      else if (commonParent.getType().equals("NP")) {
        commonParent.setType("NP#" + id);
      }
      else {
        System.out.println("Inserting mention failed: " + commonParent.getType() + " Failed id: " + id);
        failed = true;
      }
    }
    else {
      throw new IllegalArgumentException("Tokens must always have a common parent!");
    }
    
    return !failed;
  }
  
  public CorefSample read() throws IOException {
    
    RawCorefSample sample = samples.read();
    
    if (sample != null) {

      List<Parse> mentionParses = new ArrayList<Parse>();
      
      List<CorefMention[]> allMentions = sample.getMentions();
      List<Parse> allParses = sample.getParses();
      
      for (int si = 0; si < allMentions.size(); si++) {
        CorefMention mentions[] = allMentions.get(si);
        Parse p = allParses.get(si);
        
        for (Mention extent : mentionFinder.getMentions(new DefaultParse(p, si))) {
          if (extent.getParse() == null) {
            // not sure how to get head index
            Parse snp = new Parse(p.getText(),extent.getSpan(),"NML",1.0,0);
            p.insert(snp);
          }
        }
        
        Parse tokens[] = p.getTagNodes();
        
        for (CorefMention mention : mentions) {
          Span min = getMinSpan(p, mention);
          
          if (min == null) {
            min = mention.span;
          }
          
          addMention(mention.id, min, tokens);
        }
        
        p.show();
        
        mentionParses.add(p);
      }
      
      return new CorefSample(mentionParses);
    }
    else {
      return null;
    }
  }
}
