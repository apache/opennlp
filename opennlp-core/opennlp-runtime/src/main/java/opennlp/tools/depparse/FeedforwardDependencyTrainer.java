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

package opennlp.tools.depparse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParamChecks;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.ResourceLimits;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.BaseModel;

/**
 * Trains the {@link FeedforwardDependencyModel}: oracle-derived transition examples,
 * minibatch AdaGrad over a softmax cross-entropy loss, cube activation, and inverted
 * dropout on the hidden layer, the training recipe of
 * <a href="https://aclanthology.org/D14-1082/">Chen and Manning (2014)</a>.
 *
 * <p>The hyperparameters are read from {@link TrainingParameters}: the keys of this
 * class plus {@link Parameters#ITERATIONS_PARAM} for the number of epochs,
 * {@link Parameters#CUTOFF_PARAM} for the minimum word frequency,
 * {@link QNTrainer#L2COST_PARAM} for the L2 penalty on the dense weights (default
 * {@value #DEFAULT_L2}), and {@link BeamSearch#BEAM_SIZE_PARAMETER} for the refinement
 * beam. Other keys are ignored.</p>
 *
 * <p>Words below the frequency cutoff share the reserved unknown row, which is
 * therefore trained only when such words exist; the unknown rows of the tag and label
 * vocabularies keep their random initialization. Absent template positions share a
 * trained padding row. Non-projective samples have no arc-standard derivation and are
 * skipped. Training is deterministic for a fixed {@link #SEED_PARAM}.</p>
 *
 * @since 3.0.0
 */
public final class FeedforwardDependencyTrainer {

  /** The training parameter key of the embedding dimensionality; default {@value #DEFAULT_EMBEDDING_SIZE}. */
  public static final String EMBEDDING_SIZE_PARAM = "EmbeddingSize";

  /** The training parameter key of the hidden layer width; default {@value #DEFAULT_HIDDEN_SIZE}. */
  public static final String HIDDEN_SIZE_PARAM = "HiddenSize";

  /** The training parameter key of the minibatch size; default {@value #DEFAULT_BATCH_SIZE}. */
  public static final String BATCH_SIZE_PARAM = "BatchSize";

  /** The training parameter key of the AdaGrad step size; default {@value #DEFAULT_LEARNING_RATE}. */
  public static final String LEARNING_RATE_PARAM = "LearningRate";

  /** The training parameter key of the hidden dropout probability; default {@value #DEFAULT_DROPOUT}. */
  public static final String DROPOUT_PARAM = "Dropout";

  /**
   * The training parameter key of the random seed, read as an {@code int}; default
   * {@value #DEFAULT_SEED}.
   */
  public static final String SEED_PARAM = "Seed";

  /** The default embedding dimensionality. */
  public static final int DEFAULT_EMBEDDING_SIZE = 50;

  /** The default hidden layer width. */
  public static final int DEFAULT_HIDDEN_SIZE = 200;

  /** The default number of epochs. */
  public static final int DEFAULT_EPOCHS = 10;

  /** The default minibatch size. */
  public static final int DEFAULT_BATCH_SIZE = 256;

  /** The default AdaGrad step size. */
  public static final double DEFAULT_LEARNING_RATE = 0.02;

  /** The default L2 penalty. */
  public static final double DEFAULT_L2 = 1e-8;

  /** The default hidden dropout probability. */
  public static final double DEFAULT_DROPOUT = 0.5;

  /** The default minimum word frequency for an embedding row. */
  public static final int DEFAULT_WORD_CUTOFF = 2;

  /** The default random seed. */
  public static final int DEFAULT_SEED = 17;

  /** The default refinement beam size. */
  public static final int DEFAULT_BEAM_SIZE = 4;

  /** The maximum embedding width. */
  static final int MAX_EMBEDDING_SIZE = 4_096;

  /** The maximum hidden-layer width. */
  static final int MAX_HIDDEN_SIZE = 65_536;

  private static final Logger logger =
      LoggerFactory.getLogger(FeedforwardDependencyTrainer.class);

  /** The AdaGrad denominator offset that keeps the first steps finite. */
  private static final double ADAGRAD_EPSILON = 1e-6;

  /** The derivative factor of the cube activation, {@code d/dx x^3 = 3x^2}. */
  private static final double CUBE_DERIVATIVE_FACTOR = 3.0;

  /** The numerator of the Glorot uniform bound, {@code sqrt(6 / (fanIn + fanOut))}. */
  private static final double GLOROT_NUMERATOR = 6.0;

  /** The half-width of the uniform range embeddings are drawn from. */
  private static final double EMBEDDING_INIT_SCALE = 0.01;

  /** The smallest probability the reported training loss takes the log of. */
  private static final double LOSS_PROBABILITY_FLOOR = 1e-12;

  /** SHIFT and at least one RIGHT_ARC are required to parse a sentence. */
  private static final int MIN_TRANSITIONS = 2;

  /** The smallest beam that can lose the gold derivation to another path. */
  private static final int MIN_REFINE_BEAM_SIZE = 2;

  /** Prevents construction of this utility class. */
  private FeedforwardDependencyTrainer() {
  }

