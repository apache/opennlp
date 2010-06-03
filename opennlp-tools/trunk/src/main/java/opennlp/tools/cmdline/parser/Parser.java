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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class Parser implements CmdLineTool {

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

  // TODO: We should do this conversion on training time ... 
  private static String convertToken(String token) {
    if (token.equals("(")) {
      return "-LRB-";
    }
    else if (token.equals(")")) {
      return "-RRB-";
    }
    else if (token.equals("{")) {
      return "-LCB-";
    }
    else if (token.equals("}")) {
      return "-RCB-";
    }
    return token;
  }

  static ParserModel loadModel(File modelFile) {
    
    CmdLineUtil.checkInputFile("Parser model", modelFile);

    System.err.print("Loading model ... ");
    
    ParserModel model;
    try {
      InputStream modelIn = new BufferedInputStream(new FileInputStream(modelFile), 
          1000000);
      model = new ParserModel(modelIn);
      modelIn.close();
    }
    catch (IOException e) {
      System.err.println("failed");
      System.err.println("IO error while loading model: " + e.getMessage());
      System.exit(-1);
      model = null;
    }
    catch (InvalidFormatException e) {
      System.err.println("failed");
      System.err.println("Model has invalid format: " + e.getMessage());
      System.exit(-1);
      model = null;
    }
    
    System.err.println("done");
    
    return model;
  }
  
  public static Parse[] parseLine(String line, opennlp.tools.parser.Parser parser, int numParses) {
    line = untokenizedParenPattern1.matcher(line).replaceAll("$1 $2");
    line = untokenizedParenPattern2.matcher(line).replaceAll("$1 $2");
    StringTokenizer str = new StringTokenizer(line);
    StringBuffer sb = new StringBuffer();
    List<String> tokens = new ArrayList<String>();
    while (str.hasMoreTokens()) {
      String tok = convertToken(str.nextToken());
      tokens.add(tok);
      sb.append(tok).append(" ");
    }
    String text = sb.substring(0, sb.length() - 1);
    Parse p = new Parse(text, new Span(0, text.length()), AbstractBottomUpParser.INC_NODE, 1, 0);
    int start = 0;
    int i=0;
    for (Iterator<String> ti = tokens.iterator(); ti.hasNext();i++) {
      String tok = (String) ti.next();
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
      System.exit(1);
    }
    
    ParserModel model = loadModel(new File(args[args.length - 1]));
    
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
    
    // TODO: Set advance percentage and beam size
    Double advancePercentage = CmdLineUtil.getDoubleParameter("-ap", args);
    
    if (advancePercentage == null)
      advancePercentage = AbstractBottomUpParser.defaultAdvancePercentage;
      
    opennlp.tools.parser.Parser parser = 
        ParserFactory.create(model, beamSize, advancePercentage); 

    ObjectStream<String> lineStream =
      new PlainTextByLineStream(new InputStreamReader(System.in));
    
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
          }
        }
      }
    } 
    catch (ObjectStreamException e) {
      e.printStackTrace();
    }   
  }
}
