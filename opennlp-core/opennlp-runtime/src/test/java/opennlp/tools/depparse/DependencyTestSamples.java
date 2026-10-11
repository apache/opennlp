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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

/**
 * The gold samples and training setup shared by the dependency parser tests.
 */
final class DependencyTestSamples {

  /** The language code of the test corpus. */
  static final String LANGUAGE = "eng";

  /** The tokens of the first corpus sentence. */
  static final String[] THE_DOG_BARKS_TOKENS = {"the", "dog", "barks"};

  /** The tags of the first corpus sentence. */
  static final String[] THE_DOG_BARKS_TAGS = {"DT", "NN", "VBZ"};

  /** The gold tree of the first corpus sentence. */
  static final DependencyTree THE_DOG_BARKS_TREE =
      DependencyTree.of(new int[] {1, 2, -1}, new String[] {"det", "nsubj", "root"});

  /** The tokens of the third corpus sentence. */
  static final String[] SHE_EATS_FISH_TOKENS = {"she", "eats", "fish"};

  /** The tags of the third corpus sentence. */
  static final String[] SHE_EATS_FISH_TAGS = {"PRP", "VBZ", "NN"};

  /** The gold tree of the third corpus sentence. */
  static final DependencyTree SHE_EATS_FISH_TREE =
      DependencyTree.of(new int[] {1, -1, 1}, new String[] {"nsubj", "root", "obj"});

  /** Four-token heads whose arcs (2,0) and (3,1) cross, so the tree is non-projective. */
  private static final int[] NON_PROJECTIVE_HEADS = {2, 3, -1, 2};

  /** A relation label none of the corpus sentences uses. */
  static final String UNSEEN_RELATION = "dislocated";

  /** The random seed of every feedforward training run in the tests. */
  static final int SEED = FeedforwardDependencyTrainer.DEFAULT_SEED;

  /** The embedding width of the feedforward test networks. */
  static final int EMBEDDING_SIZE = 16;

  /** The hidden width of the feedforward test networks. */
  static final int HIDDEN_SIZE = 32;

  /** The minibatch size of the feedforward test networks. */
  static final int BATCH_SIZE = 32;

  /** The word cutoff that gives every corpus word its own embedding row. */
  static final int WORD_CUTOFF = 1;

  /** The transition inventory of the hand-built feedforward test networks. */
  static final String[] MINIMAL_TRANSITIONS = {"SHIFT", "LEFT_ARC:dep", "RIGHT_ARC:root"};

  /** A scoring cache size that holds every table of the test networks. */
  static final long CACHE_BYTES = 1L << 20;

  /** How often the distinct sentences are repeated in {@link #corpus()}. */
  private static final int REPETITIONS = 40;

  /** The sentences in {@link #corpus()}. */
  static final int CORPUS_SENTENCES = 3 * REPETITIONS;

  /** The tokens in {@link #corpus()}: the distinct sentences have 3 + 2 + 3 tokens. */
  static final int CORPUS_WORDS = 8 * REPETITIONS;

  /** The model {@link #localFeedforward()} trains on first use. */
  private static FeedforwardDependencyModel localFeedforward;

  /** Prevents construction of this utility class. */
  private DependencyTestSamples() {
  }

  /**
   * Builds one gold sample from its parallel arrays.
   *
   * @param tokens The sentence tokens. Must not be {@code null}.
   * @param tags The part-of-speech tags aligned with {@code tokens}.
   * @param heads The zero-based head per token, {@code -1} for the root.
   * @param relations The relation label per token.
   * @return The assembled sample. Never {@code null}.
   */
  static DependencySample sample(String[] tokens, String[] tags, int[] heads,
      String[] relations) {
    return new DependencySample(tokens, tags, DependencyTree.of(heads, relations));
  }

  /**
   * Builds a four-token sample whose gold arcs (2,0) and (3,1) cross, so the tree is
   * non-projective and has no arc-standard derivation.
   *
   * @return The non-projective sample. Never {@code null}.
   */
  static DependencySample nonProjectiveSample() {
    return nonProjectiveSample(new String[] {"the", "dog", "barks", "today"},
        new String[] {"DT", "NN", "VBZ", "RB"},
        new String[] {"det", "nsubj", "root", "advmod"});
  }