  /**
   * The parsed hyperparameters of one training or refinement run.
   *
   * @param embeddingSize The embedding dimensionality. Must be positive.
   * @param hiddenSize The hidden layer width. Must be positive.
   * @param epochs The number of passes over the examples. Must be positive.
   * @param batchSize The minibatch size. Must be positive.
   * @param learningRate The AdaGrad step size. Must be positive.
   * @param l2 The L2 penalty applied to the dense weights. Must not be negative.
   * @param dropout The hidden dropout probability. Must be in {@code [0, 1)}.
   * @param wordCutoff The minimum frequency for a word to get its own embedding. Must
   *                   not be negative.
   * @param seed The random seed making a run reproducible.
   * @param beamSize The refinement beam width; {@link #refine} requires at least 2.
   */
  record Settings(int embeddingSize, int hiddenSize, int epochs, int batchSize,
      double learningRate, double l2, double dropout, int wordCutoff, long seed,
      int beamSize) {

    /**
     * Validates the hyperparameters.
     *
     * @throws IllegalArgumentException Thrown if a value is outside its documented
     *         range or the smallest network of this shape exceeds
     *         {@link ResourceLimits#MAX_MATRIX_CELLS} float values.
     */
    Settings {
      ParamChecks.requirePositive(embeddingSize, EMBEDDING_SIZE_PARAM);
      ParamChecks.requirePositive(hiddenSize, HIDDEN_SIZE_PARAM);
      ParamChecks.requirePositive(epochs, Parameters.ITERATIONS_PARAM);
      ParamChecks.requirePositive(batchSize, BATCH_SIZE_PARAM);
      if (embeddingSize > MAX_EMBEDDING_SIZE) {
        throw new IllegalArgumentException(EMBEDDING_SIZE_PARAM + " exceeds " + MAX_EMBEDDING_SIZE
            + ": " + embeddingSize);
      }
      if (hiddenSize > MAX_HIDDEN_SIZE) {
        throw new IllegalArgumentException(HIDDEN_SIZE_PARAM + " exceeds " + MAX_HIDDEN_SIZE
            + ": " + hiddenSize);
      }
      requireWithinCellLimit(FeedforwardNetwork.RESERVED_ROWS, MIN_TRANSITIONS,
          embeddingSize, hiddenSize);
      ParamChecks.requirePositive(ParamChecks.requireFinite(learningRate, LEARNING_RATE_PARAM),
          LEARNING_RATE_PARAM);
      ParamChecks.requireNonNegative(ParamChecks.requireFinite(l2, QNTrainer.L2COST_PARAM),
          QNTrainer.L2COST_PARAM);
      if (!(dropout >= 0.0 && dropout < 1.0)) {
        throw new IllegalArgumentException(DROPOUT_PARAM + " must be in [0, 1): " + dropout);
      }
      ParamChecks.requireNonNegative(wordCutoff, Parameters.CUTOFF_PARAM);
    }

    /**
     * Reads the hyperparameters, substituting the defaults for absent keys.
     *
     * @param parameters The training parameters. Must not be {@code null}.
     * @return The parsed settings. Never {@code null}.
     * @throws IllegalArgumentException Thrown if a value is outside its documented
     *         range.
     */
    static Settings from(TrainingParameters parameters) {
      return new Settings(
          parameters.getIntParameter(EMBEDDING_SIZE_PARAM, DEFAULT_EMBEDDING_SIZE),
          parameters.getIntParameter(HIDDEN_SIZE_PARAM, DEFAULT_HIDDEN_SIZE),
          parameters.getIntParameter(Parameters.ITERATIONS_PARAM, DEFAULT_EPOCHS),
          parameters.getIntParameter(BATCH_SIZE_PARAM, DEFAULT_BATCH_SIZE),
          parameters.getDoubleParameter(LEARNING_RATE_PARAM, DEFAULT_LEARNING_RATE),
          parameters.getDoubleParameter(QNTrainer.L2COST_PARAM, DEFAULT_L2),
          parameters.getDoubleParameter(DROPOUT_PARAM, DEFAULT_DROPOUT),
          parameters.getIntParameter(Parameters.CUTOFF_PARAM, DEFAULT_WORD_CUTOFF),
          parameters.getIntParameter(SEED_PARAM, DEFAULT_SEED),
          parameters.getIntParameter(BeamSearch.BEAM_SIZE_PARAMETER, DEFAULT_BEAM_SIZE));
    }
  }

  /**
   * Trains a model from dependency samples.
   *
   * @param languageCode The ISO language code of the training data. Must not be
   *                     {@code null}.
   * @param samples The training samples. Must not be {@code null}.
   * @param parameters The {@link TrainingParameters}. Must not be {@code null}.
   * @return A trained {@link FeedforwardDependencyModel}. Never {@code null}.
   * @throws IOException Thrown if reading the samples fails.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}, a
   *         hyperparameter is out of range, no trainable example can be derived from
   *         the samples, a vocabulary or the three vocabularies together exceed
   *         {@link ResourceLimits#MAX_ENTRIES} entries or a symbol as many UTF-8 bytes,
   *         or the network would store more than {@link ResourceLimits#MAX_MATRIX_CELLS}
   *         values.
   * @throws IllegalStateException Thrown if an epoch produces a non-finite parameter.
   */
  public static FeedforwardDependencyModel train(String languageCode,
      ObjectStream<DependencySample> samples, TrainingParameters parameters)
      throws IOException {
    ParamChecks.requireNonNullArg(languageCode, "languageCode");
    ParamChecks.requireNonNullArg(samples, "samples");
    ParamChecks.requireNonNullArg(parameters, "parameters");
    final Settings settings = Settings.from(parameters);
    final List<Derivation> derivations = derive(samples);
    if (derivations.isEmpty()) {
      throw new IllegalArgumentException("no trainable examples in the samples");
    }
    final FeedforwardNetwork network = initialize(derivations, settings);
    int exampleCount = 0;
    for (final Derivation derivation : derivations) {
      exampleCount += derivation.oracle().size();
    }
    final int[][] features = new int[exampleCount][];
    final int[] gold = new int[exampleCount];
    collectExamples(derivations, network, features, gold);
    optimize(network, features, gold, settings);
    return new FeedforwardDependencyModel(languageCode, network, manifestEntries(settings));
  }

