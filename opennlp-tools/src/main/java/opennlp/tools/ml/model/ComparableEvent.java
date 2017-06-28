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
 * A maxent event representation which we can use to sort based on the
 * predicates indexes contained in the events.
 */
public class ComparableEvent implements Comparable<ComparableEvent> {
  public int outcome;
  public int[] predIndexes;
  public int seen = 1; // the number of times this event has been seen.

  public float[] values;

  public ComparableEvent(int oc, int[] pids, float[] values) {
    outcome = oc;
    this.values = values;
    predIndexes = pids;
  }

  public ComparableEvent(int oc, int[] pids) {
    this(oc, pids, null);
  }

  public int compareTo(ComparableEvent ce) {

    int compareOutcome = Integer.compare(outcome, ce.outcome);
    if (compareOutcome != 0) {
      return compareOutcome;
    }

    int smallerLength = Math.min(predIndexes.length, ce.predIndexes.length);

    for (int i = 0; i < smallerLength; i++) {
      int comparePredIndexes = Integer.compare(predIndexes[i], ce.predIndexes[i]);
      if (comparePredIndexes != 0) {
        return comparePredIndexes;
      }
      if (values != null && ce.values != null) {
        float compareValues = Float.compare(values[i], ce.values[i]);
        if (!Float.valueOf(compareValues).equals(Float.valueOf(0.0f))) {
          return (int) compareValues;
        }
      } else if (values != null) {
        float compareValues = Float.compare(values[i], 1.0f);
        if (!Float.valueOf(compareValues).equals(Float.valueOf(0.0f))) {
          return (int) compareValues;
        }
      } else if (ce.values != null) {
        float compareValues = Float.compare(1.0f, ce.values[i]);
        if (!Float.valueOf(compareValues).equals(Float.valueOf(0.0f))) {
          return (int) compareValues;
        }
      }
    }

    int comparePredIndexesLength = Integer.compare(predIndexes.length, ce.predIndexes.length);
    if (comparePredIndexesLength != 0) {
      return comparePredIndexesLength;
    }

    return 0;
  }

  @Override
  public boolean equals(Object obj) {

    if (this == obj)
      return true;

    if (obj instanceof ComparableEvent) {
      ComparableEvent other = (ComparableEvent) obj;
      return outcome == other.outcome &&
          Arrays.equals(predIndexes, other.predIndexes) &&
          seen == other.seen &&
          Arrays.equals(values, other.values);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(outcome, Arrays.hashCode(predIndexes), seen, Arrays.hashCode(values));
  }

  public String toString() {
    StringBuilder s = new StringBuilder().append(outcome).append(":");
    for (int i = 0; i < predIndexes.length; i++) {
      s.append(" ").append(predIndexes[i]);
      if (values != null) {
        s.append("=").append(values[i]);
      }
    }
    return s.toString();
  }

}

