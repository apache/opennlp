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

package opennlp.tools.namefind;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.ml.AlgorithmType;
import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.EventModelSequenceTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.Probabilistic;
import opennlp.tools.ml.SequenceTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.TrainerFactory.TrainerType;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.featuregen.AdditionalContextFeatureGenerator;
import opennlp.tools.util.featuregen.WindowFeatureGenerator;

/**
 * A maximum-entropy-based {@link TokenNameFinder name finder} implementation.
 *
 * @see Probabilistic
 * @see TokenNameFinder
 */
public class NameFinderME implements TokenNameFinder, Probabilistic {

  private static final String[][] EMPTY = new String[0][0];
  public static final int DEFAULT_BEAM_SIZE = 3;
  private static final Pattern typedOutcomePattern = Pattern.compile("(.+)-\\w+");

  public static final String START = "start";
  public static final String CONTINUE = "cont";
  public static final String OTHER = "other";

  private final SequenceCodec<String> seqCodec;

  protected final SequenceClassificationModel model;

  protected final NameContextGenerator contextGenerator;
  private Sequence bestSequence;

  private final AdditionalContextFeatureGenerator additionalContextFeatureGenerator =
          new AdditionalContextFeatureGenerator();
  private final SequenceValidator<String> sequenceValidator;

  /**
   * Initializes a {@link NameFinderME} with a {@link TokenNameFinderModel}.
   * 
   * @param model The {@link TokenNameFinderModel} to initialize with.
   */
  public NameFinderME(TokenNameFinderModel model) {

    TokenNameFinderFactory factory = model.getFactory();

    seqCodec = factory.createSequenceCodec();
    sequenceValidator = seqCodec.createSequenceValidator();
    this.model = model.getNameFinderSequenceModel();
    contextGenerator = factory.createContextGenerator();

    // TODO: We should deprecate this. And come up with a better solution!
    contextGenerator.addFeatureGenerator(
            new WindowFeatureGenerator(additionalContextFeatureGenerator, 8, 8));
  }

  @Override
  public Span[] find(String[] tokens) {
    return find(tokens, EMPTY);
  }

  /**
   * Generates name tags for the given sequence, typically a sentence, returning
   * {@link Span token spans} for any identified names.
   *
   * @param tokens An array of the tokens or words of a sequence, typically a sentence.
   * @param additionalContext Features which are based on context outside of the
   *                          sentence but which should also be used.
   *
   * @return An array of {@link Span token spans} for each of the names identified.
   */
  public Span[] find(String[] tokens, String[][] additionalContext) {

    additionalContextFeatureGenerator.setCurrentContext(additionalContext);
    bestSequence = model.bestSequence(tokens, additionalContext, contextGenerator, sequenceValidator);

    List<String> c = bestSequence.getOutcomes();

    contextGenerator.updateAdaptiveData(tokens, c.toArray(new String[0]));
    Span[] spans = seqCodec.decode(c);
    spans = setProbs(spans);
    return spans;
  }
  
  @Override
  public void clearAdaptiveData() {
    contextGenerator.clearAdaptiveData();
  }

  /**
   * Populates the specified array with the probabilities of the last decoded
   * sequence. The sequence was determined based on the previous call to
   * {@link #find(String[])}. The specified array should be at least as large as the
   * number of tokens in the previous call to {@link #find(String[])}.
   *
   * @param probs An array with the probabilities of the last decoded sequence.
   */
  public void probs(double[] probs) {
    bestSequence.getProbs(probs);
  }

  /**
   * {@inheritDoc}
   * 
   * The sequence was determined based on the previous call to {@link #find(String[])}.
   *
   * @return An array with the same number of probabilities as tokens were sent
   *         to {@link #find(String[])} when it was last called.
   */
  @Override
  public double[] probs() {
    return bestSequence.getProbs();
  }

  /**
   * Sets probabilities for the spans.
   *
   * @param spans The {@link Span spans} to set probabilities.
   *              
   * @return The {@link Span spans} with populated values.
   */
  private Span[] setProbs(Span[] spans) {
    double[] probs = probs(spans);
    if (probs != null) {

      for (int i = 0; i < probs.length; i++) {
        double prob = probs[i];
        spans[i] = new Span(spans[i], prob);
      }
    }
    return spans;
  }

  /**
   * Retrieves an array of probabilities for each of the specified spans which is
   * the arithmetic mean of the probabilities for each of the outcomes which
   * make up the span.
   *
   * @param spans The {@link Span spans} of the names for which probabilities
   *              are requested.
   *
   * @return An array of probabilities for each of the specified spans.
   */
  public double[] probs(Span[] spans) {

    double[] sprobs = new double[spans.length];
    double[] probs = bestSequence.getProbs();

    for (int si = 0; si < spans.length; si++) {

      double p = 0;

      for (int oi = spans[si].getStart(); oi < spans[si].getEnd(); oi++) {
        p += probs[oi];
      }

      p /= spans[si].length();

      sprobs[si] = p;
    }

    return sprobs;
  }