  /**
   * Refines a locally trained model with beam search and the early-update method of
   * <a href="https://aclanthology.org/P04-1015/">Collins and Roark (2004)</a>. The loss
   * is conditional likelihood over candidate paths scored by summed log-probabilities.
   *
   * <p>Refinement trains a copy with per-sentence AdaGrad steps and no dropout.
   * {@link Parameters#ITERATIONS_PARAM} sets the number of passes and
   * {@link BeamSearch#BEAM_SIZE_PARAMETER} the beam width, which the manifest of the
   * result names as the beam size to parse it with. The vocabularies and the
   * transition inventory of {@code model} are not extended, so the refined model
   * carries the word cutoff of {@code model}.</p>
   *
   * @param model The locally trained model. Not modified. Must not be {@code null}.
   * @param samples The training samples. Must not be {@code null}.
   * @param parameters The {@link TrainingParameters}; the epochs, learning rate, L2
   *                   penalty, seed, and beam size apply. Must not be {@code null}.
   * @return A new refined model, distinct from {@code model}. Never {@code null}.
   * @throws IOException Thrown if reading the samples fails.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}, a
   *         hyperparameter is out of range, the beam size is below 2, no trainable
   *         sample can be derived, or a sample requires a transition {@code model}
   *         does not know.
   * @throws IllegalStateException Thrown if an epoch produces a non-finite parameter, or
   *         the network produces a non-finite transition score while a sentence is
   *         decoded.
   */
  public static FeedforwardDependencyModel refine(FeedforwardDependencyModel model,
      ObjectStream<DependencySample> samples, TrainingParameters parameters)
      throws IOException {
    ParamChecks.requireNonNullArg(model, "model");
    ParamChecks.requireNonNullArg(samples, "samples");
    ParamChecks.requireNonNullArg(parameters, "parameters");
    final Settings settings = Settings.from(parameters);
    if (settings.beamSize() < MIN_REFINE_BEAM_SIZE) {
      throw new IllegalArgumentException(BeamSearch.BEAM_SIZE_PARAMETER
          + " must be at least " + MIN_REFINE_BEAM_SIZE + ": " + settings.beamSize());
    }
    final FeedforwardNetwork refined = model.network().copy();
    final List<Derivation> derivations = derive(samples);
    if (derivations.isEmpty()) {
      throw new IllegalArgumentException("no trainable samples for refinement");
    }
    final int[][] oracles = new int[derivations.size()][];
    for (int d = 0; d < oracles.length; d++) {
      final List<Transition> oracle = derivations.get(d).oracle();
      oracles[d] = new int[oracle.size()];
      for (int i = 0; i < oracles[d].length; i++) {
        final int id = refined.transitionIndex(oracle.get(i));
        if (id < 0) {
          throw new IllegalArgumentException(
              "unknown transition in the refinement samples: " + oracle.get(i).encode());
        }
        oracles[d][i] = id;
      }
    }

    final Optimizer optimizer = new Optimizer(refined, settings);
    final Transition[] transitions = refined.decodedTransitions();
    final Random random = new Random(settings.seed());
    final int[] order = identity(derivations.size());
    for (int epoch = 1; epoch <= settings.epochs(); epoch++) {
      final long epochStart = System.currentTimeMillis();
      shuffle(order, random);
      double loss = 0.0;
      for (final int index : order) {
        loss += optimizer.refineSentence(derivations.get(index).sample(), oracles[index],
            transitions, settings.beamSize());
      }
      checkFinite(refined);
      logger.info("refine epoch {}: loss {} over {} sentences in {} ms", epoch,
          loss / order.length, order.length, System.currentTimeMillis() - epochStart);
    }
    final Map<String, String> manifestEntries = new HashMap<>();
    manifestEntries.put(BaseModel.TRAINING_ITERATIONS_PROPERTY,
        Integer.toString(settings.epochs()));
    final String cutoff = model.getManifestProperty(BaseModel.TRAINING_CUTOFF_PROPERTY);
    if (cutoff != null) {
      manifestEntries.put(BaseModel.TRAINING_CUTOFF_PROPERTY, cutoff);
    }
    manifestEntries.put(BeamSearch.BEAM_SIZE_PARAMETER, Integer.toString(settings.beamSize()));
    return new FeedforwardDependencyModel(model.getLanguage(), refined, manifestEntries);
  }

  /**
   * @param settings The hyperparameters of the run.
   * @return The manifest entries recording the run. Never {@code null}.
   */
  private static Map<String, String> manifestEntries(Settings settings) {
    final Map<String, String> entries = new HashMap<>();
    entries.put(BaseModel.TRAINING_ITERATIONS_PROPERTY, Integer.toString(settings.epochs()));
    entries.put(BaseModel.TRAINING_CUTOFF_PROPERTY, Integer.toString(settings.wordCutoff()));
    return entries;
  }

  /**
   * A projective training sample with its arc-standard oracle derivation.
   *
   * @param sample The sample.
   * @param oracle The gold transitions, in order.
   */
  private record Derivation(DependencySample sample, List<Transition> oracle) {
  }

