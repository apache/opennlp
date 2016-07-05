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


package opennlp.tools.util.featuregen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The {@link AggregatedFeatureGenerator} aggregates a set of
 * {@link FeatureGeneratorAdapter}s and calls them to generate the features.
 */
public class AggregatedFeatureGenerator extends FeatureGeneratorAdapter {

  /**
   * Contains all aggregated {@link FeatureGeneratorAdapter}s.
   */
  private Collection<FeatureGeneratorAdapter> generators;

  /**
   * Initializes the current instance.
   *
   * @param generators array of generators, null values are not permitted
   */
  public AggregatedFeatureGenerator(FeatureGeneratorAdapter... generators) {

    for (FeatureGeneratorAdapter generator : generators) {
      if (generator == null)
        throw new IllegalArgumentException("null values in generators are not permitted!");
    }

    this.generators = new ArrayList<FeatureGeneratorAdapter>(generators.length);

    Collections.addAll(this.generators, generators);

    this.generators = Collections.unmodifiableCollection(this.generators);
  }

  public AggregatedFeatureGenerator(Collection<FeatureGeneratorAdapter> generators) {
    this(generators.toArray(new FeatureGeneratorAdapter[generators.size()]));
  }

  /**
   * Calls the {@link FeatureGeneratorAdapter#clearAdaptiveData()} method
   * on all aggregated {@link FeatureGeneratorAdapter}s.
   */
  @Override
  public void clearAdaptiveData() {

    for (FeatureGeneratorAdapter generator : generators) {
      generator.clearAdaptiveData();
    }
  }

  /**
   * Calls the {@link FeatureGeneratorAdapter#createFeatures(List, String[], int, String[])}
   * method on all aggregated {@link FeatureGeneratorAdapter}s.
   */
  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    for (FeatureGeneratorAdapter generator : generators) {
      generator.createFeatures(features, tokens, index, previousOutcomes);
    }
  }

  /**
   * Calls the {@link FeatureGeneratorAdapter#updateAdaptiveData(String[], String[])}
   * method on all aggregated {@link FeatureGeneratorAdapter}s.
   */
  @Override
  public void updateAdaptiveData(String[] tokens, String[] outcomes) {

    for (FeatureGeneratorAdapter generator : generators) {
      generator.updateAdaptiveData(tokens, outcomes);
    }
  }

  /**
   * Retrieves a {@link Collections} of all aggregated
   * {@link FeatureGeneratorAdapter}s.
   *
   * @return all aggregated generators
   */
  public Collection<FeatureGeneratorAdapter> getGenerators() {
    return generators;
  }
}
