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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.ObjectStream;

/**
 * Trains the {@link FeedforwardDependencyModel} entirely in Java: oracle-derived
 * transition examples, minibatch AdaGrad over a softmax cross-entropy loss, cube
 * activation, and inverted dropout on the hidden layer. No external training framework
 * is involved, so the whole neural tier, training and inference, is plain array
 * arithmetic inside the JVM.
 *
 * <p>Words below the frequency cutoff share a learned unknown embedding; absent
 * template positions share a learned padding embedding. Non-projective samples have no
 * arc-standard derivation and are skipped. Training is deterministic for a fixed
 * {@link Settings#seed()}.</p>
 *
 * @since 3.0.0
 */
public final class FeedforwardDependencyTrainer {

  private static final Logger logger =
      LoggerFactory.getLogger(FeedforwardDependencyTrainer.class);

  private static final double ADAGRAD_EPSILON = 1e-6;

  private FeedforwardDependencyTrainer() {
    // static trainer only
  }

  /**
   * The training hyperparameters.
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
   */
  public record Settings(int embeddingSize, int hiddenSize, int epochs, int batchSize,
      double learningRate, double l2, double dropout, int wordCutoff, long seed) {

    /**
     * Validates the hyperparameters.
     *
     * @throws IllegalArgumentException Thrown if a value is outside its documented
     *         range.
     */
    public Settings {
      if (embeddingSize <= 0 || hiddenSize <= 0 || epochs <= 0 || batchSize <= 0) {
        throw new IllegalArgumentException("sizes, epochs and batch must be positive");
      }
      if (learningRate <= 0.0 || l2 < 0.0) {
        throw new IllegalArgumentException("learningRate must be positive, l2 not negative");
      }
      if (!(dropout >= 0.0 && dropout < 1.0)) {
        throw new IllegalArgumentException("dropout must be in [0, 1): " + dropout);
      }
      if (wordCutoff < 0) {
        throw new IllegalArgumentException("wordCutoff must not be negative");
      }
    }

    /**
     * @return The default hyperparameters. Never {@code null}.
     */
    public static Settings defaults() {
      return new Settings(50, 200, 10, 256, 0.02, 1e-8, 0.5, 2, 17L);
    }
  }

  /**
   * Trains a model from dependency samples.
   *
   * @param samples The training samples. Must not be {@code null}.
   * @param settings The hyperparameters. Must not be {@code null}.
   * @return A trained {@link FeedforwardDependencyModel}. Never {@code null}.
   * @throws IOException Thrown if reading the samples fails.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null} or no
   *         trainable example can be derived from the samples.
   */
  public static FeedforwardDependencyModel train(ObjectStream<DependencySample> samples,
      Settings settings) throws IOException {
    return train(samples, settings, null);
  }

  /**
   * Trains a model from dependency samples, seeding word embeddings from a pretrained
   * source.
   *
   * <p>The provider is consulted once per vocabulary word during initialization; words
   * it returns {@code null} for keep their random initialization, and all embeddings
   * remain trainable afterwards. The pretrained source is a training-time ingredient
   * only: the learned embeddings ship inside the model, so parsing carries no
   * dependency on the source.</p>
   *
   * @param samples The training samples. Must not be {@code null}.
   * @param settings The hyperparameters. Must not be {@code null}.
   * @param pretrained Maps a normalized word to its pretrained vector of exactly
   *                   {@link Settings#embeddingSize()} dimensions, or {@code null} for
   *                   unknown words. May be {@code null} to disable seeding.
   * @return A trained {@link FeedforwardDependencyModel}. Never {@code null}.
   * @throws IOException Thrown if reading the samples fails.
   * @throws IllegalArgumentException Thrown if {@code samples} or {@code settings} is
   *         {@code null}, no trainable example can be derived, or a pretrained vector
   *         has the wrong dimensionality.
   */
  public static FeedforwardDependencyModel train(ObjectStream<DependencySample> samples,
      Settings settings, java.util.function.Function<String, float[]> pretrained)
      throws IOException {
    if (samples == null || settings == null) {
      throw new IllegalArgumentException("samples and settings must not be null");
    }
    final List<DependencySample> corpus = new ArrayList<>();
    DependencySample sample;
    while ((sample = samples.read()) != null) {
      corpus.add(sample);
    }
    final FeedforwardDependencyModel model = initialize(corpus, settings);
    if (pretrained != null) {
      seed(model, pretrained, settings);
    }
    final List<int[]> featureList = new ArrayList<>();
    final List<Integer> goldList = new ArrayList<>();
    collectExamples(corpus, model, featureList, goldList);
    if (featureList.isEmpty()) {
      throw new IllegalArgumentException("no trainable examples in the samples");
    }
    optimize(model, featureList, goldList, settings);
    return model;
  }