  /**
   * Reads a sample stream into memory and derives the oracle of every projective
   * sample once; the trainer passes over the derivations repeatedly.
   *
   * @param samples The stream to drain.
   * @return The derivations in stream order, without the non-projective samples.
   *         Never {@code null}.
   * @throws IOException Thrown if reading the samples fails.
   */
  private static List<Derivation> derive(ObjectStream<DependencySample> samples)
      throws IOException {
    final List<Derivation> derivations = new ArrayList<>();
    int skipped = 0;
    DependencySample sample;
    while ((sample = samples.read()) != null) {
      if (ArcStandardOracle.isProjective(sample.getTree())) {
        derivations.add(new Derivation(sample, ArcStandardOracle.transitions(sample.getTree())));
      } else {
        skipped++;
      }
    }
    if (skipped > 0) {
      logger.warn("Skipped {} non-projective sample(s) without an arc-standard derivation.",
          skipped);
    }
    return derivations;
  }

  /**
   * Builds the vocabularies and randomly initialized weights. Symbols are assigned
   * rows in sorted order after the reserved symbols, so the network depends only on
   * the samples and the settings. A word, tag, or label in the training data that
   * spells a reserved symbol shares the reserved row, as an unknown symbol does.
   *
   * @param derivations The projective training samples with their oracles.
   * @param settings The hyperparameters.
   * @return The untrained network. Never {@code null}.
   * @throws IllegalArgumentException Thrown if a vocabulary, the three vocabularies
   *         together, or the transition inventory exceed
   *         {@link ResourceLimits#MAX_ENTRIES} entries, a symbol exceeds as many
   *         UTF-8 bytes, or the network would store more than
   *         {@link ResourceLimits#MAX_MATRIX_CELLS} values.
   */
  private static FeedforwardNetwork initialize(List<Derivation> derivations,
      Settings settings) {
    final Map<String, Integer> wordCounts = new HashMap<>();
    final Set<String> tagSet = new HashSet<>();
    final Set<String> labelSet = new HashSet<>();
    final Set<String> transitionSet = new HashSet<>();
    for (final Derivation derivation : derivations) {
      final DependencySample s = derivation.sample();
      for (final String token : s.getTokens()) {
        wordCounts.merge(FeedforwardNetwork.normalize(token), 1, Integer::sum);
      }
      for (final String tag : s.getTags()) {
        tagSet.add(tag);
      }
      final DependencyTree tree = s.getTree();
      for (int i = 0; i < tree.size(); i++) {
        labelSet.add(tree.relationOf(i));
      }
      for (final Transition transition : derivation.oracle()) {
        transitionSet.add(transition.encode());
      }
    }
    final List<String> words = new ArrayList<>();
    for (final Map.Entry<String, Integer> entry : wordCounts.entrySet()) {
      if (entry.getValue() >= settings.wordCutoff()) {
        words.add(entry.getKey());
      }
    }

    int row = 0;
    final Map<String, Integer> wordIds = new HashMap<>();
    row = assignRows(wordIds, row, FeedforwardNetwork.RESERVED_WORD_SYMBOLS, words,
        "word vocabulary");
    final Map<String, Integer> tagIds = new HashMap<>();
    row = assignRows(tagIds, row, FeedforwardNetwork.RESERVED_TAG_SYMBOLS,
        new ArrayList<>(tagSet), "tag vocabulary");
    final Map<String, Integer> labelIds = new HashMap<>();
    row = assignRows(labelIds, row, FeedforwardNetwork.RESERVED_LABEL_SYMBOLS,
        new ArrayList<>(labelSet), "label vocabulary");
    requireCombinedVocabularySize(wordIds.size(), tagIds.size(), labelIds.size());

    final String[] transitions = transitionSet.toArray(String[]::new);
    Arrays.sort(transitions);
    ResourceLimits.requireWithinMaxEntries(transitions.length, "transition count");
    for (final String transition : transitions) {
      FeedforwardNetwork.requireStorable(transition, "transition");
    }
    requireWithinCellLimit(row, transitions.length, settings.embeddingSize(),
        settings.hiddenSize());

    final Random random = new Random(settings.seed());
    final int inputSize = FeedforwardContext.FEATURE_COUNT * settings.embeddingSize();
    final float[][] embeddings = uniform(random, row, settings.embeddingSize(),
        EMBEDDING_INIT_SCALE);
    final float[][] hiddenWeights = uniform(random, settings.hiddenSize(), inputSize,
        Math.sqrt(GLOROT_NUMERATOR / (inputSize + settings.hiddenSize())));
    final float[][] outputWeights = uniform(random, transitions.length,
        settings.hiddenSize(),
        Math.sqrt(GLOROT_NUMERATOR / (settings.hiddenSize() + transitions.length)));
    return new FeedforwardNetwork(wordIds, tagIds, labelIds, transitions,
        settings.embeddingSize(), embeddings, hiddenWeights,
        new float[settings.hiddenSize()], outputWeights, new float[transitions.length]);
  }

