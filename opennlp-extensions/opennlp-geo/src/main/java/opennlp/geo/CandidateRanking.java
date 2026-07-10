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
package opennlp.geo;

import java.util.Comparator;

import opennlp.tools.geo.GazetteerEntry;

/**
 * The shared candidate order of this module: population descending, then a small feature-class
 * prior ({@link GazetteerEntry#FEATURE_CLASS_CITY} before {@link GazetteerEntry#FEATURE_CLASS_ADMIN}
 * before {@link GazetteerEntry#FEATURE_CLASS_POI} before anything else, applied on population
 * ties only), then source and record id. The trailing identifier comparison makes the order
 * total, so any candidate list sorts the same way on every run.
 */
final class CandidateRanking {

  /** Population descending, feature-class prior, then source and record id. */
  static final Comparator<GazetteerEntry> BY_PRIOR =
      Comparator.comparingLong(GazetteerEntry::population).reversed()
          .thenComparingInt(entry -> featureClassRank(entry.featureClass()))
          .thenComparing(GazetteerEntry::source)
          .thenComparing(GazetteerEntry::recordId);

  private CandidateRanking() {
  }

  // CITY before ADMIN before POI before anything else (including unknown). Matching on the
  // published constants is what makes the prior portable: any gazetteer that uses them gets it.
  static int featureClassRank(String featureClass) {
    if (GazetteerEntry.FEATURE_CLASS_CITY.equals(featureClass)) {
      return 0;
    }
    if (GazetteerEntry.FEATURE_CLASS_ADMIN.equals(featureClass)) {
      return 1;
    }
    if (GazetteerEntry.FEATURE_CLASS_POI.equals(featureClass)) {
      return 2;
    }
    return 3;
  }
}