  /**
   * Fine-tunes a locally trained model globally: sentences are decoded with a beam, the
   * gold derivation is tracked through it, and the moment the gold prefix falls out of
   * the beam an early update pushes the model toward keeping it. The loss is a
   * conditional likelihood over the beam's candidate paths, scored exactly like the
   * beamed parser scores them, summed log-probabilities, so training optimizes the
   * quantity decoding uses.
   *
   * <p>The model is updated in place with per-sentence AdaGrad steps and no dropout;
   * {@link Settings#epochs()} counts the refinement passes. Refinement is deterministic
   * for a fixed {@link Settings#seed()}. Parse afterwards with the same beam size.</p>
   *
   * @param model The locally trained model to refine. Must not be {@code null}.
   * @param samples The training samples. Must not be {@code null}.
   * @param settings The hyperparameters; {@code epochs}, {@code learningRate},
   *                 {@code l2}, and {@code seed} apply. Must not be {@code null}.
   * @param beamSize The beam width to track the gold derivation in. Must be at least 2.
   * @return The same model instance, refined. Never {@code null}.
   * @throws IOException Thrown if reading the samples fails.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null},
   *         {@code beamSize} is below 2, or no trainable sample can be derived.
   */
  public static FeedforwardDependencyModel refine(FeedforwardDependencyModel model,
      ObjectStream<DependencySample> samples, Settings settings, int beamSize)
      throws IOException {
    if (model == null || samples == null || settings == null) {
      throw new IllegalArgumentException("model, samples and settings must not be null");
    }
    if (beamSize < 2) {
      throw new IllegalArgumentException("beamSize must be at least 2: " + beamSize);
    }
    final List<DependencySample> corpus = new ArrayList<>();
    DependencySample sample;
    while ((sample = samples.read()) != null) {
      corpus.add(sample);
    }
    final Map<String, Integer> transitionIds = new HashMap<>();
    final Transition[] transitions = new Transition[model.transitions().length];
    for (int i = 0; i < transitions.length; i++) {
      transitionIds.put(model.transitions()[i], i);
      transitions[i] = Transition.decode(model.transitions()[i]);
    }

    final List<DependencySample> trainable = new ArrayList<>();
    final List<int[]> oracles = new ArrayList<>();
    for (final DependencySample s : corpus) {
      final List<Transition> oracle;
      try {
        oracle = ArcStandardOracle.transitions(s.getGraph());
      } catch (IllegalArgumentException e) {
        continue;
      }
      final int[] encoded = new int[oracle.size()];
      for (int i = 0; i < encoded.length; i++) {
        encoded[i] = transitionIds.get(oracle.get(i).encode());
      }
      trainable.add(s);
      oracles.add(encoded);
    }
    if (trainable.isEmpty()) {
      throw new IllegalArgumentException("no trainable samples for refinement");
    }

    final GlobalOptimizer optimizer = new GlobalOptimizer(model, settings);
    final Random random = new Random(settings.seed());
    final int[] order = new int[trainable.size()];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    for (int epoch = 1; epoch <= settings.epochs(); epoch++) {
      final long epochStart = System.currentTimeMillis();
      shuffle(order, random);
      double loss = 0.0;
      int updates = 0;
      for (final int index : order) {
        final double sentenceLoss = optimizer.refineSentence(trainable.get(index),
            oracles.get(index), transitions, beamSize);
        if (sentenceLoss >= 0.0) {
          loss += sentenceLoss;
          updates++;
        }
      }
      logger.info("refine epoch {}: loss {} over {} updates in {} ms", epoch,
          loss / Math.max(updates, 1), updates, System.currentTimeMillis() - epochStart);
    }
    return model;
  }