  /**
   * Assigns the next embedding rows of one vocabulary: the reserved symbols first, then
   * the observed symbols in sorted order, skipping observed symbols that spell a
   * reserved one.
   *
   * @param ids The vocabulary to fill.
   * @param row The next free embedding row.
   * @param reserved The reserved symbols, in the order they take their rows.
   * @param observed The symbols seen in the training data; sorted in place.
   * @param label The vocabulary name used in error messages.
   * @return The next free embedding row after the vocabulary.
   * @throws IllegalArgumentException Thrown if the vocabulary exceeds
   *         {@link ResourceLimits#MAX_ENTRIES} entries or a symbol as many UTF-8 bytes.
   */
  private static int assignRows(Map<String, Integer> ids, int row, String[] reserved,
      List<String> observed, String label) {
    int next = row;
    for (final String symbol : reserved) {
      ids.put(symbol, next++);
    }
    observed.sort(null);
    for (final String symbol : observed) {
      if (!ids.containsKey(symbol)) {
        FeedforwardNetwork.requireStorable(symbol, label);
        ids.put(symbol, next++);
      }
    }
    ResourceLimits.requireWithinMaxEntries(ids.size(), label + " size");
    return next;
  }

  /**
   * Checks the combined vocabulary size the network loader allows, so a trained model
   * always loads.
   *
   * @param words The word vocabulary size.
   * @param tags The tag vocabulary size.
   * @param labels The label vocabulary size.
   * @throws IllegalArgumentException Thrown if the three sizes together exceed
   *         {@link ResourceLimits#MAX_ENTRIES}.
   */
  static void requireCombinedVocabularySize(int words, int tags, int labels) {
    ResourceLimits.requireWithinMaxEntries((long) words + tags + labels,
        "combined vocabulary size");
  }

  /**
   * @param vocabularyRows The combined word, tag, and label row count.
   * @param transitionCount The output layer width.
   * @param embeddingSize The embedding dimensionality.
   * @param hiddenSize The hidden layer width.
   * @throws IllegalArgumentException Thrown if a network of that shape stores more than
   *         {@link ResourceLimits#MAX_MATRIX_CELLS} float values.
   */
  private static void requireWithinCellLimit(int vocabularyRows, int transitionCount,
      int embeddingSize, int hiddenSize) {
    final long values = FeedforwardNetwork.floatValues(vocabularyRows, transitionCount,
        embeddingSize, hiddenSize);
    if (values > ResourceLimits.MAX_MATRIX_CELLS) {
      throw new IllegalArgumentException("the network would store " + values
          + " values, limit is " + ResourceLimits.MAX_MATRIX_CELLS);
    }
  }

  /**
   * Replays the oracle over every derivation, emitting one example per step.
   *
   * @param derivations The projective training samples with their oracles.
   * @param network The initialized network whose vocabularies map the features.
   * @param features Receives the embedding rows of each configuration.
   * @param gold Receives the gold transition index of each configuration.
   */
  private static void collectExamples(List<Derivation> derivations,
      FeedforwardNetwork network, int[][] features, int[] gold) {
    int example = 0;
    for (final Derivation derivation : derivations) {
      final DependencySample sample = derivation.sample();
      final FeedforwardContext context =
          new FeedforwardContext(network, sample.getTokens(), sample.getTags());
      final ArcStandardState state = new ArcStandardState(sample.getTree().size());
      for (final Transition transition : derivation.oracle()) {
        features[example] = context.featureIds(state);
        gold[example] = network.transitionIndex(transition);
        example++;
        state.apply(transition);
      }
    }
  }

  /**
   * Minibatch AdaGrad over softmax cross-entropy with cube activation and dropout,
   * updating {@code network} in place.
   *
   * @param network The initialized network.
   * @param features The embedding rows of every example.
   * @param gold The gold transition index of every example.
   * @param settings The hyperparameters.
   * @throws IllegalStateException Thrown if an epoch produces a non-finite parameter.
   */
  private static void optimize(FeedforwardNetwork network, int[][] features, int[] gold,
      Settings settings) {
    final int exampleCount = features.length;
    final Optimizer optimizer = new Optimizer(network, settings);
    final Random random = new Random(settings.seed());
    final int[] order = identity(exampleCount);
    final double keep = 1.0 - settings.dropout();
    final boolean[] mask = new boolean[settings.hiddenSize()];

    for (int epoch = 1; epoch <= settings.epochs(); epoch++) {
      final long epochStart = System.currentTimeMillis();
      shuffle(order, random);
      double loss = 0.0;
      for (int batchStart = 0; batchStart < exampleCount;
          batchStart += settings.batchSize()) {
        final int batchEnd = Math.min(batchStart + settings.batchSize(), exampleCount);
        optimizer.clearGradients();
        for (int b = batchStart; b < batchEnd; b++) {
          for (int j = 0; j < mask.length; j++) {
            mask[j] = random.nextDouble() < keep;
          }
          loss += optimizer.accumulate(features[order[b]], gold[order[b]], mask, keep);
        }
        optimizer.applyUpdates(batchEnd - batchStart);
      }
      checkFinite(network);
      logger.info("epoch {}: loss {} in {} ms", epoch, loss / exampleCount,
          System.currentTimeMillis() - epochStart);
    }
  }

  /**
   * The forward pass, backward pass, gradient buffers, and AdaGrad state shared by
   * local training and beam refinement. Dropout is an optional mask; without it the
   * pass is the inference computation of {@link FeedforwardNetwork#score}.
   */
  private static final class Optimizer {
    private final FeedforwardNetwork network;
    private final Settings settings;
    private final int embeddingSize;
    private final int hiddenSize;
    private final int outputSize;

    private final double[][] embeddingAccumulator;
    private final double[][] hiddenAccumulator;
    private final double[] hiddenBiasAccumulator;
    private final double[][] outputAccumulator;
    private final double[] outputBiasAccumulator;

    private final double[][] hiddenGradient;
    private final double[] hiddenBiasGradient;
    private final double[][] outputGradient;
    private final double[] outputBiasGradient;

