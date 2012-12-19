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

package opennlp.tools.cmdline.coref;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.LinkerMode;
import opennlp.tools.coref.mention.DefaultParse;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.lang.english.TreebankLinker;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class CoreferencerTool extends BasicCmdLineTool {

  class CorefParse {

    private Map<Parse, Integer> parseMap;
    private List<Parse> parses;

    public CorefParse(List<Parse> parses, DiscourseEntity[] entities) {
      this.parses = parses;
      parseMap = new HashMap<Parse, Integer>();
      for (int ei=0,en=entities.length;ei<en;ei++) {
        if (entities[ei].getNumMentions() > 1) {
          for (Iterator<MentionContext> mi = entities[ei].getMentions(); mi.hasNext();) {
            MentionContext mc = mi.next();
            Parse mentionParse = ((DefaultParse) mc.getParse()).getParse();
            parseMap.put(mentionParse,ei+1);
          }
        }
      }
    }

    public void show() {
      for (int pi=0,pn=parses.size();pi<pn;pi++) {
        Parse p = parses.get(pi);
        show(p);
        System.out.println();
      }
    }

    private void show(Parse p) {
      int start;
      start = p.getSpan().getStart();
      if (!p.getType().equals(Parser.TOK_NODE)) {
        System.out.print("(");
        System.out.print(p.getType());
        if (parseMap.containsKey(p)) {
          System.out.print("#"+parseMap.get(p));
        }
        //System.out.print(p.hashCode()+"-"+parseMap.containsKey(p));
        System.out.print(" ");
      }
      Parse[] children = p.getChildren();
      for (int pi=0,pn=children.length;pi<pn;pi++) {
        Parse c = children[pi];
        Span s = c.getSpan();
        if (start < s.getStart()) {
          System.out.print(p.getText().substring(start, s.getStart()));
        }
        show(c);
        start = s.getEnd();
      }
      System.out.print(p.getText().substring(start, p.getSpan().getEnd()));
      if (!p.getType().equals(Parser.TOK_NODE)) {
        System.out.print(")");
      }
    }
  }
  
  public String getShortDescription() {
    return "learnable noun phrase coreferencer";
  }
  
  public void run(String[] args) {
    if (args.length != 1) {
      System.out.println(getHelp());
    }
    else {
      
      TreebankLinker treebankLinker;
      try {
        treebankLinker = new TreebankLinker(args[0], LinkerMode.TEST);
      } catch (IOException e) {
        throw new TerminateToolException(-1, "Failed to load all coreferencer models!", e);
      }
      
      ObjectStream<String> lineStream =
          new PlainTextByLineStream(new InputStreamReader(System.in));
      
      PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "parses");
      perfMon.start();
      
      try {
        
        int sentenceNumber = 0;
        List<Mention> document = new ArrayList<Mention>();
        List<Parse> parses = new ArrayList<Parse>();
        
        String line;
        while ((line = lineStream.read()) != null) {

          if (line.equals("")) {
            DiscourseEntity[] entities = treebankLinker.getEntities(document.toArray(new Mention[document.size()]));
            //showEntities(entities);
            new CorefParse(parses,entities).show();
            sentenceNumber=0;
            document.clear();
            parses.clear();
          }
          else {
            Parse p = Parse.parseParse(line);
            parses.add(p);
            Mention[] extents = treebankLinker.getMentionFinder().getMentions(new DefaultParse(p,sentenceNumber));
            //construct new parses for mentions which don't have constituents.
            for (int ei=0,en=extents.length;ei<en;ei++) {
              //System.err.println("PennTreebankLiner.main: "+ei+" "+extents[ei]);

              if (extents[ei].getParse() == null) {
                //not sure how to get head index, but its not used at this point.
                Parse snp = new Parse(p.getText(),extents[ei].getSpan(),"NML",1.0,0);
                p.insert(snp);
                extents[ei].setParse(new DefaultParse(snp,sentenceNumber));
              }

            }
            document.addAll(Arrays.asList(extents));
            sentenceNumber++;
          }
          
          perfMon.incrementCounter();
        }
      }
      catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }
      
      perfMon.stopAndPrintFinalResult();
    }
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model_directory < parses";
  }
}