  /** One candidate path in the refinement beam: the parent link forms the history. */
  private static final class BeamNode {
    private final BeamNode parent;
    private final int[] features;
    private final int transition;
    private final double score;
    private final boolean gold;
    private ArcStandardState state;

    private BeamNode(BeamNode parent, int[] features, int transition, double score,
        boolean gold) {
      this.parent = parent;
      this.features = features;
      this.transition = transition;
      this.score = score;
      this.gold = gold;
    }
  }

  /** The forward, backward, and AdaGrad state for global refinement. */
  private static final class GlobalOptimizer {
    private final FeedforwardDependencyModel model;
    private final Settings settings;
    private final int embeddingSize;
    private final int hiddenSize;
    private final int outputSize;
    private final int inputSize;

    private final double[][] embeddingAccumulator;
    private final double[][] hiddenAccumulator;
    private final double[] hiddenBiasAccumulator;
    private final double[][] outputAccumulator;
    private final double[] outputBiasAccumulator;

    private final double[][] hiddenGradient;
    private final double[] hiddenBiasGradient;
    private final double[][] outputGradient;
    private final double[] outputBiasGradient;
    private final Map<Integer, double[]> embeddingGradients = new HashMap<>();

    private final double[] x;
    private final double[] pre;
    private final double[] hidden;
    private final double[] probabilities;
    private final double[] hiddenDelta;
    private final double[] inputDelta;

    private GlobalOptimizer(FeedforwardDependencyModel model, Settings settings) {
      this.model = model;
      this.settings = settings;
      this.embeddingSize = model.embeddings()[0].length;
      this.hiddenSize = model.hiddenBias().length;
      this.outputSize = model.outputBias().length;
      this.inputSize =
          (2 * FeedforwardContext.POSITIONS + FeedforwardContext.LABEL_POSITIONS)
              * embeddingSize;
      this.embeddingAccumulator =
          new double[model.embeddings().length][embeddingSize];
      this.hiddenAccumulator = new double[hiddenSize][inputSize];
      this.hiddenBiasAccumulator = new double[hiddenSize];
      this.outputAccumulator = new double[outputSize][hiddenSize];
      this.outputBiasAccumulator = new double[outputSize];
      this.hiddenGradient = new double[hiddenSize][inputSize];
      this.hiddenBiasGradient = new double[hiddenSize];
      this.outputGradient = new double[outputSize][hiddenSize];
      this.outputBiasGradient = new double[outputSize];
      this.x = new double[inputSize];
      this.pre = new double[hiddenSize];
      this.hidden = new double[hiddenSize];
      this.probabilities = new double[outputSize];
      this.hiddenDelta = new double[hiddenSize];
      this.inputDelta = new double[inputSize];
    }

    /**
     * Decodes one sentence with the beam, updating on the early-update point or the
     * final beam.
     *
     * @param sample The sentence.
     * @param oracle The gold transition indexes.
     * @param transitions The decoded transition inventory.
     * @param beamSize The beam width.
     * @return The sentence loss, or {@code -1} when the sentence produced no update.
     */
    private double refineSentence(DependencySample sample, int[] oracle,
        Transition[] transitions, int beamSize) {
      final String[] tokens = sample.getTokens();
      final String[] tags = sample.getTags();
      final BeamNode root = new BeamNode(null, null, -1, 0.0, true);
      root.state = new ArcStandardState(tokens.length);
      List<BeamNode> beam = List.of(root);

      for (int step = 0; step < oracle.length; step++) {
        final List<BeamNode> expansions = new ArrayList<>();
        BeamNode goldChild = null;
        for (final BeamNode node : beam) {
          final int[] features =
              model.featureIds(FeedforwardContext.extract(node.state, tokens, tags));
          forward(features);
          logSoftmaxInPlace(probabilities);
          for (int i = 0; i < outputSize; i++) {
            if (node.state.canApply(transitions[i])) {
              final boolean goldNext = node.gold && i == oracle[step];
              final BeamNode child = new BeamNode(node, features, i,
                  node.score + probabilities[i], goldNext);
              expansions.add(child);
              if (goldNext) {
                goldChild = child;
              }
            }
          }
        }
        expansions.sort((a, b) -> Double.compare(b.score, a.score));
        final List<BeamNode> survivors =
            new ArrayList<>(expansions.subList(0, Math.min(beamSize, expansions.size())));
        boolean goldSurvives = false;
        for (final BeamNode survivor : survivors) {
          if (survivor.gold) {
            goldSurvives = true;
            break;
          }
        }
        if (!goldSurvives) {
          if (goldChild == null) {
            return -1.0; // the oracle transition was inapplicable; nothing to learn from
          }
          survivors.add(goldChild);
          return updateFromCandidates(survivors);
        }
        if (step == oracle.length - 1) {
          return updateFromCandidates(survivors);
        }
        for (final BeamNode survivor : survivors) {
          survivor.state = survivor.parent.state.copy();
          survivor.state.apply(transitions[survivor.transition]);
        }
        beam = survivors;
      }
      return -1.0;
    }