    /** The embedding gradient rows, allocated on first touch and reused. */
    private final double[][] embeddingGradient;

    /** The rows with a nonzero embedding gradient since the last clear. */
    private final int[] touchedRows;
    private final boolean[] touched;
    private int touchedCount;

    private final double[] pre;
    private final double[] hidden;
    private final double[] scores;
    private final double[] outputDelta;
    private final double[] hiddenDelta;

    /** The embedding rows of the example being backpropagated, one per feature position. */
    private final float[][] featureEmbeddings = new float[FeedforwardContext.FEATURE_COUNT][];

    /** The gradient rows of those embeddings, one per feature position. */
    private final double[][] featureGradients = new double[FeedforwardContext.FEATURE_COUNT][];

    /** The nodes collected for one refinement update. */
    private final List<ArcStandardBeam.Node> collected = new ArrayList<>();

    /**
     * Sizes the accumulators and scratch buffers for one run.
     *
     * @param network The network the run updates in place.
     * @param settings The hyperparameters; {@code learningRate} and {@code l2} apply.
     */
    private Optimizer(FeedforwardNetwork network, Settings settings) {
      this.network = network;
      this.settings = settings;
      this.embeddingSize = network.embeddingSize();
      this.hiddenSize = network.hiddenSize();
      this.outputSize = network.outputBias().length;
      final int inputSize = FeedforwardContext.FEATURE_COUNT * embeddingSize;
      final int rows = network.embeddings().length;
      this.embeddingAccumulator = new double[rows][embeddingSize];
      this.hiddenAccumulator = new double[hiddenSize][inputSize];
      this.hiddenBiasAccumulator = new double[hiddenSize];
      this.outputAccumulator = new double[outputSize][hiddenSize];
      this.outputBiasAccumulator = new double[outputSize];
      this.hiddenGradient = new double[hiddenSize][inputSize];
      this.hiddenBiasGradient = new double[hiddenSize];
      this.outputGradient = new double[outputSize][hiddenSize];
      this.outputBiasGradient = new double[outputSize];
      this.embeddingGradient = new double[rows][];
      this.touchedRows = new int[rows];
      this.touched = new boolean[rows];
      this.pre = new double[hiddenSize];
      this.hidden = new double[hiddenSize];
      this.scores = new double[outputSize];
      this.outputDelta = new double[outputSize];
      this.hiddenDelta = new double[hiddenSize];
    }

    /**
     * Zeroes the gradient buffers before a batch.
     */
    private void clearGradients() {
      zero(hiddenGradient);
      Arrays.fill(hiddenBiasGradient, 0.0);
      zero(outputGradient);
      Arrays.fill(outputBiasGradient, 0.0);
      for (int i = 0; i < touchedCount; i++) {
        Arrays.fill(embeddingGradient[touchedRows[i]], 0.0);
        touched[touchedRows[i]] = false;
      }
      touchedCount = 0;
    }

    /**
     * Runs one example forward and adds its cross-entropy gradient to the buffers.
     *
     * @param features The embedding rows of the configuration.
     * @param gold The gold transition index.
     * @param mask The hidden units kept by dropout.
     * @param keep The probability a hidden unit is kept.
     * @return The example's negative log-likelihood.
     */
    private double accumulate(int[] features, int gold, boolean[] mask, double keep) {
      forward(features, mask, keep);
      softmaxInPlace(scores);
      for (int o = 0; o < outputSize; o++) {
        outputDelta[o] = scores[o] - (o == gold ? 1.0 : 0.0);
      }
      backward(features, mask, keep);
      return -Math.log(Math.max(scores[gold], LOSS_PROBABILITY_FLOOR));
    }

    /**
     * Computes hidden activations and raw output scores into the scratch buffers.
     *
     * @param features The embedding rows of the configuration.
     * @param mask The hidden units kept by dropout, or {@code null} to keep all.
     * @param keep The probability a hidden unit is kept; {@code 1} without dropout.
     */
    private void forward(int[] features, boolean[] mask, double keep) {
      network.preActivations(features, null, pre);
      for (int j = 0; j < hiddenSize; j++) {
        if (mask != null && !mask[j]) {
          pre[j] = 0.0;
          hidden[j] = 0.0;
        } else {
          hidden[j] = pre[j] * pre[j] * pre[j] / keep;
        }
      }
      network.outputScores(hidden, scores);
    }

    /**
     * Propagates {@link #outputDelta}, the loss gradient on the raw output scores,
     * back through the layers of the last {@link #forward} pass into the gradient
     * buffers.
     *
     * @param features The embedding rows of the configuration.
     * @param mask The hidden units kept by dropout, or {@code null} to keep all.
     * @param keep The probability a hidden unit is kept; {@code 1} without dropout.
     */
    private void backward(int[] features, boolean[] mask, double keep) {
      Arrays.fill(hiddenDelta, 0.0);
      final float[][] outputWeights = network.outputWeights();
      for (int o = 0; o < outputSize; o++) {
        final double delta = outputDelta[o];
        outputBiasGradient[o] += delta;
        final double[] gradientRow = outputGradient[o];
        final float[] weightRow = outputWeights[o];
        for (int j = 0; j < hiddenSize; j++) {
          gradientRow[j] += delta * hidden[j];
          hiddenDelta[j] += delta * weightRow[j];
        }
      }
      final float[][] embeddings = network.embeddings();
      final float[][] hiddenWeights = network.hiddenWeights();
      for (int f = 0; f < features.length; f++) {
        featureEmbeddings[f] = embeddings[features[f]];
        featureGradients[f] = touch(features[f]);
      }
      for (int j = 0; j < hiddenSize; j++) {
        if (mask != null && !mask[j]) {
          continue;
        }
        final double preDelta = hiddenDelta[j] * CUBE_DERIVATIVE_FACTOR * pre[j] * pre[j] / keep;
        hiddenBiasGradient[j] += preDelta;
        final double[] gradientRow = hiddenGradient[j];
        final float[] weightRow = hiddenWeights[j];
        for (int f = 0; f < features.length; f++) {
          final float[] embedding = featureEmbeddings[f];
          final double[] embeddingGradientRow = featureGradients[f];
          final int offset = f * embeddingSize;
          for (int d = 0; d < embeddingSize; d++) {
            gradientRow[offset + d] += preDelta * embedding[d];
            embeddingGradientRow[d] += preDelta * weightRow[offset + d];
          }
        }
      }
    }

