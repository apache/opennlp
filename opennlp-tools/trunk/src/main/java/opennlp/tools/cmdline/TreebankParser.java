/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.cmdline;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.Span;

/**
 * Class for performing full parsing on English text.
 */
public class TreebankParser {

  private static Pattern untokenizedParenPattern1 = Pattern.compile("([^ ])([({)}])");
  private static Pattern untokenizedParenPattern2 = Pattern.compile("([({)}])([^ ])");

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

  public static Parse[] parseLine(String line, Parser parser, int numParses) {
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

  private static void usage() {
    System.err.println("Usage: TreebankParser [-bs n -ap f] model < tokenized_sentences");
    System.err.println("model the parser model.");
    System.err.println("-bs 20: Use a beam size of 20.");
    System.err.println("-ap 0.95: Advance outcomes in with at least 95% of the probability mass.");
    System.err.println("-k 5: Show the top 5 parses.  This will also display their log-probablities.");
    System.exit(1);
  }


  public static void main(String[] args) throws InvalidFormatException, IOException {
    if (args.length == 0) {
      usage();
    }
    boolean showTopK = false;
    int numParses = 1;
    int ai = 0;

    // TODO: Set these parameters on the parser
    int beamSize = AbstractBottomUpParser.defaultBeamSize;
    double advancePercentage = AbstractBottomUpParser.defaultAdvancePercentage;
    while (args[ai].startsWith("-")) {
      if (args[ai].equals("-bs")) {
      	if (args.length > ai+1) {
          try {
            beamSize=Integer.parseInt(args[ai+1]);
            ai++;
          }
          catch(NumberFormatException nfe) {
            System.err.println(nfe);
            usage();
          }
      	}
      	else {
      	  usage();
      	}
      }
      else if (args[ai].equals("-ap")) {
        if (args.length > ai+1) {
          try {
            advancePercentage=Double.parseDouble(args[ai+1]);
            ai++;
          }
          catch(NumberFormatException nfe) {
            System.err.println(nfe);
            usage();
          }
      	}
      	else {
      	  usage();
      	}
      }
      else if (args[ai].equals("-k")) {
        showTopK = true;
        if (args.length > ai+1) {
          try {
            numParses=Integer.parseInt(args[ai+1]);
            ai++;
          }
          catch(NumberFormatException nfe) {
            System.err.println(nfe);
            usage();
          }
      	}
      	else {
      	  usage();
      	}
      }
      else if (args[ai].equals("--")) {
      	ai++;
        break;
      }
      else {
        System.err.println("Unknown option "+args[ai]);
        usage();
      }
      ai++;
    }

    ParserModel model = ParserModel.create(new FileInputStream(args[ai]));
    Parser parser = new opennlp.tools.parser.chunking.Parser(model);

    BufferedReader in;
    if (ai == args.length) {
      in = new BufferedReader(new InputStreamReader(System.in));
    }
    else {
      in = new BufferedReader(new FileReader(args[ai]));
    }
    String line;
    try {
      while (null != (line = in.readLine())) {
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
    catch (IOException e) {
      System.err.println(e);
    }
  }
}