    /** Applies the conditional-likelihood update over the candidate paths. */
    private double updateFromCandidates(List<BeamNode> candidates) {
      double max = Double.NEGATIVE_INFINITY;
      double goldScore = Double.NEGATIVE_INFINITY;
      for (final BeamNode candidate : candidates) {
        max = Math.max(max, candidate.score);
        if (candidate.gold) {
          goldScore = candidate.score;
        }
      }
      double normalizer = 0.0;
      for (final BeamNode candidate : candidates) {
        normalizer += Math.exp(candidate.score - max);
      }
      final double logNormalizer = max + Math.log(normalizer);

      zero(hiddenGradient);
      java.util.Arrays.fill(hiddenBiasGradient, 0.0);
      zero(outputGradient);
      java.util.Arrays.fill(outputBiasGradient, 0.0);
      embeddingGradients.clear();
      for (final BeamNode candidate : candidates) {
        final double weight = Math.exp(candidate.score - logNormalizer)
            - (candidate.gold ? 1.0 : 0.0);
        if (weight == 0.0) {
          continue;
        }
        for (BeamNode node = candidate; node.parent != null; node = node.parent) {
          backward(node.features, node.transition, weight);
        }
      }
      update(model.hiddenWeights(), hiddenGradient, hiddenAccumulator, 1, settings);
      updateVector(model.hiddenBias(), hiddenBiasGradient, hiddenBiasAccumulator, 1,
          settings);
      update(model.outputWeights(), outputGradient, outputAccumulator, 1, settings);
      updateVector(model.outputBias(), outputBiasGradient, outputBiasAccumulator, 1,
          settings);
      for (final Map.Entry<Integer, double[]> entry : embeddingGradients.entrySet()) {
        final float[] embeddingRow = model.embeddings()[entry.getKey()];
        final double[] accumulatorRow = embeddingAccumulator[entry.getKey()];
        final double[] gradientRow = entry.getValue();
        for (int d = 0; d < embeddingSize; d++) {
          final double gradient = gradientRow[d];
          accumulatorRow[d] += gradient * gradient;
          embeddingRow[d] -= settings.learningRate() * gradient
              / (Math.sqrt(accumulatorRow[d]) + ADAGRAD_EPSILON);
        }
      }
      return logNormalizer - goldScore;
    }

    /** Computes hidden activations and raw output scores for one feature vector. */
    private void forward(int[] features) {
      final float[][] embeddings = model.embeddings();
      for (int f = 0; f < features.length; f++) {
        final float[] embedding = embeddings[features[f]];
        final int offset = f * embeddingSize;
        for (int d = 0; d < embeddingSize; d++) {
          x[offset + d] = embedding[d];
        }
      }
      final float[][] hiddenWeights = model.hiddenWeights();
      final float[] hiddenBias = model.hiddenBias();
      for (int j = 0; j < hiddenSize; j++) {
        final float[] weightRow = hiddenWeights[j];
        double sum = hiddenBias[j];
        for (int k = 0; k < inputSize; k++) {
          sum += weightRow[k] * x[k];
        }
        pre[j] = sum;
        hidden[j] = sum * sum * sum;
      }
      final float[][] outputWeights = model.outputWeights();
      final float[] outputBias = model.outputBias();
      for (int o = 0; o < outputSize; o++) {
        final float[] weightRow = outputWeights[o];
        double sum = outputBias[o];
        for (int j = 0; j < hiddenSize; j++) {
          sum += weightRow[j] * hidden[j];
        }
        probabilities[o] = sum;
      }
    }