    /**
     * Marks an embedding row as touched and returns its gradient row.
     *
     * @param row The embedding row.
     * @return The gradient row, zeroed on first touch. Never {@code null}.
     */
    private double[] touch(int row) {
      if (!touched[row]) {
        touched[row] = true;
        touchedRows[touchedCount++] = row;
        if (embeddingGradient[row] == null) {
          embeddingGradient[row] = new double[embeddingSize];
        }
      }
      return embeddingGradient[row];
    }

    /**
     * Applies one AdaGrad step from the gradient buffers to every parameter.
     *
     * @param batch The number of examples the gradients sum over.
     */
    private void applyUpdates(int batch) {
      update(network.hiddenWeights(), hiddenGradient, hiddenAccumulator, batch, settings);
      updateVector(network.hiddenBias(), hiddenBiasGradient, hiddenBiasAccumulator, batch,
          settings);
      update(network.outputWeights(), outputGradient, outputAccumulator, batch, settings);
      updateVector(network.outputBias(), outputBiasGradient, outputBiasAccumulator, batch,
          settings);
      final float[][] embeddings = network.embeddings();
      for (int i = 0; i < touchedCount; i++) {
        final int row = touchedRows[i];
        updateVector(embeddings[row], embeddingGradient[row], embeddingAccumulator[row],
            batch, settings);
      }
    }

    /**
     * Decodes one sentence with the beam, updating on the early-update point or the
     * final beam.
     *
     * @param sample The sentence.
     * @param oracle The gold transition indexes.
     * @param transitions The decoded transition inventory.
     * @param beamSize The beam width.
     * @return The sentence loss.
     * @throws IllegalStateException Thrown if the oracle and
     *         {@link ArcStandardState#canApply(Transition)} disagree, so the gold derivation
     *         cannot be followed.
     */
    private double refineSentence(DependencySample sample, int[] oracle,
        Transition[] transitions, int beamSize) {
      final FeedforwardContext context =
          new FeedforwardContext(network, sample.getTokens(), sample.getTags());
      List<ArcStandardBeam.Node> beam = List.of(
          new ArcStandardBeam.Node(new ArcStandardState(sample.getTokens().length)));
      for (int step = 0; step < oracle.length; step++) {
        final List<ArcStandardBeam.Node> expansions = ArcStandardBeam.expand(beam, context,
            network, null, transitions, oracle[step], hidden, scores);
        ArcStandardBeam.Node goldChild = null;
        for (final ArcStandardBeam.Node expansion : expansions) {
          if (expansion.gold) {
            goldChild = expansion;
            break;
          }
        }
        if (goldChild == null) {
          throw new IllegalStateException("the oracle and canApply disagree: gold transition "
              + transitions[oracle[step]].encode() + " is not applicable at step " + step);
        }
        final List<ArcStandardBeam.Node> survivors =
            ArcStandardBeam.advance(expansions, beamSize, transitions);
        if (!survivors.contains(goldChild)) {
          survivors.add(goldChild);
          return updateFromCandidates(survivors);
        }
        if (step == oracle.length - 1) {
          return updateFromCandidates(survivors);
        }
        beam = survivors;
      }
      throw new IllegalStateException("the gold derivation has no transitions");
    }

    /**
     * Applies the conditional-likelihood update over the candidate paths. Each node is
     * backpropagated once with the weight summed over the candidates passing through
     * it, so shared prefixes whose weights cancel cost nothing.
     *
     * @param candidates The surviving paths plus, on early update, the gold path.
     * @return The negative log-probability of the gold path under the candidates.
     */
    private double updateFromCandidates(List<ArcStandardBeam.Node> candidates) {
      final double[] pathScores = new double[candidates.size()];
      double goldScore = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < pathScores.length; i++) {
        pathScores[i] = candidates.get(i).score;
        if (candidates.get(i).gold) {
          goldScore = pathScores[i];
        }
      }
      FeedforwardNetwork.logSoftmaxInPlace(pathScores);

      clearGradients();
      collected.clear();
      for (int i = 0; i < pathScores.length; i++) {
        final ArcStandardBeam.Node candidate = candidates.get(i);
        final double weight = Math.exp(pathScores[i]) - (candidate.gold ? 1.0 : 0.0);
        for (ArcStandardBeam.Node node = candidate; node.parent != null; node = node.parent) {
          if (!node.collected) {
            node.collected = true;
            node.weight = 0.0;
            collected.add(node);
          }
          node.weight += weight;
        }
      }
      for (final ArcStandardBeam.Node node : collected) {
        node.collected = false;
        if (node.weight != 0.0) {
          backpropagate(node.features, node.transition, node.weight);
        }
      }
      applyUpdates(1);
      final double logNormalizer = candidates.get(0).score - pathScores[0];
      return logNormalizer - goldScore;
    }

