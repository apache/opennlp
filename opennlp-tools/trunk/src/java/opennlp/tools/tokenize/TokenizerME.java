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


package opennlp.tools.tokenize;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import opennlp.maxent.GIS;
import opennlp.maxent.GISModel;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.AbstractModel;
import opennlp.model.EventStream;
import opennlp.model.MaxentModel;
import opennlp.model.TwoPassDataIndexer;
import opennlp.tools.util.Span;

/**
 * A Tokenizer for converting raw text into separated tokens.  It uses
 * Maximum Entropy to make its decisions.  The features are loosely
 * based off of Jeff Reynar's UPenn thesis "Topic Segmentation:
 * Algorithms and Applications.", which is available from his
 * homepage: <http://www.cis.upenn.edu/~jcreynar>.
 *
 * @author      Tom Morton
 * @version $Revision: 1.25 $, $Date: 2008-09-28 18:12:19 $
 */
public class TokenizerME extends AbstractTokenizer {

  /**
   * Constant indicates a token split.
   */
  public static final String SPLIT ="T";
  
  /**
   * Constant indicates no token split.
   */
  public static final String NO_SPLIT ="F";
  
  /**
   * Alpha-Numeric Pattern
   */
  public static final Pattern alphaNumeric = Pattern.compile("^[A-Za-z0-9]+$");
  
  /**
   * The maximum entropy model to use to evaluate contexts.
   */
  private MaxentModel model;

  /**
   * The context generator.
   */
  private final TokenContextGenerator cg = new DefaultTokenContextGenerator();

  /** 
   * Optimization flag to skip alpha numeric tokens for further
   * tokenization 
   */
  private boolean useAlphaNumericOptimization;

  /** 
   * List of probabilities for each token returned from call to
   * tokenize() 
   */
  private List<Double> tokProbs;
  
  private List<Span> newTokens;

  /**
   * Class constructor which takes the string locations of the
   * information which the maxent model needs.
   * 
   * @param mod 
   */
  @Deprecated
  public TokenizerME(MaxentModel mod) {
    setAlphaNumericOptimization(false);
    model = mod;
    newTokens = new ArrayList<Span>();
    tokProbs = new ArrayList<Double>(50);
  }

  public TokenizerME(TokenizerModel model) {
    this(model.getMaxentModel());
    useAlphaNumericOptimization = model.useAlphaNumericOptimization();
  }
  
  /** 
   * Returns the probabilities associated with the most recent
   * calls to tokenize() or tokenizePos().
   * 
   * @return probability for each token returned for the most recent
   * call to tokenize.  If not applicable an empty array is
   * returned.
   */
  public double[] getTokenProbabilities() {
    double[] tokProbArray = new double[tokProbs.size()];
    for (int i = 0; i < tokProbArray.length; i++) {
      tokProbArray[i] = ((Double) tokProbs.get(i)).doubleValue();
    }
    return tokProbArray;
  }

  /**
   * Tokenizes the string.
   *
   * @param d  The string to be tokenized.
   * 
   * @return   A span array containing individual tokens as elements.
   */
  public Span[] tokenizePos(String d) {
    Span[] tokens = WhitespaceTokenizer.INSTANCE.tokenizePos(d);
    newTokens.clear();
    tokProbs.clear();
    for (int i = 0, il = tokens.length; i < il; i++) {
      Span s = tokens[i];
      String tok = d.substring(s.getStart(), s.getEnd());
      // Can't tokenize single characters
      if (tok.length() < 2) {
        newTokens.add(s);
        tokProbs.add(1d);
      }
      else if (useAlphaNumericOptimization() && alphaNumeric.matcher(tok).matches()) {
        newTokens.add(s);
        tokProbs.add(1d);
      }
      else {
        int start = s.getStart();
        int end = s.getEnd();
        final int origStart = s.getStart();
        double tokenProb = 1.0;
        for (int j = origStart + 1; j < end; j++) {
          double[] probs =
            model.eval(cg.getContext(tok, j - origStart));
          String best = model.getBestOutcome(probs);
          //System.err.println("TokenizerME: "+tok.substring(0,j-origStart)+"^"+tok.substring(j-origStart)+" "+best+" "+probs[model.getIndex(best)]);
          tokenProb *= probs[model.getIndex(best)];
          if (best.equals(TokenizerME.SPLIT)) {
            newTokens.add(new Span(start, j));
            tokProbs.add(new Double(tokenProb));
            start = j;
            tokenProb = 1.0;
          }
        }
        newTokens.add(new Span(start, end));
        tokProbs.add(new Double(tokenProb));
      }
    }

    Span[] spans = new Span[newTokens.size()];
    newTokens.toArray(spans);
    return spans;
  }

  /**
   * Trains a model for the {@link TokenizerME}.
   * 
   * @param samples the samples used for the training.
   * @param useAlphaNumericOptimization - if true alpha numerics are skipped
   * 
   * @return the trained {@link TokenizerModel}
   * 
   * @throws IOException its throws if an {@link IOException} is thrown
   * during IO operations on a temp file which is created during training occur.
   */
  public static TokenizerModel train(Iterator<TokenSample> samples, 
      boolean useAlphaNumericOptimization) throws IOException {
    
    EventStream eventStream = new TokSpanEventStream(samples, 
        useAlphaNumericOptimization);
    
    GISModel maxentModel = 
        GIS.trainModel(100, new TwoPassDataIndexer(eventStream, 5));
    
    return new TokenizerModel(maxentModel, useAlphaNumericOptimization);
  }
  
  /**
   * Trains the {@link TokenizerME}, use this to create a new model.
   * 
   * @param evc
   * 
   * @return the new model
   */
  @Deprecated
  public static AbstractModel train(EventStream evc) throws IOException {
    return opennlp.maxent.GIS.trainModel(100, new TwoPassDataIndexer(evc, 5));
  }
  
  /**
   * Trains the {@link TokenizerME}, use this to create a new model.
   * 
   * @param evc
   * @param output
   * 
   * @throws IOException
   */
  @Deprecated
  public static void train(EventStream evc, File output, String encoding) throws IOException {
    new SuffixSensitiveGISModelWriter(TokenizerME.train(evc), output).persist();
  }

  /**
   * Used to have the tokenizer ignore tokens which only contain alpha-numeric characters.
   * 
   * @param opt set to true to use the optimization, false otherwise.
   */
  @Deprecated
  public void setAlphaNumericOptimization(boolean opt) {
    useAlphaNumericOptimization = opt;
  }

/**
 * Returns the value of the alpha-numeric optimization flag.
 * 
 * @return true if the tokenizer should use alpha-numeric optization, false otherwise.
 */
  public boolean useAlphaNumericOptimization() {
    return useAlphaNumericOptimization;
  }
}