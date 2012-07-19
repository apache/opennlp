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

package opennlp.tools.lang.english;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import opennlp.tools.namefind.NameFinderEventStream;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.parser.Parse;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;

/**
 * Class is used to create a name finder for English.
 * 
 * @deprecated will be removed soon!
 */
@Deprecated
public class TreebankNameFinder {
  
  public static String[] NAME_TYPES = {"person", "organization", "location", "date", "time", "percentage", "money"};

  private NameFinderME nameFinder;
  
  /** Creates an English name finder using the specified model.
   * @param mod The model used for finding names.
   */
  public TreebankNameFinder(TokenNameFinderModel mod) {
    nameFinder = new NameFinderME(mod);
  }

  private static void clearPrevTokenMaps(TreebankNameFinder[] finders) {
    for (int mi = 0; mi < finders.length; mi++) {
      finders[mi].nameFinder.clearAdaptiveData();
    }
  }

  private static void processParse(TreebankNameFinder[] finders, String[] tags, BufferedReader input) throws IOException {
    Span[][] nameSpans = new Span[finders.length][];
    
    for (String line = input.readLine(); null != line; line = input.readLine()) {
      if (line.equals("")) {
        System.out.println();
        clearPrevTokenMaps(finders);
        continue;
      }
      Parse p = Parse.parseParse(line);
      Parse[] tagNodes = p.getTagNodes();
      String[] tokens = new String[tagNodes.length];
      for (int ti=0;ti<tagNodes.length;ti++){
        tokens[ti] = tagNodes[ti].getCoveredText();
      }
      //System.err.println(java.util.Arrays.asList(tokens));
      for (int fi = 0, fl = finders.length; fi < fl; fi++) {
        nameSpans[fi] = finders[fi].nameFinder.find(tokens);
        //System.err.println("english.NameFinder.processParse: "+tags[fi] + " " + java.util.Arrays.asList(nameSpans[fi]));
      }
      
      for (int fi = 0, fl = finders.length; fi < fl; fi++) {
        Parse.addNames(tags[fi],nameSpans[fi],tagNodes);
      }
      p.show();
    }
  }
      
  /**
   * Adds sgml style name tags to the specified input buffer and outputs this information to stdout. 
   * @param finders The name finders to be used.
   * @param tags The tag names for the corresponding name finder.
   * @param input The input reader.
   * @throws IOException
   */
  private static void processText(TreebankNameFinder[] finders, String[] tags, BufferedReader input) throws IOException {
    Span[][] nameSpans = new Span[finders.length][];
    String[][] nameOutcomes = new String[finders.length][];
    opennlp.tools.tokenize.Tokenizer tokenizer = new SimpleTokenizer();
    StringBuffer output = new StringBuffer();
    for (String line = input.readLine(); null != line; line = input.readLine()) {
      if (line.equals("")) {
        clearPrevTokenMaps(finders);
        System.out.println();
        continue;
      }
      output.setLength(0);
      Span[] spans = tokenizer.tokenizePos(line);
      String[] tokens = Span.spansToStrings(spans,line);
      for (int fi = 0, fl = finders.length; fi < fl; fi++) {
        nameSpans[fi] = finders[fi].nameFinder.find(tokens);
        //System.err.println("EnglighNameFinder.processText: "+tags[fi] + " " + java.util.Arrays.asList(finderTags[fi]));
        nameOutcomes[fi] = NameFinderEventStream.generateOutcomes(nameSpans[fi], null, tokens.length);
      }
      
      for (int ti = 0, tl = tokens.length; ti < tl; ti++) {
        for (int fi = 0, fl = finders.length; fi < fl; fi++) {
          //check for end tags
          if (ti != 0) {
            if ((nameOutcomes[fi][ti].equals(NameFinderME.START) || nameOutcomes[fi][ti].equals(NameFinderME.OTHER)) && 
                (nameOutcomes[fi][ti - 1].equals(NameFinderME.START) || nameOutcomes[fi][ti - 1].equals(NameFinderME.CONTINUE))) {
              output.append("</").append(tags[fi]).append(">");
            }
          }
        }
        if (ti > 0 && spans[ti - 1].getEnd() < spans[ti].getStart()) {
          output.append(line.substring(spans[ti - 1].getEnd(), spans[ti].getStart()));
        }
        //check for start tags
        for (int fi = 0, fl = finders.length; fi < fl; fi++) {
          if (nameOutcomes[fi][ti].equals(NameFinderME.START)) {
            output.append("<").append(tags[fi]).append(">");
          }
        }
        output.append(tokens[ti]);
      }
      //final end tags
      if (tokens.length != 0) {
        for (int fi = 0, fl = finders.length; fi < fl; fi++) {
          if (nameOutcomes[fi][tokens.length - 1].equals(NameFinderME.START) || nameOutcomes[fi][tokens.length - 1].equals(NameFinderME.CONTINUE)) {
            output.append("</").append(tags[fi]).append(">");
          }
        }
      }
      if (tokens.length != 0) {
        if (spans[tokens.length - 1].getEnd() < line.length()) {
          output.append(line.substring(spans[tokens.length - 1].getEnd()));
        }
      }
      System.out.println(output);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage NameFinder -[parse] model1 model2 ... modelN < sentences");
      System.err.println(" -parse: Use this option to find names on parsed input.  Un-tokenized sentence text is the default.");
      System.exit(1);
    }
    int ai = 0;
    boolean parsedInput = false;
    while (args[ai].startsWith("-") && ai < args.length) {
      if (args[ai].equals("-parse")) {
        parsedInput = true;
      }
      else {
        System.err.println("Ignoring unknown option "+args[ai]);
      }
      ai++;
    }
    TreebankNameFinder[] finders = new TreebankNameFinder[args.length-ai];
    String[] names = new String[args.length-ai];
    for (int fi=0; ai < args.length; ai++,fi++) {
      String modelName = args[ai];
      finders[fi] = new TreebankNameFinder(new TokenNameFinderModel(new FileInputStream(modelName)));
      int nameStart = modelName.lastIndexOf(System.getProperty("file.separator")) + 1;
      int nameEnd = modelName.indexOf('.', nameStart);
      if (nameEnd == -1) {
        nameEnd = modelName.length();
      }
      names[fi] = modelName.substring(nameStart, nameEnd);
    }
    //long t1 = System.currentTimeMillis();
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    if (parsedInput) {
      processParse(finders,names,in);
    }
    else {
      processText(finders,names,in);
    }
    //long t2 = System.currentTimeMillis();
    //System.err.println("Time "+(t2-t1));
  }
}