  /**
   * Builds a four-token sample over the crossing arcs (2,0) and (3,1), so the tree is
   * non-projective and has no arc-standard derivation.
   *
   * @param tokens The four sentence tokens. Must not be {@code null}.
   * @param tags The part-of-speech tags aligned with {@code tokens}.
   * @param relations The relation label per token.
   * @return The non-projective sample. Never {@code null}.
   */
  static DependencySample nonProjectiveSample(String[] tokens, String[] tags,
      String[] relations) {
    return sample(tokens, tags, NON_PROJECTIVE_HEADS, relations);
  }

  /**
   * Builds a non-projective sample that also uses {@link #UNSEEN_RELATION}, so a
   * trainer must skip it for projectivity before it validates the relation.
   *
   * @return The non-projective sample. Never {@code null}.
   */
  static DependencySample nonProjectiveSampleWithUnseenRelation() {
    return nonProjectiveSample(new String[] {"a", "b", "c", "d"},
        new String[] {"DT", "NN", "VBZ", "NN"},
        new String[] {"det", UNSEEN_RELATION, "root", "obj"});
  }

  /**
   * Builds the three distinct projective sentences of the test corpus.
   *
   * @return One sample per sentence. Never {@code null}.
   */
  static List<DependencySample> sentences() {
    return List.of(
        new DependencySample(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS, THE_DOG_BARKS_TREE),
        sample(new String[] {"dogs", "bark"}, new String[] {"NNS", "VBP"},
            new int[] {1, -1}, new String[] {"nsubj", "root"}),
        new DependencySample(SHE_EATS_FISH_TOKENS, SHE_EATS_FISH_TAGS, SHE_EATS_FISH_TREE));
  }

  /**
   * Builds the training corpus: {@link #sentences()} repeated {@code REPETITIONS}
   * times, which makes the small parsers memorize them deterministically.
   *
   * @return The training samples. Never {@code null}.
   */
  static List<DependencySample> corpus() {
    return repeat(sentences());
  }

  /**
   * Repeats samples {@code REPETITIONS} times, in order.
   *
   * @param distinct The samples to repeat. Must not be {@code null}.
   * @return The repeated samples. Never {@code null}.
   */
  static List<DependencySample> repeat(List<DependencySample> distinct) {
    final List<DependencySample> repeated = new ArrayList<>(REPETITIONS * distinct.size());
    for (int i = 0; i < REPETITIONS; i++) {
      repeated.addAll(distinct);
    }
    return repeated;
  }

  /**
   * @return Default training parameters with a zero cutoff, so the small test corpus
   *         keeps every feature. Never {@code null}.
   */
  static TrainingParameters trainingParameters() {
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    return parameters;
  }

  /**
   * Trains a model on samples with {@link #trainingParameters()}.
   *
   * @param samples The training samples. Must not be {@code null}.
   * @return The trained model. Never {@code null}.
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  static DependencyModel train(List<DependencySample> samples) throws IOException {
    return DependencyParserME.train(LANGUAGE, ObjectStreamUtils.createObjectStream(samples),
        trainingParameters());
  }

  /**
   * Builds feedforward training parameters for a small network: no dropout, no L2,
   * every corpus word embedded, and a fixed seed, so the network memorizes the corpus
   * deterministically.
   *
   * @param epochs The number of passes over the corpus.
   * @param learningRate The AdaGrad step size.
   * @return The parameters. Never {@code null}.
   */
  static TrainingParameters feedforwardParameters(int epochs, double learningRate) {
    final TrainingParameters parameters = new TrainingParameters();
    parameters.put(FeedforwardDependencyTrainer.EMBEDDING_SIZE_PARAM, EMBEDDING_SIZE);
    parameters.put(FeedforwardDependencyTrainer.HIDDEN_SIZE_PARAM, HIDDEN_SIZE);
    parameters.put(Parameters.ITERATIONS_PARAM, epochs);
    parameters.put(FeedforwardDependencyTrainer.BATCH_SIZE_PARAM, BATCH_SIZE);
    parameters.put(FeedforwardDependencyTrainer.LEARNING_RATE_PARAM, learningRate);
    parameters.put(QNTrainer.L2COST_PARAM, 0.0);
    parameters.put(FeedforwardDependencyTrainer.DROPOUT_PARAM, 0.0);
    parameters.put(Parameters.CUTOFF_PARAM, WORD_CUTOFF);
    parameters.put(FeedforwardDependencyTrainer.SEED_PARAM, SEED);
    return parameters;
  }