    /**
     * Accumulates gradients for one decoded step: the weighted difference between the
     * step's softmax and its chosen transition.
     *
     * @param features The step's input features.
     * @param chosen The transition the path took at this step.
     * @param weight The path's weight in the candidate distribution.
     */
    private void backward(int[] features, int chosen, double weight) {
      forward(features);
      double max = Double.NEGATIVE_INFINITY;
      for (int o = 0; o < outputSize; o++) {
        max = Math.max(max, probabilities[o]);
      }
      double normalizer = 0.0;
      for (int o = 0; o < outputSize; o++) {
        probabilities[o] = Math.exp(probabilities[o] - max);
        normalizer += probabilities[o];
      }
      java.util.Arrays.fill(hiddenDelta, 0.0);
      java.util.Arrays.fill(inputDelta, 0.0);
      final float[][] outputWeights = model.outputWeights();
      for (int o = 0; o < outputSize; o++) {
        // dL/dlogit for a path's step under the conditional likelihood: the path weight
        // times how the step's log-probability responds to this logit
        final double delta =
            weight * ((o == chosen ? 1.0 : 0.0) - probabilities[o] / normalizer);
        outputBiasGradient[o] += delta;
        final double[] gradientRow = outputGradient[o];
        final float[] weightRow = outputWeights[o];
        for (int j = 0; j < hiddenSize; j++) {
          gradientRow[j] += delta * hidden[j];
          hiddenDelta[j] += delta * weightRow[j];
        }
      }
      final float[][] hiddenWeights = model.hiddenWeights();
      for (int j = 0; j < hiddenSize; j++) {
        final double preDelta = hiddenDelta[j] * 3.0 * pre[j] * pre[j];
        hiddenBiasGradient[j] += preDelta;
        final double[] gradientRow = hiddenGradient[j];
        final float[] weightRow = hiddenWeights[j];
        for (int k = 0; k < inputSize; k++) {
          gradientRow[k] += preDelta * x[k];
          inputDelta[k] += preDelta * weightRow[k];
        }
      }
      for (int f = 0; f < features.length; f++) {
        final double[] embeddingGradient = embeddingGradients
            .computeIfAbsent(features[f], key -> new double[embeddingSize]);
        final int offset = f * embeddingSize;
        for (int d = 0; d < embeddingSize; d++) {
          embeddingGradient[d] += inputDelta[offset + d];
        }
      }
    }

    /** Turns raw scores into log-probabilities in place. */
    private static void logSoftmaxInPlace(double[] scores) {
      double max = Double.NEGATIVE_INFINITY;
      for (final double score : scores) {
        max = Math.max(max, score);
      }
      double sum = 0.0;
      for (final double score : scores) {
        sum += Math.exp(score - max);
      }
      final double logSum = max + Math.log(sum);
      for (int i = 0; i < scores.length; i++) {
        scores[i] -= logSum;
      }
    }
  }

  /** Overwrites the random word rows with pretrained vectors where available. */
  private static void seed(FeedforwardDependencyModel model,
      java.util.function.Function<String, float[]> pretrained, Settings settings) {
    int seeded = 0;
    for (final Map.Entry<String, Integer> entry : model.wordIds().entrySet()) {
      if (entry.getKey().startsWith("*")) {
        continue; // the special symbols have no pretrained meaning
      }
      final float[] vector = pretrained.apply(entry.getKey());
      if (vector == null) {
        continue;
      }
      if (vector.length != settings.embeddingSize()) {
        throw new IllegalArgumentException("pretrained vector for '" + entry.getKey()
            + "' has " + vector.length + " dimensions, expected "
            + settings.embeddingSize());
      }
      System.arraycopy(vector, 0, model.embeddings()[entry.getValue()], 0, vector.length);
      seeded++;
    }
    logger.info("seeded {} of {} word embeddings from the pretrained source", seeded,
        model.wordIds().size());
  }

