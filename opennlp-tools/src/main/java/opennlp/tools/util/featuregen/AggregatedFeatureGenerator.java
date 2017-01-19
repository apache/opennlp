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
import java.util.Objects;

/**
 * The {@link AggregatedFeatureGenerator} aggregates a set of
 * {@link AdaptiveFeatureGenerator}s and calls them to generate the features.
 */
public class AggregatedFeatureGenerator implements AdaptiveFeatureGenerator {

  /**
   * Contains all aggregated {@link AdaptiveFeatureGenerator}s.
   */
  private Collection<AdaptiveFeatureGenerator> generators;

  /**
   * Initializes the current instance.
   *
   * @param generators array of generators, null values are not permitted
   */
  public AggregatedFeatureGenerator(AdaptiveFeatureGenerator... generators) {

    for (AdaptiveFeatureGenerator generator : generators) {
      Objects.requireNonNull(generator, "null values in generators are not permitted");
    }

    this.generators = new ArrayList<>(generators.length);

    Collections.addAll(this.generators, generators);

    this.generators = Collections.unmodifiableCollection(this.generators);
  }

  public AggregatedFeatureGenerator(Collection<AdaptiveFeatureGenerator> generators) {
    this(generators.toArray(new AdaptiveFeatureGenerator[generators.size()]));
  }

  /**
   * Calls the {@link AdaptiveFeatureGenerator#clearAdaptiveData()} method
   * on all aggregated {@link AdaptiveFeatureGenerator}s.
   */
  public void clearAdaptiveData() {

    for (AdaptiveFeatureGenerator generator : generators) {
      generator.clearAdaptiveData();
    }
  }

  /**
   * Calls the {@link AdaptiveFeatureGenerator#createFeatures(List, String[], int, String[])}
   * method on all aggregated {@link AdaptiveFeatureGenerator}s.
   */
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    for (AdaptiveFeatureGenerator generator : generators) {
      generator.createFeatures(features, tokens, index, previousOutcomes);
    }
  }

  /**
   * Calls the {@link AdaptiveFeatureGenerator#updateAdaptiveData(String[], String[])}
   * method on all aggregated {@link AdaptiveFeatureGenerator}s.
   */
  public void updateAdaptiveData(String[] tokens, String[] outcomes) {

    for (AdaptiveFeatureGenerator generator : generators) {
      generator.updateAdaptiveData(tokens, outcomes);
    }
  }

  /**
   * Retrieves a {@link Collections} of all aggregated
   * {@link AdaptiveFeatureGenerator}s.
   *
   * @return all aggregated generators
   */
  public Collection<AdaptiveFeatureGenerator> getGenerators() {
    return generators;
  }
}