  /**
   * Starts a training of a {@link TokenNameFinderModel} with the given parameters.
   *
   * @param languageCode The ISO conform language code.
   * @param type The type to use.
   * @param samples The {@link ObjectStream} of {@link NameSample} used as input for training.
   * @param params The {@link TrainingParameters} for the context of the training.
   * @param factory The {@link TokenNameFinderFactory} for creating related objects defined
   *                via {@code params}.
   *
   * @return A valid, trained {@link TokenNameFinderModel} instance.
   * @throws IOException Thrown if IO errors occurred during training.
   */
  public static TokenNameFinderModel train(String languageCode, String type,
                                           ObjectStream<NameSample> samples, TrainingParameters params,
                                           TokenNameFinderFactory factory) throws IOException {

    //FIXME OPENNLP-1742
    params.putIfAbsent(Parameters.ALGORITHM_PARAM, AlgorithmType.PERCEPTRON.getAlgorithmType());
    params.putIfAbsent(Parameters.CUTOFF_PARAM, 0);
    params.putIfAbsent(Parameters.ITERATIONS_PARAM, 300);

    int beamSize = params.getIntParameter(BeamSearch.BEAM_SIZE_PARAMETER, NameFinderME.DEFAULT_BEAM_SIZE);

    Map<String, String> manifestInfoEntries = new HashMap<>();

    MaxentModel nameFinderModel = null;
    SequenceClassificationModel seqModel = null;

    TrainerType trainerType = TrainerFactory.getTrainerType(params);

    if (TrainerType.EVENT_MODEL_TRAINER.equals(trainerType)) {
      ObjectStream<Event> eventStream = new NameFinderEventStream(samples, type,
              factory.createContextGenerator(), factory.createSequenceCodec());

      EventTrainer<TrainingParameters> trainer =
          TrainerFactory.getEventTrainer(params, manifestInfoEntries);
      nameFinderModel = trainer.train(eventStream);
    } // TODO: Maybe it is not a good idea, that these two don't use the context generator ?!
    // These also don't use the sequence codec ?!
    else if (TrainerType.EVENT_MODEL_SEQUENCE_TRAINER.equals(trainerType)) {
      NameSampleSequenceStream ss = new NameSampleSequenceStream(samples, factory.createContextGenerator());

      EventModelSequenceTrainer<NameSample, TrainingParameters> trainer =
          TrainerFactory.getEventModelSequenceTrainer(params, manifestInfoEntries);
      nameFinderModel = trainer.train(ss);
    } else if (TrainerType.SEQUENCE_TRAINER.equals(trainerType)) {
      SequenceTrainer<TrainingParameters> trainer =
              TrainerFactory.getSequenceModelTrainer(params, manifestInfoEntries);

      NameSampleSequenceStream ss =
          new NameSampleSequenceStream(samples, factory.createContextGenerator(), false);
      seqModel = trainer.train(ss);
    } else {
      throw new IllegalStateException("Unexpected trainer type!");
    }

    if (seqModel != null) {
      return new TokenNameFinderModel(languageCode, seqModel, factory.getFeatureGenerator(),
              factory.getResources(), manifestInfoEntries, factory.getSequenceCodec(), factory);
    } else {
      return new TokenNameFinderModel(languageCode, nameFinderModel, beamSize, factory.getFeatureGenerator(),
              factory.getResources(), manifestInfoEntries, factory.getSequenceCodec(), factory);
    }
  }

  /**
   * Extracts the name type from the {@code outcome}.
   *
   * @param outcome The outcome
   * @return The name type, or {@code null} if not set.
   */
  static String extractNameType(String outcome) {
    Matcher matcher = typedOutcomePattern.matcher(outcome);
    if (matcher.matches()) {
      return matcher.group(1);
    }

    return null;
  }

  /**
   * Removes {@link Span spans} with are intersecting or crossing in any way.
   *
   * <p>
   * The following rules are used to remove the spans:<br>
   * Identical spans: The first span in the array after sorting it remains.<br>
   * Intersecting spans: The first span after sorting remains.<br>
   * Contained spans: All spans which are contained by another are removed.<br>
   *
   * @param spans The input {@link Span spans}.
   *
   * @return The resulting non-overlapping {@link Span spans}.
   */
  public static Span[] dropOverlappingSpans(Span[] spans) {

    List<Span> sortedSpans = new ArrayList<>(spans.length);
    Collections.addAll(sortedSpans, spans);
    Collections.sort(sortedSpans);

    Iterator<Span> it = sortedSpans.iterator();

    Span lastSpan = null;

    while (it.hasNext()) {
      Span span = it.next();

      if (lastSpan != null) {
        if (lastSpan.intersects(span)) {
          it.remove();
          span = lastSpan;
        }
      }

      lastSpan = span;
    }

    return sortedSpans.toArray(new Span[0]);
  }
}
