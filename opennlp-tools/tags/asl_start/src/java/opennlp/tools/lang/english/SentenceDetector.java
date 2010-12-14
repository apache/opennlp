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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.tools.sentdetect.SentenceDetectorME;

/**
 * A sentence detector which uses a model trained on English data.
 *
 * @author      Jason Baldridge and Tom Morton
 * @version     $Revision: 1.7 $, $Date: 2008-09-28 18:12:20 $
 */

public class SentenceDetector extends SentenceDetectorME {
  /**
   * Loads a new sentence detector using the model specified by the model name.
   * @param modelName The name of the maxent model trained for sentence detection. 
   * @throws IOException If the model specified can not be read.
   */
  public SentenceDetector(String modelName) throws IOException {
    super((new SuffixSensitiveGISModelReader(new File(modelName))).getModel());
    this.useTokenEnd = true;
  }

  /**
   * Perform sentence detection the input stream.  A newline will be treated as a paragraph boundry.
   * <p>java opennlp.tools.sentdetect.EnglishSentenceDetectorME model < "First sentence. Second sentence? Here is another one. And so on and so forth - you get the idea."
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.print("Usage java opennlp.tools.lang.english.SentenceDetector model < text");
      System.exit(1);
    }
    SentenceDetectorME sdetector = new SentenceDetector(args[0]);
    StringBuffer para = new StringBuffer();
    BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
    for (String line = inReader.readLine(); line != null; line = inReader.readLine()) {
      if (line.equals("")) {
        if (para.length() != 0) {
          //System.err.println(para.toString());
          String[] sents = sdetector.sentDetect(para.toString());
          for (int si = 0, sn = sents.length; si < sn; si++) {
            System.out.println(sents[si]);
          }
        }
        System.out.println();
        para.setLength(0);
      }
      else {
        para.append(line).append(" ");
      }
    }
    if (para.length() != 0) {
      String[] sents = sdetector.sentDetect(para.toString());
      for (int si = 0, sn = sents.length; si < sn; si++) {
        System.out.println(sents[si]);
      }
    }
  }
}