  /** Builds the vocabularies and randomly initialized weights. */
  private static FeedforwardDependencyModel initialize(List<DependencySample> corpus,
      Settings settings) {
    final Map<String, Integer> wordCounts = new HashMap<>();
    final Map<String, Integer> tagIds = new HashMap<>();
    final Map<String, Integer> labelIds = new HashMap<>();
    final Map<String, Integer> transitionIds = new HashMap<>();
    for (final DependencySample s : corpus) {
      for (final String token : s.getTokens()) {
        wordCounts.merge(FeedforwardDependencyModel.normalize(token), 1, Integer::sum);
      }
      for (final String tag : s.getTags()) {
        tagIds.putIfAbsent(tag, 0);
      }
      final DependencyGraph graph = s.getGraph();
      for (int i = 0; i < graph.size(); i++) {
        labelIds.putIfAbsent(graph.relationOf(i), 0);
      }
    }
    // the outcome space: shift plus both arc directions for every observed label
    transitionIds.putIfAbsent(Transition.SHIFT.encode(), 0);
    for (final String label : labelIds.keySet()) {
      transitionIds.putIfAbsent(Transition.leftArc(label).encode(), 0);
      transitionIds.putIfAbsent(Transition.rightArc(label).encode(), 0);
    }

    int row = 0;
    final Map<String, Integer> wordIds = new HashMap<>();
    for (final String special : List.of(FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.ABSENT, "*ROOT*")) {
      wordIds.put(special, row++);
    }
    for (final Map.Entry<String, Integer> entry : wordCounts.entrySet()) {
      if (entry.getValue() >= settings.wordCutoff() && !wordIds.containsKey(entry.getKey())) {
        wordIds.put(entry.getKey(), row++);
      }
    }
    final Map<String, Integer> tags = new HashMap<>();
    for (final String special : List.of(FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.ABSENT, "*ROOT*")) {
      tags.put(special, row++);
    }
    for (final String tag : tagIds.keySet()) {
      tags.put(tag, row++);
    }
    final Map<String, Integer> labels = new HashMap<>();
    for (final String special : List.of(FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.ABSENT)) {
      labels.put(special, row++);
    }
    for (final String label : labelIds.keySet()) {
      labels.put(label, row++);
    }

    int transitionIndex = 0;
    final String[] transitions = new String[transitionIds.size()];
    for (final String encoded : transitionIds.keySet()) {
      transitions[transitionIndex] = encoded;
      transitionIds.put(encoded, transitionIndex++);
    }

    final Random random = new Random(settings.seed());
    final int inputSize =
        (2 * FeedforwardContext.POSITIONS + FeedforwardContext.LABEL_POSITIONS)
            * settings.embeddingSize();
    final float[][] embeddings = uniform(random, row, settings.embeddingSize(), 0.01);
    final float[][] hiddenWeights = uniform(random, settings.hiddenSize(), inputSize,
        Math.sqrt(6.0 / (inputSize + settings.hiddenSize())));
    final float[][] outputWeights = uniform(random, transitions.length,
        settings.hiddenSize(),
        Math.sqrt(6.0 / (settings.hiddenSize() + transitions.length)));
    return new FeedforwardDependencyModel(wordIds, tags, labels, transitions,
        settings.embeddingSize(), embeddings, hiddenWeights,
        new float[settings.hiddenSize()], outputWeights, new float[transitions.length]);
  }

  /** Replays the oracle over every projective sample, emitting one example per step. */
  private static void collectExamples(List<DependencySample> corpus,
      FeedforwardDependencyModel model, List<int[]> featureList, List<Integer> goldList) {
    final Map<String, Integer> transitionIds = new HashMap<>();
    final String[] transitions = model.transitions();
    for (int i = 0; i < transitions.length; i++) {
      transitionIds.put(transitions[i], i);
    }
    int skipped = 0;
    for (final DependencySample sample : corpus) {
      final List<Transition> oracle;
      try {
        oracle = ArcStandardOracle.transitions(sample.getGraph());
      } catch (IllegalArgumentException e) {
        skipped++;
        continue;
      }
      final ArcStandardState state = new ArcStandardState(sample.getGraph().size());
      final String[] tokens = sample.getTokens();
      final String[] tags = sample.getTags();
      for (final Transition transition : oracle) {
        featureList.add(model.featureIds(FeedforwardContext.extract(state, tokens, tags)));
        goldList.add(transitionIds.get(transition.encode()));
        state.apply(transition);
      }
    }
    if (skipped > 0) {
      logger.warn("Skipped {} non-projective sample(s) without an arc-standard derivation.",
          skipped);
    }
  }

