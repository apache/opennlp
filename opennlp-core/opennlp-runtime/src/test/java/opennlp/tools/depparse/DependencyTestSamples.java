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
import java.util.List;

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

  /** The gold graph of the first corpus sentence. */
  static final DependencyGraph THE_DOG_BARKS_GRAPH =
      DependencyGraph.of(new int[] {1, 2, -1}, new String[] {"det", "nsubj", "root"});

  /** The tokens of the third corpus sentence. */
  static final String[] SHE_EATS_FISH_TOKENS = {"she", "eats", "fish"};

  /** The tags of the third corpus sentence. */
  static final String[] SHE_EATS_FISH_TAGS = {"PRP", "VBZ", "NN"};

  /** The gold graph of the third corpus sentence. */
  static final DependencyGraph SHE_EATS_FISH_GRAPH =
      DependencyGraph.of(new int[] {1, -1, 1}, new String[] {"nsubj", "root", "obj"});

  /** How often the distinct sentences are repeated in {@link #corpus()}. */
  private static final int REPETITIONS = 40;

  /** The sentences in {@link #corpus()}. */
  static final int CORPUS_SENTENCES = 3 * REPETITIONS;

  /** The tokens in {@link #corpus()}: the distinct sentences have 3 + 2 + 3 tokens. */
  static final int CORPUS_WORDS = 8 * REPETITIONS;

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
    return new DependencySample(tokens, tags, DependencyGraph.of(heads, relations));
  }

  /**
   * Builds the three distinct projective sentences of the test corpus.
   *
   * @return One sample per sentence. Never {@code null}.
   */
  static List<DependencySample> sentences() {
    return List.of(
        new DependencySample(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS, THE_DOG_BARKS_GRAPH),
        sample(new String[] {"dogs", "bark"}, new String[] {"NNS", "VBP"},
            new int[] {1, -1}, new String[] {"nsubj", "root"}),
        new DependencySample(SHE_EATS_FISH_TOKENS, SHE_EATS_FISH_TAGS, SHE_EATS_FISH_GRAPH));
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
}
