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


package opennlp.tools.lang.german;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.model.MaxentModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.postag.DefaultPOSContextGenerator;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.TagDictionary;
import opennlp.tools.util.InvalidFormatException;

/**
 * A part of speech tagger that uses a model trained on German data from the German Treebank.
 * @Deprecated
 */
public class PosTagger extends POSTaggerME {

  public PosTagger(String modelFile, Dictionary dict, TagDictionary tagdict) {
      super(getModel(modelFile), new DefaultPOSContextGenerator(dict),tagdict);
  }
  
  public PosTagger(String modelFile, TagDictionary tagdict) {
    super(getModel(modelFile), new DefaultPOSContextGenerator(null),tagdict);
}

  public PosTagger(String modelFile, Dictionary dict) {
    super(getModel(modelFile), new DefaultPOSContextGenerator(dict));
  }

  private static MaxentModel getModel(String name) {
    try {
      return new SuffixSensitiveGISModelReader(new File(name)).getModel();
    }
    catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public static void usage() {
    System.err.println("Usage: PosTagger [-d tagdict] [-di case_insensiteve_tagdict] [-k 5] model < tokenized_sentences");
    System.err.println("-d tagdict Specifies that a tag dictionary file should be used.");
    System.err.println("-di tagdict Specifies that a case-insensitive tag dictionary should be used.");
    System.err.println("-k n tagdict Specifies that the top n tagging should be reported.");
    System.exit(1);    
  }

  public static void main(String[] args) throws InvalidFormatException, IOException {
    if (args.length == 0) {
      usage();
    }
    int ai=0;
    boolean test = false;
    String tagdict = null;
    boolean caseSensitive = true;
    int numTaggings = 1;
    while(ai < args.length && args[ai].startsWith("-")) {
      if (args[ai].equals("-d")) {
        tagdict = args[ai+1];
        ai+=2;
      }
      else if (args[ai].equals("-di")) {
        tagdict = args[ai+1];
        ai+=2;
        caseSensitive = false;
      }
      else if (args[ai].equals("-k")) {
        numTaggings = Integer.parseInt(args[ai+1]);
        ai+=2;
      }
    }
    POSTaggerME tagger;
    String model = args[ai++];
    String dictFile = null;
    if (ai < args.length) {
      dictFile = args[ai++];
    }
    
    if (tagdict != null) {
      if (dictFile != null) {
        tagger = new PosTagger(model,new Dictionary(
            new FileInputStream(dictFile)), 
            new POSDictionary(tagdict,caseSensitive));
      }
      else {
        tagger = new PosTagger(model,new POSDictionary(tagdict,caseSensitive));
      }
    }
    else {
      if (dictFile != null) {
        tagger = new PosTagger(model,
            new Dictionary(new FileInputStream(dictFile)));
      }
      else {
        tagger = new PosTagger(model,(Dictionary)null);
      }
    }
    if (test) {
      System.out.println(tagger.tag(args[ai]));
    }
    else {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      
      for (String line = in.readLine(); line != null; line = in.readLine()) {
        if (numTaggings == 1) {
          System.out.println(tagger.tag(line));
        }
        else {
          String[] tokens = line.split(" ");
          String[][] taggings = tagger.tag(numTaggings, tokens);
          for (int ti=0;ti<taggings.length;ti++) {
            for (int wi=0;wi<tokens.length;wi++) {
              if (wi != 0) {
                System.out.print(" ");
              }
              System.out.print(tokens[wi]+"/"+taggings[ti][wi]);
            }
            System.out.println();
          }
        }
      }
    }
  }
}