  /** Minibatch AdaGrad over softmax cross-entropy with cube activation and dropout. */
  private static void optimize(FeedforwardDependencyModel model, List<int[]> featureList,
      List<Integer> goldList, Settings settings) {
    final int exampleCount = featureList.size();
    final int[][] features = featureList.toArray(new int[0][]);
    final int[] gold = new int[exampleCount];
    for (int i = 0; i < exampleCount; i++) {
      gold[i] = goldList.get(i);
    }

    final float[][] embeddings = model.embeddings();
    final float[][] hiddenWeights = model.hiddenWeights();
    final float[] hiddenBias = model.hiddenBias();
    final float[][] outputWeights = model.outputWeights();
    final float[] outputBias = model.outputBias();
    final int embeddingSize = settings.embeddingSize();
    final int hiddenSize = settings.hiddenSize();
    final int outputSize = outputBias.length;
    final int inputSize = features[0].length * embeddingSize;

    final double[][] embeddingAccumulator =
        new double[embeddings.length][embeddingSize];
    final double[][] hiddenAccumulator = new double[hiddenSize][inputSize];
    final double[] hiddenBiasAccumulator = new double[hiddenSize];
    final double[][] outputAccumulator = new double[outputSize][hiddenSize];
    final double[] outputBiasAccumulator = new double[outputSize];

    final double[][] hiddenGradient = new double[hiddenSize][inputSize];
    final double[] hiddenBiasGradient = new double[hiddenSize];
    final double[][] outputGradient = new double[outputSize][hiddenSize];
    final double[] outputBiasGradient = new double[outputSize];
    final Map<Integer, double[]> embeddingGradients = new HashMap<>();

    final Random random = new Random(settings.seed());
    final int[] order = new int[exampleCount];
    for (int i = 0; i < exampleCount; i++) {
      order[i] = i;
    }

    final double keep = 1.0 - settings.dropout();
    final double[] x = new double[inputSize];
    final double[] pre = new double[hiddenSize];
    final double[] hidden = new double[hiddenSize];
    final boolean[] mask = new boolean[hiddenSize];
    final double[] probabilities = new double[outputSize];
    final double[] hiddenDelta = new double[hiddenSize];
    final double[] inputDelta = new double[inputSize];

    for (int epoch = 1; epoch <= settings.epochs(); epoch++) {
      final long epochStart = System.currentTimeMillis();
      shuffle(order, random);
      double loss = 0.0;
      for (int batchStart = 0; batchStart < exampleCount;
          batchStart += settings.batchSize()) {
        final int batchEnd = Math.min(batchStart + settings.batchSize(), exampleCount);
        final int batch = batchEnd - batchStart;
        zero(hiddenGradient);
        java.util.Arrays.fill(hiddenBiasGradient, 0.0);
        zero(outputGradient);
        java.util.Arrays.fill(outputBiasGradient, 0.0);
        embeddingGradients.clear();

        for (int b = batchStart; b < batchEnd; b++) {
          final int[] feats = features[order[b]];
          final int goldTransition = gold[order[b]];
          for (int f = 0; f < feats.length; f++) {
            final float[] embedding = embeddings[feats[f]];
            final int offset = f * embeddingSize;
            for (int d = 0; d < embeddingSize; d++) {
              x[offset + d] = embedding[d];
            }
          }
          for (int j = 0; j < hiddenSize; j++) {
            mask[j] = random.nextDouble() < keep;
            if (!mask[j]) {
              pre[j] = 0.0;
              hidden[j] = 0.0;
              continue;
            }
            final float[] weightRow = hiddenWeights[j];
            double sum = hiddenBias[j];
            for (int k = 0; k < inputSize; k++) {
              sum += weightRow[k] * x[k];
            }
            pre[j] = sum;
            hidden[j] = sum * sum * sum / keep;
          }
          double max = Double.NEGATIVE_INFINITY;
          for (int o = 0; o < outputSize; o++) {
            final float[] weightRow = outputWeights[o];
            double sum = outputBias[o];
            for (int j = 0; j < hiddenSize; j++) {
              sum += weightRow[j] * hidden[j];
            }
            probabilities[o] = sum;
            max = Math.max(max, sum);
          }
          double normalizer = 0.0;
          for (int o = 0; o < outputSize; o++) {
            probabilities[o] = Math.exp(probabilities[o] - max);
            normalizer += probabilities[o];
          }
          for (int o = 0; o < outputSize; o++) {
            probabilities[o] /= normalizer;
          }
          loss -= Math.log(Math.max(probabilities[goldTransition], 1e-12));

          java.util.Arrays.fill(hiddenDelta, 0.0);
          java.util.Arrays.fill(inputDelta, 0.0);
          for (int o = 0; o < outputSize; o++) {
            final double delta = probabilities[o] - (o == goldTransition ? 1.0 : 0.0);
            outputBiasGradient[o] += delta;
            final double[] gradientRow = outputGradient[o];
            final float[] weightRow = outputWeights[o];
            for (int j = 0; j < hiddenSize; j++) {
              gradientRow[j] += delta * hidden[j];
              hiddenDelta[j] += delta * weightRow[j];
            }
          }
          for (int j = 0; j < hiddenSize; j++) {
            if (!mask[j]) {
              continue;
            }
            final double preDelta = hiddenDelta[j] * 3.0 * pre[j] * pre[j] / keep;
            hiddenBiasGradient[j] += preDelta;
            final double[] gradientRow = hiddenGradient[j];
            final float[] weightRow = hiddenWeights[j];
            for (int k = 0; k < inputSize; k++) {
              gradientRow[k] += preDelta * x[k];
              inputDelta[k] += preDelta * weightRow[k];
            }
          }
          for (int f = 0; f < feats.length; f++) {
            final double[] embeddingGradient = embeddingGradients
                .computeIfAbsent(feats[f], key -> new double[embeddingSize]);
            final int offset = f * embeddingSize;
            for (int d = 0; d < embeddingSize; d++) {
              embeddingGradient[d] += inputDelta[offset + d];
            }
          }
        }

        update(hiddenWeights, hiddenGradient, hiddenAccumulator, batch, settings);
        updateVector(hiddenBias, hiddenBiasGradient, hiddenBiasAccumulator, batch, settings);
        update(outputWeights, outputGradient, outputAccumulator, batch, settings);
        updateVector(outputBias, outputBiasGradient, outputBiasAccumulator, batch, settings);
        for (final Map.Entry<Integer, double[]> entry : embeddingGradients.entrySet()) {
          final float[] embeddingRow = embeddings[entry.getKey()];
          final double[] accumulatorRow = embeddingAccumulator[entry.getKey()];
          final double[] gradientRow = entry.getValue();
          for (int d = 0; d < embeddingSize; d++) {
            final double gradient = gradientRow[d] / batch;
            accumulatorRow[d] += gradient * gradient;
            embeddingRow[d] -= settings.learningRate() * gradient
                / (Math.sqrt(accumulatorRow[d]) + ADAGRAD_EPSILON);
          }
        }
      }
      logger.info("epoch {}: loss {} in {} ms", epoch, loss / exampleCount,
          System.currentTimeMillis() - epochStart);
    }
  }

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

  private static void updateVector(float[] weights, double[] gradients,
      double[] accumulators, int batch, Settings settings) {
    for (int i = 0; i < weights.length; i++) {
      final double gradient = gradients[i] / batch;
      accumulators[i] += gradient * gradient;
      weights[i] -= settings.learningRate() * gradient
          / (Math.sqrt(accumulators[i]) + ADAGRAD_EPSILON);
    }
  }

  private static float[][] uniform(Random random, int rows, int columns, double scale) {
    final float[][] matrix = new float[rows][columns];
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < columns; c++) {
        matrix[r][c] = (float) ((random.nextDouble() * 2.0 - 1.0) * scale);
      }
    }
    return matrix;
  }

  private static void zero(double[][] matrix) {
    for (final double[] row : matrix) {
      java.util.Arrays.fill(row, 0.0);
    }
  }

  private static void shuffle(int[] order, Random random) {
    for (int i = order.length - 1; i > 0; i--) {
      final int j = random.nextInt(i + 1);
      final int swap = order[i];
      order[i] = order[j];
      order[j] = swap;
    }
  }
}
