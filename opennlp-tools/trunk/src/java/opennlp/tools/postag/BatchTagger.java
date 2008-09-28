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


package opennlp.tools.postag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import opennlp.maxent.DataStream;
import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.model.AbstractModel;
import opennlp.tools.dictionary.Dictionary;

/**
 * Invoke a part-of-speech tagging model from the command line.
 *
 * @author   Jason Baldridge
 * @version $Revision: 1.8 $, $Date: 2008-09-28 18:12:22 $
 */
public class BatchTagger {

  private static void usage() {
    System.err.println("Usage: BatchTagger [-dict dict_file] data_file model");
    System.err.println("This applies a model to the specified text file.");
    System.exit(1);
  }

  /**
   * <p>
   * Applies a pos model.
   * </p>
   * 
   * @param args
   * @throws IOException
   * 
   */
  public static void main (String[] args) throws IOException {
    if (args.length == 0) {
      usage();
    }
    int ai=0;
    try {
      //String encoding = null;

      String dictFile = "";
      String tagDictFile = "";
      //int cutoff = 0;
      while (args[ai].startsWith("-")) {
	if (args[ai].equals("-dict")) {
          ai++;
          if (ai < args.length) {
            dictFile = args[ai++];
          }
          else {
            usage();
          }
        }
	else if (args[ai].equals("-tag_dict")) {
          ai++;
          if (ai < args.length) {
            tagDictFile = args[ai++];
          }
          else {
            usage();
          }
        }
        else {
          System.err.println("Unknown option "+args[ai]);
          usage();
        }
      }

      Dictionary dict = new Dictionary(new FileInputStream(dictFile));

      File textFile = new File(args[ai++]);
      File modelFile = new File(args[ai++]);

      AbstractModel mod = new SuffixSensitiveGISModelReader(modelFile).getModel();

      POSTagger tagger;
      if (tagDictFile.equals("")) {
	  tagger = new POSTaggerME(mod, dict);
      }
      else {
	  tagger = 
	      new POSTaggerME(mod, dict, new POSDictionary(tagDictFile)); 
      }

      DataStream text = 
	  new opennlp.maxent.PlainTextByLineDataStream(
	       new java.io.FileReader(textFile));
      while(text.hasNext()) {
          String str = (String)text.nextToken();
	  System.out.println(tagger.tag(str));
      }

    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
