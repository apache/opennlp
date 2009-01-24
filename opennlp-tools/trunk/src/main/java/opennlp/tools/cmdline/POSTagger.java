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
import java.io.IOException;
import java.io.InputStreamReader;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.InvalidFormatException;

/**
 * The command line utility for the maxent pos tagger.
 *
 * @see POSTaggerME
 */
public class POSTagger {

  /**
   * <p>Part-of-speech tag a string passed in on the command line. For
   * example:
   *
   * <p>java opennlp.tools.lang.POSTagger -test "Mr. Smith gave a car to his son on Friday."
   *
   * @throws IOException
   * @throws InvalidFormatException
   */
  public static void main(String[] args) throws InvalidFormatException, IOException {

    if (args.length == 0) {
      System.err.println("Usage: java opennlp.tools.cmdline.POSTagger [-k 5] model < tokenized_sentences");
      System.err.println("-k n Specifies that the top n tagging should be reported.");
      System.exit(1);
    }

    int ai = 0;

    int numTaggings = 1;

    if (args.length == 2) {
      numTaggings = Integer.parseInt(args[ai]);
      ai++;
    }

    POSModel model = new POSModel(new FileInputStream(args[ai]));

    POSTaggerME tagger = new POSTaggerME(model);

    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

    for (String line = in.readLine(); line != null; line = in.readLine()) {
      if (numTaggings == 1) {
        System.out.println(tagger.tag(line));
      }
      else {
        String[] tokens = line.split(" ");
        String[][] taggings = tagger.tag(numTaggings, tokens);
        for (int ti = 0; ti < taggings.length; ti++) {
          for (int wi = 0; wi < tokens.length; wi++) {
            if (wi != 0) {
              System.out.print(" ");
            }
            System.out.print(tokens[wi] + "/" + taggings[ti][wi]);
          }
          System.out.println();
        }
      }
    }
  }
}
