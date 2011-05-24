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

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import opennlp.model.AbstractModel;
import opennlp.model.EventStream;
import opennlp.model.MaxentModel;
import opennlp.model.TrainUtil;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

/**
 * A Tokenizer for converting raw text into separated tokens.  It uses
 * Maximum Entropy to make its decisions.  The features are loosely
 * based off of Jeff Reynar's UPenn thesis "Topic Segmentation:
 * Algorithms and Applications.", which is available from his
 * homepage: <http://www.cis.upenn.edu/~jcreynar>.
 * <p>
 * This tokenizer needs a statistical model to tokenize a text which reproduces
 * the tokenization observed in the training data used to create the model.
 * The {@link TokenizerModel} class encapsulates the model and provides
 * methods to create it from the binary representation. 
 * <p>
 * A tokenizer instance is not thread safe. For each thread one tokenizer
 * must be instantiated which can share one <code>TokenizerModel</code> instance
 * to safe memory.
 * <p>
 * To train a new model {{@link #train(String, ObjectStream, boolean, TrainingParameters)} method
 * can be used.
 * <p>
 * Sample usage:
 * <p>
 * <code>
 * InputStream modelIn;<br>
 * <br>
 * ...<br>
 * <br>
 * TokenizerModel model = TokenizerModel(modelIn);<br>
 * <br>
 * Tokenizer tokenizer = new TokenizerME(model);<br>
 * <br>
 * String tokens[] = tokenizer.tokenize("A sentence to be tokenized.");
 * </code>
 * 
 * @see Tokenizer
 * @see TokenizerModel
 * @see TokenSample
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
   * List of probabilities for each token returned from a call to
   * <code>tokenize</code> or <code>tokenizePos</code>.
   */
  private List<Double> tokProbs;

  private List<Span> newTokens;

  public TokenizerME(TokenizerModel model) {
    this.model = model.getMaxentModel();
    useAlphaNumericOptimization = model.useAlphaNumericOptimization();

    newTokens = new ArrayList<Span>();
    tokProbs = new ArrayList<Double>(50);
  }

  /**
   * Returns the probabilities associated with the most recent
   * calls to {@link TokenizerME#tokenize(String)} or {@link TokenizerME#tokenizePos(String)}.
   *
   * @return probability for each token returned for the most recent
   * call to tokenize.  If not applicable an empty array is
   * returned.
   */
  public double[] getTokenProbabilities() {
    double[] tokProbArray = new double[tokProbs.size()];
    for (int i = 0; i < tokProbArray.length; i++) {
      tokProbArray[i] = tokProbs.get(i);
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
          tokenProb *= probs[model.getIndex(best)];
          if (best.equals(TokenizerME.SPLIT)) {
            newTokens.add(new Span(start, j));
            tokProbs.add(tokenProb);
            start = j;
            tokenProb = 1.0;
          }
        }
        newTokens.add(new Span(start, end));
        tokProbs.add(tokenProb);
      }
    }

    Span[] spans = new Span[newTokens.size()];
    newTokens.toArray(spans);
    return spans;
  }

  public static TokenizerModel train(String languageCode, ObjectStream<TokenSample> samples,
      boolean useAlphaNumericOptimization, TrainingParameters mlParams) throws IOException {

    Map<String, String> manifestInfoEntries = new HashMap<String, String>();
    
    EventStream eventStream = new TokSpanEventStream(samples,
        useAlphaNumericOptimization);

    AbstractModel maxentModel = TrainUtil.train(eventStream, mlParams.getSettings(), manifestInfoEntries);
    
    return new TokenizerModel(languageCode, maxentModel, 
        useAlphaNumericOptimization, manifestInfoEntries);
  }
  
  /**
   * Trains a model for the {@link TokenizerME}.
   *
   * @param languageCode the language of the natural text
   * @param samples the samples used for the training.
   * @param useAlphaNumericOptimization - if true alpha numerics are skipped
   * @param cutoff number of times a feature must be seen to be considered
   * @param iterations number of iterations to train the maxent model
   * 
   * @return the trained {@link TokenizerModel}
   *
   * @throws IOException it throws an {@link IOException} if an {@link IOException}
   * is thrown during IO operations on a temp file which is created during training.
   * Or if reading from the {@link ObjectStream} fails.
   * 
   */
  public static TokenizerModel train(String languageCode, ObjectStream<TokenSample> samples,
      boolean useAlphaNumericOptimization, int cutoff, int iterations) throws IOException {

    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(iterations));
    mlParams.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(cutoff));
    
    return train(languageCode, samples, useAlphaNumericOptimization, mlParams);
  }


  /**
   * Trains a model for the {@link TokenizerME} with a default cutoff of 5 and 100 iterations.
   *
   * @param languageCode the language of the natural text
   * @param samples the samples used for the training.
   * @param useAlphaNumericOptimization - if true alpha numerics are skipped
   *
   * @return the trained {@link TokenizerModel}
   *
   * @throws IOException it throws an {@link IOException} if an {@link IOException}
   * is thrown during IO operations on a temp file which is
   * 
   * @throws ObjectStreamException if reading from the {@link ObjectStream} fails
   * created during training.
   */
  public static TokenizerModel train(String languageCode, ObjectStream<TokenSample> samples,
      boolean useAlphaNumericOptimization) throws IOException, ObjectStreamException {
    return train(languageCode, samples, useAlphaNumericOptimization, 5, 100);
  }
  
  /**
   * Returns the value of the alpha-numeric optimization flag.
   *
   * @return true if the tokenizer should use alpha-numeric optimization, false otherwise.
   */
  public boolean useAlphaNumericOptimization() {
    return useAlphaNumericOptimization;
  }
}
