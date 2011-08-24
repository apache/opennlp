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

package opennlp.tools.cmdline.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public final class ParserTool implements CmdLineTool {

  public String getName() {
    return "Parser";
  }
  
  public String getShortDescription() {
    return "performs full syntactic parsing";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " [-bs n -ap n -k n] model < sentences \n" +
        "-bs n: Use a beam size of n.\n" +
        "-ap f: Advance outcomes in with at least f% of the probability mass.\n" + 
        "-k n: Show the top n parses.  This will also display their log-probablities.";
  }

  private static Pattern untokenizedParenPattern1 = Pattern.compile("([^ ])([({)}])");
  private static Pattern untokenizedParenPattern2 = Pattern.compile("([({)}])([^ ])");

  public static Parse[] parseLine(String line, opennlp.tools.parser.Parser parser, int numParses) {
    line = untokenizedParenPattern1.matcher(line).replaceAll("$1 $2");
    line = untokenizedParenPattern2.matcher(line).replaceAll("$1 $2");
    StringTokenizer str = new StringTokenizer(line);
    StringBuffer sb = new StringBuffer();
    List<String> tokens = new ArrayList<String>();
    while (str.hasMoreTokens()) {
      String tok = str.nextToken();
      tokens.add(tok);
      sb.append(tok).append(" ");
    }
    String text = sb.substring(0, sb.length() - 1);
    Parse p = new Parse(text, new Span(0, text.length()), AbstractBottomUpParser.INC_NODE, 0, 0);
    int start = 0;
    int i=0;
    for (Iterator<String> ti = tokens.iterator(); ti.hasNext();i++) {
      String tok = ti.next();
      p.insert(new Parse(text, new Span(start, start + tok.length()), AbstractBottomUpParser.TOK_NODE, 0,i));
      start += tok.length() + 1;
    }
    Parse[] parses;
    if (numParses == 1) {
      parses = new Parse[] { parser.parse(p)};
    }
    else {
      parses = parser.parse(p,numParses);
    }
    return parses;
  }
  
  public void run(String[] args) {
    
    if (args.length < 1) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    ParserModel model = new ParserModelLoader().load(new File(args[args.length - 1]));
    
    Integer beamSize = CmdLineUtil.getIntParameter("-bs", args);
    if (beamSize == null)
        beamSize = AbstractBottomUpParser.defaultBeamSize;
    
    Integer numParses = CmdLineUtil.getIntParameter("-k", args);
    boolean showTopK;
    if (numParses == null) {
      numParses = 1;
      showTopK = false;
    }
    else {
      showTopK = true;
    }
    
    Double advancePercentage = CmdLineUtil.getDoubleParameter("-ap", args);
    
    if (advancePercentage == null)
      advancePercentage = AbstractBottomUpParser.defaultAdvancePercentage;
      
    opennlp.tools.parser.Parser parser = 
        ParserFactory.create(model, beamSize, advancePercentage); 

    ObjectStream<String> lineStream =
      new PlainTextByLineStream(new InputStreamReader(System.in));
    
    PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "sent");
    perfMon.start();
    
    try {
      String line;
      while ((line = lineStream.read()) != null) {
        if (line.length() == 0) {
          System.out.println();
        }
        else {
          Parse[] parses = parseLine(line, parser, numParses);
          
          for (int pi=0,pn=parses.length;pi<pn;pi++) {
            if (showTopK) {
              System.out.print(pi+" "+parses[pi].getProb()+" ");
            }
            
            parses[pi].show();
            
            perfMon.incrementCounter();
          }
        }
      }
    } 
    catch (IOException e) {
      CmdLineUtil.handleStdinIoError(e);
    }
    
    perfMon.stopAndPrintFinalResult();
  }
}