    /**
     * Adds the gradient of one decoded step to the buffers: the weighted difference
     * between the step's softmax and its chosen transition.
     *
     * @param features The step's input features.
     * @param chosen The transition the path took at this step.
     * @param weight The step's summed weight in the candidate distribution.
     */
    private void backpropagate(int[] features, int chosen, double weight) {
      forward(features, null, 1.0);
      softmaxInPlace(scores);
      for (int o = 0; o < outputSize; o++) {
        outputDelta[o] = weight * ((o == chosen ? 1.0 : 0.0) - scores[o]);
      }
      backward(features, null, 1.0);
    }
  }

  /**
   * Turns finite raw scores into probabilities in place.
   *
   * @param scores The raw scores, overwritten with their softmax. Must not be empty.
   */
  private static void softmaxInPlace(double[] scores) {
    FeedforwardNetwork.logSoftmaxInPlace(scores);
    for (int i = 0; i < scores.length; i++) {
      scores[i] = Math.exp(scores[i]);
    }
  }

  /**
   * One AdaGrad step on a weight matrix, with the L2 penalty folded into the gradient.
   *
   * @param weights The weights, updated in place.
   * @param gradients The summed gradients of the batch.
   * @param accumulators The AdaGrad squared-gradient sums, updated in place.
   * @param batch The number of examples the gradients sum over.
   * @param settings The hyperparameters; {@code learningRate} and {@code l2} apply.
   */
  private static void update(float[][] weights, double[][] gradients,
      double[][] accumulators, int batch, Settings settings) {
    for (int r = 0; r < weights.length; r++) {
      final float[] weightRow = weights[r];
      final double[] gradientRow = gradients[r];
      final double[] accumulatorRow = accumulators[r];
      for (int c = 0; c < weightRow.length; c++) {
        final double gradient = gradientRow[c] / batch + settings.l2() * weightRow[c];
        accumulatorRow[c] += gradient * gradient;
        weightRow[c] -= settings.learningRate() * gradient
            / (Math.sqrt(accumulatorRow[c]) + ADAGRAD_EPSILON);
      }
    }
  }

  /**
   * One AdaGrad step on a vector without an L2 penalty: a bias or an embedding row.
   *
   * @param weights The values, updated in place.
   * @param gradients The summed gradients of the batch.
   * @param accumulators The AdaGrad squared-gradient sums, updated in place.
   * @param batch The number of examples the gradients sum over.
   * @param settings The hyperparameters; {@code learningRate} applies.
   */
  private static void updateVector(float[] weights, double[] gradients,
      double[] accumulators, int batch, Settings settings) {
    for (int i = 0; i < weights.length; i++) {
      final double gradient = gradients[i] / batch;
      accumulators[i] += gradient * gradient;
      weights[i] -= settings.learningRate() * gradient
          / (Math.sqrt(accumulators[i]) + ADAGRAD_EPSILON);
    }
  }

  /**
   * Rejects numerical overflow before returning or continuing to train a network.
   *
   * @param network The network to check.
   * @throws IllegalStateException Thrown if any parameter is NaN or infinite.
   */
  private static void checkFinite(FeedforwardNetwork network) {
    checkFinite(network.embeddings());
    checkFinite(network.hiddenWeights());
    checkFinite(network.hiddenBias());
    checkFinite(network.outputWeights());
    checkFinite(network.outputBias());
  }

  /**
   * @param matrix The matrix to check.
   * @throws IllegalStateException Thrown if any value is NaN or infinite.
   */
  private static void checkFinite(float[][] matrix) {
    for (final float[] row : matrix) {
      checkFinite(row);
    }
  }

  /**
   * @param vector The vector to check.
   * @throws IllegalStateException Thrown if any value is NaN or infinite.
   */
  private static void checkFinite(float[] vector) {
    for (final float value : vector) {
      if (!Float.isFinite(value)) {
        throw new IllegalStateException("training produced a non-finite model parameter");
      }
    }
  }

  /**
   * @param random The source of randomness.
   * @param rows The row count.
   * @param columns The column count.
   * @param scale The half-width of the uniform range.
   * @return A matrix drawn uniformly from {@code [-scale, scale]}. Never {@code null}.
   */
  private static float[][] uniform(Random random, int rows, int columns, double scale) {
    final float[][] matrix = new float[rows][columns];
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < columns; c++) {
        matrix[r][c] = (float) ((random.nextDouble() * 2.0 - 1.0) * scale);
      }
    }
    return matrix;
  }

  /**
   * Fills a matrix with zeros.
   *
   * @param matrix The matrix to clear in place.
   */
  private static void zero(double[][] matrix) {
    for (final double[] row : matrix) {
      Arrays.fill(row, 0.0);
    }
  }

  /**
   * @param size The permutation length.
   * @return The identity permutation. Never {@code null}.
   */
  private static int[] identity(int size) {
    final int[] order = new int[size];
    for (int i = 0; i < size; i++) {
      order[i] = i;
    }
    return order;
  }

  /**
   * Fisher-Yates shuffle of the visit order.
   *
   * @param order The permutation to shuffle in place.
   * @param random The source of randomness.
   */
  private static void shuffle(int[] order, Random random) {
    for (int i = order.length - 1; i > 0; i--) {
      final int j = random.nextInt(i + 1);
      final int swap = order[i];
      order[i] = order[j];
      order[j] = swap;
    }
  }
}
