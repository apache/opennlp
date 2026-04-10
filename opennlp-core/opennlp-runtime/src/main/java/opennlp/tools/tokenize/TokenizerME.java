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

package opennlp.tools.tokenize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.ArrayMath;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.Probabilistic;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.models.ModelType;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.OwnerOrPerThreadState;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;
import opennlp.tools.util.TrainingParameters;

/**
 * A {@link Tokenizer} for converting raw text into separated tokens. It uses
 * Maximum Entropy to make its decisions. The features are loosely
 * based off of Jeff Reynar's UPenn thesis "Topic Segmentation:
 * Algorithms and Applications.", which is available from his
 * homepage: <a href="http://www.cis.upenn.edu/~jcreynar">http://www.cis.upenn.edu/~jcreynar</a>.
 * <p>
 * This implementation needs a statistical model to tokenize a text which reproduces
 * the tokenization observed in the training data used to create the model.
 * The {@link TokenizerModel} class encapsulates that model and provides
 * methods to create it from the binary representation.
 * <p>
 * A tokenizer instance is thread-safe. One tokenizer can be shared across multiple threads to save
 * memory.
 * <p>
 * <b>Note:</b> In container environments with classloader isolation (e.g. Jakarta EE), ensure instances do
 * not outlive the application's lifecycle, as underlying components use {@link ThreadLocal} state that may
 * pin the classloader.
 * <p>
 * To train a new model, use {@link #train(ObjectStream, TokenizerFactory, TrainingParameters)}.
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
 * @see Probabilistic
 */
@ThreadSafe
public class TokenizerME extends AbstractTokenizer implements Probabilistic {

  /**
   * Constant indicates a token split.
   */
  public static final String SPLIT = "T";

  /**
   * Constant indicates no token split.
   */
  public static final String NO_SPLIT = "F";

  private final Pattern alphanumeric;

  /*
   * The maximum entropy model to use to evaluate contexts.
   */
  private final MaxentModel model;

  /*
   * The context generator.
   */
  private final TokenContextGenerator cg;

  /*
   * Optimization flag to skip alphanumeric tokens for further tokenization
   */
  private final boolean useAlphaNumericOptimization;

  private final OwnerOrPerThreadState<TokenizerState> perThreadState =
      new OwnerOrPerThreadState<>(TokenizerState::new, s -> {
        s.newTokens = new ArrayList<>();
        s.tokProbs = new ArrayList<>();
      });

  private static final class TokenizerState {
    private List<Double> tokProbs = new ArrayList<>();
    private List<Span> newTokens = new ArrayList<>();
  }

  /*
   * The {@link Dictionary abbreviation dictionary} if available (may be {@code null}).
   */
  private final Dictionary abbDict;

  /**
   * Initializes a {@link TokenizerME} by downloading a default model.
   * @param language The language of the tokenizer.
   * @throws IOException Thrown if the model cannot be downloaded or saved.
   */
  public TokenizerME(String language) throws IOException {
    this(DownloadUtil.downloadModel(language, ModelType.TOKENIZER,
            TokenizerModel.class));
  }

  /**
   * Instantiates a {@link TokenizerME} with an existing {@link TokenizerModel}.
   *
   * @param model The {@link TokenizerModel} to be used.
   */
  public TokenizerME(TokenizerModel model) {
    this(model, model.getAbbreviations());
  }

  /**
   * Instantiates a {@link TokenizerME} with an existing {@link TokenizerModel}.
   *
   * @param model The {@link TokenizerModel} to be used.
   * @param abbDict The {@link Dictionary} to be used. It must fit the language of the {@code model}.
   */
  public TokenizerME(TokenizerModel model, Dictionary abbDict) {
    this.model = model.getMaxentModel();
    this.abbDict = abbDict;
    TokenizerFactory factory = model.getFactory();
    this.cg = factory.getContextGenerator();
    this.alphanumeric = factory.getAlphaNumericPattern();
    this.useAlphaNumericOptimization = factory.isUseAlphaNumericOptimization();
  }

  /**
   * {@inheritDoc}
   *
   * The sequence was determined based on the previous call to {@link #tokenizePos(String)}.
   *
   * @return an array with the same number of probabilities as tokens were sent to the computational method
   *     when {@link #tokenizePos(String)} was last called; if not applicable, an empty array
   */
  @Override
  public double[] probs() {
    return ArrayMath.toDoubleArray(perThreadState.get().tokProbs);
  }