  /**
   * Trains a feedforward model on samples.
   *
   * @param samples The training samples. Must not be {@code null}.
   * @param parameters The training parameters. Must not be {@code null}.
   * @return The trained model. Never {@code null}.
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  static FeedforwardDependencyModel trainFeedforward(List<DependencySample> samples,
      TrainingParameters parameters) throws IOException {
    return FeedforwardDependencyTrainer.train(LANGUAGE,
        ObjectStreamUtils.createObjectStream(samples), parameters);
  }

  /**
   * Trains the shared feedforward model: 120 epochs at a step size of 0.05 memorize
   * {@link #corpus()}.
   *
   * @return The trained model. Never {@code null}.
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  static FeedforwardDependencyModel trainFeedforward() throws IOException {
    return trainFeedforward(corpus(), feedforwardParameters(120, 0.05));
  }

  /**
   * Trains the locally trained model that the refinement tests start from, once: 60
   * epochs at a step size of 0.05 on {@link #corpus()}. Refinement copies it, so the
   * tests can share it.
   *
   * @return The model. Never {@code null}.
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  static synchronized FeedforwardDependencyModel localFeedforward() throws IOException {
    if (localFeedforward == null) {
      localFeedforward = trainFeedforward(corpus(), feedforwardParameters(60, 0.05));
    }
    return localFeedforward;
  }

  /**
   * Scores a configuration with fresh buffers.
   *
   * @param network The network.
   * @param features The embedding rows of the configuration.
   * @param cache The contribution cache, or {@code null}.
   * @return The transition scores. Never {@code null}.
   */
  static double[] score(FeedforwardNetwork network, int[] features, ContributionCache cache) {
    return network.score(features, cache, new double[network.hiddenSize()],
        new double[network.transitions().length]);
  }

  /**
   * @param network The network to serialize.
   * @return The network bytes. Never {@code null}.
   * @throws IOException Thrown if writing fails.
   */
  static byte[] networkBytes(FeedforwardNetwork network) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    new FeedforwardNetwork.Serializer().serialize(network, out);
    return out.toByteArray();
  }

  /**
   * Builds a network over the reserved vocabularies only, in the row order the trainer
   * assigns, and {@link #MINIMAL_TRANSITIONS}.
   *
   * @param embeddings The embedding matrix, one row per reserved symbol.
   * @param hiddenWeights The hidden weights.
   * @param hiddenBias The hidden bias.
   * @param outputWeights The output weights, one row per transition.
   * @return The network. Never {@code null}.
   */
  static FeedforwardNetwork minimalNetwork(float[][] embeddings, float[][] hiddenWeights,
      float[] hiddenBias, float[][] outputWeights) {
    final Map<String, Integer> wordIds = new HashMap<>();
    final Map<String, Integer> tagIds = new HashMap<>();
    final Map<String, Integer> labelIds = new HashMap<>();
    int row = 0;
    for (final String symbol : FeedforwardNetwork.RESERVED_WORD_SYMBOLS) {
      wordIds.put(symbol, row++);
    }
    for (final String symbol : FeedforwardNetwork.RESERVED_TAG_SYMBOLS) {
      tagIds.put(symbol, row++);
    }
    for (final String symbol : FeedforwardNetwork.RESERVED_LABEL_SYMBOLS) {
      labelIds.put(symbol, row++);
    }
    return new FeedforwardNetwork(wordIds, tagIds, labelIds, MINIMAL_TRANSITIONS,
        embeddings[0].length, embeddings, hiddenWeights, hiddenBias, outputWeights,
        new float[MINIMAL_TRANSITIONS.length]);
  }

  /**
   * Wraps a network in a model with the test language.
   *
   * @param network The network. Must not be {@code null}.
   * @return The model. Never {@code null}.
   */
  static FeedforwardDependencyModel model(FeedforwardNetwork network) {
    return new FeedforwardDependencyModel(LANGUAGE, network, null);
  }
}
