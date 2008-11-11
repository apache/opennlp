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


package opennlp.tools.sentdetect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.maxent.DataStream;
import opennlp.maxent.GIS;
import opennlp.maxent.GISModel;
import opennlp.maxent.IntegerPool;
import opennlp.maxent.PlainTextByLineDataStream;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.AbstractModel;
import opennlp.model.EventStream;
import opennlp.model.MaxentModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.sentdetect.lang.th.SentenceContextGenerator;

/**
 * A sentence detector for splitting up raw text into sentences.  A maximum
 * entropy model is used to evaluate the characters ".", "!", and "?" in a
 * string to determine if they signify the end of a sentence.
 *
 * @author      Jason Baldridge and Tom Morton
 */
public class SentenceDetectorME implements SentenceDetector {

  /**
   * Constant indicates a sentence split.
   */
  public static final String SPLIT ="T";
  
  /**
   * Constant indicates no sentence split.
   */
  public static final String NO_SPLIT ="F";
  
  /** 
   * The maximum entropy model to use to evaluate contexts. 
   */
  private MaxentModel model;

  /** 
   * The feature context generator. 
   */
  private final SDContextGenerator cgen;

  /** 
   * The {@link EndOfSentenceScanner} to use when scanning for end of sentence offsets. 
   */
  private final EndOfSentenceScanner scanner;

  /** 
   * A pool of read-only java.lang.Integer objects in the range 0..100 
   */
  private static final IntegerPool INT_POOL = new IntegerPool(100);
  
  /** 
   * The list of probabilities associated with each decision. 
   */
  private List<Double> sentProbs = new ArrayList<Double>();
  
  protected boolean useTokenEnd;

  /**
   * Initializes the current instance.
   * 
   * @param model the {@link SentenceModel}
   */
  public SentenceDetectorME(SentenceModel model) {
    this(model, new Factory());
  }

  public SentenceDetectorME(SentenceModel model, Factory factory) {
    this.model = model.getMaxentModel();
    cgen = factory.createSentenceContextGenerator(model.getLanguage());
    scanner = factory.createEndOfSentenceScanner(model.getLanguage());
    useTokenEnd = model.useTokenEnd();
  }
  
  /**
   * Detect sentences in a String.
   *
   * @param s  The string to be processed.
   * @return   A string array containing individual sentences as elements.
   *           
   */
  public String[] sentDetect(String s) {
    int[] starts = sentPosDetect(s);
    if (starts.length == 0) {
	return new String[] {s};
    }

    boolean leftover = starts[starts.length - 1] != s.length() && useTokenEnd;
    //System.err.println("sentDetect leftover="+leftover+" length="+s.length());
    String[] sents = new String[leftover? starts.length + 1 : starts.length];
    sents[0] = s.substring(0,starts[0]);
    //System.err.println("sentDetect:0 "+starts[0]);
    for (int si = 1; si < starts.length; si++) {
      sents[si] = s.substring(starts[si - 1], starts[si]);
      //System.err.println("sentDetect:"+si+" "+starts[si]);
    }

    if (leftover) {
        sents[sents.length - 1] = s.substring(starts[starts.length - 1]);
    }
    
    return sents;
  }

  private int getFirstWS(String s, int pos) {
    while (pos < s.length() && !Character.isWhitespace(s.charAt(pos)))
      pos++;
    return pos;
  }

  private int getFirstNonWS(String s, int pos) {
    while (pos < s.length() && Character.isWhitespace(s.charAt(pos)))
      pos++;
    return pos;
  }

  /**
   * Detect the position of the first words of sentences in a String.
   *
   * @param s  The string to be processed.
   * @return   A integer array containing the positions of the end index of
   *          every sentence
   *           
   */
  public int[] sentPosDetect(String s) {
    double sentProb = 1;
    sentProbs.clear();
    StringBuffer sb = new StringBuffer(s);
    List<Integer> enders = scanner.getPositions(s);
    List<Integer> positions = new ArrayList<Integer>(enders.size());

    for (int i = 0, end = enders.size(), index = 0; i < end; i++) {
      Integer candidate = (Integer) enders.get(i);
      int cint = candidate.intValue();
      // skip over the leading parts of non-token final delimiters
      int fws = getFirstWS(s,cint + 1);
      if (i + 1 < end && ((Integer) enders.get(i + 1)).intValue() < fws) {
        continue;
      }

      double[] probs = model.eval(cgen.getContext(sb.toString(), candidate.intValue()));
      String bestOutcome = model.getBestOutcome(probs);
      sentProb *= probs[model.getIndex(bestOutcome)];
      //System.err.println("sentPosDetect: cand="+cint+" index="+index+" "+bestOutcome+" "+probs[model.getIndex(bestOutcome)]+" "+s.substring(0,cint));
      if (bestOutcome.equals(SPLIT) && isAcceptableBreak(s, index, cint)) {
        if (index != cint) {
          if (useTokenEnd) {
            positions.add(INT_POOL.get(getFirstNonWS(s, getFirstWS(s,cint + 1))));
          }
          else {
            positions.add(INT_POOL.get(getFirstNonWS(s,cint)));
          }
          sentProbs.add(new Double(probs[model.getIndex(bestOutcome)]));
        }
        index = cint + 1;
      }
    }

    int[] sentPositions = new int[positions.size()];
    for (int i = 0; i < sentPositions.length; i++) {
      sentPositions[i] = ((Integer) positions.get(i)).intValue();
    }
    return sentPositions;
  }

