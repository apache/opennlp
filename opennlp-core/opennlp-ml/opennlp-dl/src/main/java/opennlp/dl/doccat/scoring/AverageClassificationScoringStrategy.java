/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl.doccat.scoring;

import java.util.List;

/**
 * A {@link ClassificationScoringStrategy} which calculates the document classification scores
 * by averaging the scores for all individual parts of a document.
 *
 * @see ClassificationScoringStrategy
 */
public class AverageClassificationScoringStrategy implements ClassificationScoringStrategy {

  @Override
  public double[] score(List<double[]> scores) {

    final int values = scores.get(0).length;

    final double[] averages = new double[values];

    int j = 0;

    for (int i = 0; i < values; i++) {

      double sum = 0;

      for (final double[] score : scores) {

        sum += score[i];

      }

      averages[j++] = (sum / scores.size());

    }

    return averages;

  }

}
