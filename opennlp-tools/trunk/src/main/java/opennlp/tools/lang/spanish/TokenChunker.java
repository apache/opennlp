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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.tools.namefind.NameFinderEventStream;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;

/**
 * Class which identifies multi-token chunk which are treated as a single token in for POS-tagging.
 */
public class TokenChunker {

  private NameFinderME nameFinder;

  public TokenChunker(String modelName) throws IOException {
  nameFinder = new NameFinderME(new SuffixSensitiveGISModelReader(
      new File(modelName)).getModel());
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: java opennlp.tools.spanish.TokenChunker model < tokenized_sentences");
      System.exit(1);
    }
    TokenChunker chunker = new TokenChunker(args[0]);
    java.io.BufferedReader inReader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in,"ISO-8859-1"));
    PrintStream out = new PrintStream(System.out,true,"ISO-8859-1");
    for (String line = inReader.readLine(); line != null; line = inReader.readLine()) {
      if (line.equals("")) {
        out.println();
      }
      else {
        String[] tokens = line.split(" ");
        Span[] spans = chunker.nameFinder.find(tokens);
        String[] outcomes = NameFinderEventStream.generateOutcomes(spans, null, tokens.length);
        //System.err.println(java.util.Arrays.asList(chunks));
        for (int ci=0,cn=outcomes.length;ci<cn;ci++) {
          if (ci == 0) {
            out.print(tokens[ci]);
          }
          else if (outcomes[ci].equals(NameFinderME.CONTINUE)) {
            out.print("_"+tokens[ci]);
          }
          else {
            out.print(" "+tokens[ci]);
          }
        }
        out.println();
      }
    }
  }
}
