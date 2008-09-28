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


package opennlp.tools.lang.spanish;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.model.MaxentModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.postag.DefaultPOSContextGenerator;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.TagDictionary;

/**
 * A part of speech tagger that uses a model trained on Spanish data.
 */

public class PosTagger extends POSTaggerME {

  public PosTagger(String modelFile, Dictionary dict, TagDictionary tagdict) {
      super(getModel(modelFile), new DefaultPOSContextGenerator(dict),tagdict);
  }

  public PosTagger(String modelFile, TagDictionary tagdict) {
    super(getModel(modelFile), new DefaultPOSContextGenerator(null),tagdict);
  }
  
  public PosTagger(String modelFile) {
    super(getModel(modelFile), new DefaultPOSContextGenerator(null));
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

  /**
   * <p>Part-of-speech tag a string passed in on the command line. For
   * example: 
   *
   * <p>java opennlp.tools.lang.spanish.PosTagger -test "Sr. Smith da el auto a sus hermano en Lunes."
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: PosTaggerME [-td tagdict] model < tokenized_sentences");
      System.err.println("       PosTaggerME -test model \"sentence\"");
      System.exit(1);
    }
    int ai=0;
    boolean test = false;
    String tagdict = null;
    while(ai < args.length && args[ai].startsWith("-")) {
      if (args[ai].equals("-test")) {
        ai++;
        test=true;
      }
      else if (args[ai].equals("-td")) {
        tagdict = args[ai+1];
        ai+=2;
      }
    }
    POSTaggerME tagger;
    String modelFile = args[ai++];
    if (tagdict != null) {
      tagger = new PosTagger(modelFile, new POSDictionary(tagdict));
    }
    else {
      tagger = new PosTagger(modelFile);
    }
    if (test) {
      System.out.println(tagger.tag(args[ai]));
    }
    else {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in,"ISO-8859-1"));
      PrintStream out = new PrintStream(System.out,true,"ISO-8859-1");
      for (String line = in.readLine(); line != null; line = in.readLine()) {
        out.println(tagger.tag(line));
      }
    }
  }
}