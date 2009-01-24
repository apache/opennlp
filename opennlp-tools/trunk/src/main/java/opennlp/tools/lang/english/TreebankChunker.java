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
import opennlp.tools.chunker.ChunkerContextGenerator;
import opennlp.tools.chunker.ChunkerME;

/** This is a chunker based on the CONLL chunking task which uses Penn Treebank constituents as the basis for the chunks.
 *   See   http://cnts.uia.ac.be/conll2000/chunking/ for data and task definition.
 * @author Tom Morton
 */
public class TreebankChunker extends ChunkerME {

  /**
   * Creates an English Treebank Chunker which uses the specified model file.
   * @param modelFile The name of the maxent model to be used.
   * @throws IOException When the model file can't be open or read.
   */
  public TreebankChunker(String modelFile) throws IOException {
    this(new SuffixSensitiveGISModelReader(new File(modelFile)).getModel());
  }

  /**
   * Creates an English Treebank Chunker which uses the specified model.
   * @param mod The maxent model to be used.
   */
  public TreebankChunker(MaxentModel mod) {
    super(mod);
  }

  /**
   * Creates an English Treebank Chunker which uses the specified model and context generator.
   * @param mod The maxent model to be used.
   * @param cg The context generator to be used.
   */
  public TreebankChunker(MaxentModel mod, ChunkerContextGenerator cg) {
    super(mod, cg);
  }

  /**
   * Creates an English Treebank Chunker which uses the specified model and context generator
   * which will be decoded using the specified beamSize.
   * @param mod The maxent model to be used.
   * @param cg The context generator to be used.
   * @param beamSize The size of the beam used for decoding.
   */
  public TreebankChunker(MaxentModel mod, ChunkerContextGenerator cg, int beamSize) {
    super(mod, cg, beamSize);
  }

  private boolean validOutcome(String outcome, String prevOutcome) {
    if (outcome.startsWith("I-")) {
      if (prevOutcome == null) {
        return (false);
      }
      else {
        if (prevOutcome.equals("O")) {
          return (false);
        }
        if (!prevOutcome.substring(2).equals(outcome.substring(2))) {
          return (false);
        }
      }
    }
    return (true);
  }

  protected boolean validOutcome(String outcome, String[] sequence) {
    String prevOutcome = null;
    if (sequence.length > 0) {
      prevOutcome = sequence[sequence.length-1];
    }
    return validOutcome(outcome,prevOutcome);
  }


  /* inherieted java doc
  protected boolean validOutcome(String outcome, Sequence sequence) {
    String prevOutcome = null;
    List tagList = sequence.getOutcomes();
    int lti = tagList.size() - 1;
    if (lti >= 0) {
      prevOutcome = (String) tagList.get(lti);
    }
    return validOutcome(outcome,prevOutcome);
  }
  */

  /**
   * Chunks tokenized input from stdin. <br>
   * Usage: java opennlp.tools.chunker.EnglishTreebankChunker model < tokenized_sentences <br>
   */
  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: java opennlp.tools.english.TreebankChunker model < tokenized_sentences");
      System.exit(1);
    }
    TreebankChunker chunker = new TreebankChunker(args[0]);
    java.io.BufferedReader inReader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
    for (String line = inReader.readLine(); line != null; line = inReader.readLine()) {
      if (line.equals("")) {
        System.out.println();
      }
      else {
        String[] tts = line.split(" ");
        String[] tokens = new String[tts.length];
        String[] tags = new String[tts.length];
        for (int ti=0,tn=tts.length;ti<tn;ti++) {
          int si = tts[ti].lastIndexOf("/");
          tokens[ti]=tts[ti].substring(0,si);
          tags[ti]=tts[ti].substring(si+1);
        }
        String[] chunks = chunker.chunk(tokens,tags);
        //System.err.println(java.util.Arrays.asList(chunks));
        for (int ci=0,cn=chunks.length;ci<cn;ci++) {
          if (ci > 0 && !chunks[ci].startsWith("I-") && !chunks[ci-1].equals("O")) {
            System.out.print(" ]");
          }
          if (chunks[ci].startsWith("B-")) {
            System.out.print(" ["+chunks[ci].substring(2));
          }
          /*
           else {
           System.out.print(" ");
           }
           */
          System.out.print(" "+tokens[ci]+"/"+tags[ci]);
        }
        if (!chunks[chunks.length-1].equals("O")) {
          System.out.print(" ]");
        }
        System.out.println();
      }
    }
  }
}
