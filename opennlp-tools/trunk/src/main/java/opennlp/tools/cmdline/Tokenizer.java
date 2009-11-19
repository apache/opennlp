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

package opennlp.tools.cmdline;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InvalidFormatException;

/**
 * The command line utility for the maxent tokenizer.
 *
 * @see TokenizerME
 */
public class Tokenizer {

  public static void main(String[] args) throws IOException, InvalidFormatException {

    if (args.length != 1) {
      System.err.println("Usage: java opennlp.tools.cmdline.Tokenizer model < sentences");
      System.exit(1);
    }

    TokenizerModel model = new TokenizerModel(new FileInputStream(args[0]));

    opennlp.tools.tokenize.Tokenizer tokenizer = new TokenizerME(model);

    // TODO: Specify encoding, based on model language
    // <- print encoding to err
    System.err.println("Encoding: ");

    BufferedReader inReader =  new BufferedReader(
        new InputStreamReader(System.in));

    for (String line = inReader.readLine(); line != null; line = inReader.readLine()) {
      if (line.equals("")) {
        System.out.println();
      }
      else {
        String[] tokens = tokenizer.tokenize(line);

        if (tokens.length > 0) {
          System.out.print(tokens[0]);
        }

        for (int ti = 1, tn = tokens.length; ti < tn; ti++) {
          System.out.print(" " + tokens[ti]);
        }

        System.out.println();
      }
    }
  }
}