  /**
   * @return the probabilities associated with the most recent calls to {@link #tokenizePos(String)}; if not
   *     applicable, an empty array
   * @deprecated Use {@link #probs()} instead.
   */
  @Deprecated(forRemoval = true, since = "2.5.5")
  public double[] getTokenProbabilities() {
    return probs();
  }

  /**
   * Tokenizes the string.
   *
   * @param d  The string to be tokenized.
   *
   * @return   A {@link Span} array containing individual tokens as elements.
   */
  @Override
  public Span[] tokenizePos(String d) {
    WhitespaceTokenizer whitespaceTokenizer = WhitespaceTokenizer.INSTANCE;
    whitespaceTokenizer.setKeepNewLines(keepNewLines);
    Span[] tokens = whitespaceTokenizer.tokenizePos(d);
    List<Span> localTokens = new ArrayList<>();
    List<Double> localProbs = new ArrayList<>(50);
    for (Span s : tokens) {
      String tok = d.substring(s.getStart(), s.getEnd());
      // Can't tokenize single characters
      if (tok.length() < 2) {
        localTokens.add(s);
        localProbs.add(1d);
      } else if (useAlphaNumericOptimization() && alphanumeric.matcher(tok).matches()) {
        localTokens.add(s);
        localProbs.add(1d);
      } else {
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
            if (isAcceptableAbbreviation(tok)) {
              localTokens.add(new Span(start, end));
              localProbs.add(tokenProb);
              long numberOfDots = tok.codePoints().filter(ch -> ch == '.').count();
              j = j + (int) numberOfDots; // To compensate for abbreviation dot(s)
              start = j + 1;
            } else {
              localTokens.add(new Span(start, j));
              localProbs.add(tokenProb);
              start = j;
            }
            tokenProb = 1.0;
          }
        }
        if (start < end) {
          localTokens.add(new Span(start, end));
          localProbs.add(tokenProb);
        }
      }
    }

    // Publish per-thread state for backward-compatible probs() access
    TokenizerState state = perThreadState.get();
    state.newTokens = localTokens;
    state.tokProbs = localProbs;

    Span[] spans = new Span[localTokens.size()];
    localTokens.toArray(spans);
    return spans;
  }

  /**
   * Removes thread-local state to prevent classloader leaks in container environments.
   * Call when the thread is returned to a pool or the tokenizer is no longer needed.
   */
  public void clearThreadLocalState() {
    perThreadState.clearForCurrentThread();
  }

  /**
   * Trains a model for the {@link TokenizerME}.
   *
   * @param samples The samples used for the training.
   * @param factory A {@link TokenizerFactory} to get resources from.
   * @param mlParams The machine learning {@link TrainingParameters train parameters}.
   * @return A trained {@link TokenizerModel}.
   * @throws IOException Thrown during IO on a temp file created during training, or if reading from the
   *     {@link ObjectStream} fails.
   */
  public static TokenizerModel train(ObjectStream<TokenSample> samples, TokenizerFactory factory,
      TrainingParameters mlParams) throws IOException {

    Map<String, String> manifestInfoEntries = new HashMap<>();

    ObjectStream<Event> eventStream = new TokSpanEventStream(samples,
        factory.isUseAlphaNumericOptimization(),
        factory.getAlphaNumericPattern(), factory.getContextGenerator());

    EventTrainer<TrainingParameters> trainer = TrainerFactory.getEventTrainer(
        mlParams, manifestInfoEntries);

    MaxentModel maxentModel = trainer.train(eventStream);

    return new TokenizerModel(maxentModel, manifestInfoEntries, factory);
  }

  /**
   * @return {@code true} if the tokenizer uses alphanumeric optimization, {@code false} otherwise.
   */
  public boolean useAlphaNumericOptimization() {
    return useAlphaNumericOptimization;
  }

  /**
   * Allows checking a token abbreviation candidate for acceptability.
   *
   * <p>Note: The implementation always returns {@code false} if no
   * abbreviation dictionary is available for the underlying model.</p>
   *
   * @param s the {@link CharSequence token} to check for.
   * @return {@code true} if the candidate is acceptable, {@code false} otherwise.
   */
  protected boolean isAcceptableAbbreviation(CharSequence s) {
    if (abbDict == null)
      return false;

    return abbDict.contains(new StringList(s.toString()));
  }
}