  /** 
   * Returns the probabilities associated with the most recent
   * calls to sentDetect().
   * 
   * @return probability for each sentence returned for the most recent
   * call to sentDetect.  If not applicable an empty array is
   * returned.
   */
  public double[] getSentenceProbabilities() {
    double[] sentProbArray = new double[sentProbs.size()];
    for (int i = 0; i < sentProbArray.length; i++) {
      sentProbArray[i] = ((Double) sentProbs.get(i)).doubleValue();
    }
    return sentProbArray;
  }

  /** 
   * Allows subclasses to check an overzealous (read: poorly
   * trained) model from flagging obvious non-breaks as breaks based
   * on some boolean determination of a break's acceptability.
   *
   * <p>The implementation here always returns true, which means
   * that the MaxentModel's outcome is taken as is.</p>
   * 
   * @param s the string in which the break occurred. 
   * @param fromIndex the start of the segment currently being evaluated 
   * @param candidateIndex the index of the candidate sentence ending 
   * @return true if the break is acceptable 
   */
  protected boolean isAcceptableBreak(String s, int fromIndex, int candidateIndex) {
    return true;
  }

  public static SentenceModel train(String languageCode, Iterator<SentenceSample> samples, 
      boolean useTokenEnd, Dictionary abbreviations) {
    
    Factory factory = new Factory();
    
    EventStream eventStream = new SDEventStreamNew(samples, 
        factory.createSentenceContextGenerator(languageCode),
        factory.createEndOfSentenceScanner(languageCode));
    
    GISModel sentModel = GIS.trainModel(eventStream, 100, 5);
    
    return new SentenceModel(languageCode, sentModel,
        useTokenEnd, abbreviations);
  }
  
  private static void usage() {
    System.err.println("Usage: SentenceDetectorME [-encoding charset] [-lang language] trainData modelName");
    System.err.println("-encoding charset specifies the encoding which should be used ");
    System.err.println("                  for reading and writing text.");
    System.err.println("-lang language    specifies the language which ");
    System.err.println("                  is being processed.");
    System.exit(1);    
  }

  /**
   * <p>Trains a new sentence detection model.</p>
   *
   * <p>Usage: opennlp.tools.sentdetect.SentenceDetectorME data_file new_model_name (iterations cutoff)?</p>
   * 
   * @param args 
   * @throws IOException 
   */
  public static void main(String[] args) throws IOException {
    int ai=0;
    String encoding = null;
    String lang = null;
    if (args.length == 0) {
      usage();
    }
    while (args[ai].startsWith("-")) {
      if (args[ai].equals("-encoding")) {
        ai++;
        if (ai < args.length) {
          encoding = args[ai];
          ai++;
        }
        else {
          usage();
        }
      }
      else if (args[ai].equals("-lang")) {
        ai++;
        if (ai < args.length) {
          lang = args[ai];
          ai++;
        }
        else {
          usage();
        }
      }
      else {
        usage();
      }
    }
    
    File inFile = new File(args[ai++]);
    File outFile = new File(args[ai++]);
    AbstractModel mod;
    
    try {
      if (lang == null) {
        usage();
      }
      
      SentenceModel model = train("en", 
          new SentenceSampleStream(new PlainTextByLineDataStream(
          new InputStreamReader(new FileInputStream(inFile), encoding))), true, null);

      // TODO: add support for iterations and cutoff settings
      
//      if (args.length > ai)
//        mod = train(es, Integer.parseInt(args[ai++]), Integer.parseInt(args[ai++]));
//      else
//        mod = train(es, 100, 5);

      System.out.println("Saving the model as: " + outFile);
      model.serialize(new FileOutputStream(outFile));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}