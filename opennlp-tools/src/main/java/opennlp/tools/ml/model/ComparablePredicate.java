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

package opennlp.tools.ml.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * A maxent predicate representation which we can use to sort based on the
 * outcomes. This allows us to make the mapping of features to their parameters
 * much more compact.
 */
public class ComparablePredicate implements Comparable<ComparablePredicate> {
  public String name;
  public int[] outcomes;
  public double[] params;

  public ComparablePredicate(String n, int[] ocs, double[] ps) {
    name = n;
    outcomes = ocs;
    params = ps;
  }

  public int compareTo(ComparablePredicate cp) {
    int smallerLength = Math.min(outcomes.length, cp.outcomes.length);

    for (int i = 0; i < smallerLength; i++) {
      int compareOutcomes = Integer.compare(outcomes[i], cp.outcomes[i]);
      if (compareOutcomes != 0) {
        return compareOutcomes;
      }
    }

    int compareOutcomesLength = Integer.compare(outcomes.length, cp.outcomes.length);
    if (compareOutcomesLength != 0) {
      return compareOutcomesLength;
    }

    return 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, Arrays.hashCode(outcomes), Arrays.hashCode(params));
  }

  @Override
  public boolean equals(Object obj) {

    if (this == obj)
      return true;

    if (obj instanceof ComparablePredicate) {
      ComparablePredicate other = (ComparablePredicate) obj;
      return Objects.equals(name, other.name) &&
          Arrays.equals(outcomes, other.outcomes) &&
          Arrays.equals(params, other.params);
    }

    return false;
  }

  public String toString() {
    StringBuilder s = new StringBuilder();
    for (int outcome : outcomes) {
      s.append(" ").append(outcome);
    }
    return s.toString();
  }

}

