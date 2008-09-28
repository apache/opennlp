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


package opennlp.tools.lang.english;

import java.io.File;
import java.io.IOException;

import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.model.MaxentModel;
import opennlp.tools.tokenize.TokenizerME;

/**
 * A tokenizer which uses default English data for the maxent model.
 *
 * @author      Jason Baldridge 
 * @author      Tom Morton
 */
public class Tokenizer extends TokenizerME  {
  public Tokenizer(String name) throws IOException  {
    super((new SuffixSensitiveGISModelReader(new File(name))).getModel());
    setAlphaNumericOptimization(true);
  }
  
  public Tokenizer(MaxentModel model) throws IOException {
    super(model);
    setAlphaNumericOptimization(true);
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage:  java opennlp.tools.english.Tokenizer model < sentences");
      System.exit(1);
    }
    Tokenizer tokenizer = new Tokenizer(args[0]);
    java.io.BufferedReader inReader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
    for (String line = inReader.readLine(); line != null; line = inReader.readLine()) {
      if (line.equals("")) {
        System.out.println();
      }
      else {
        String[] tokens = tokenizer.tokenize(line);
        if (tokens.length > 0) {
          System.out.print(tokens[0]);
        }
        for (int ti=1,tn=tokens.length;ti<tn;ti++) {
          System.out.print(" "+tokens[ti]);
        }
        System.out.println();
      }
    }
  }
}
