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

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AverageClassificationScoringStrategyTest {

  @Test
  public void calculateAverage1() {

    final List<double[]> scores = new LinkedList<>();
    scores.add(new double[]{1, 2, 3, 4, 5});
    scores.add(new double[]{1, 2, 3, 4, 5});
    scores.add(new double[]{1, 2, 3, 4, 5});

    final ClassificationScoringStrategy strategy = new AverageClassificationScoringStrategy();
    final double[] results = strategy.score(scores);

    Assertions.assertEquals(1.0, results[0], 0);
    Assertions.assertEquals(2.0, results[1], 0);
    Assertions.assertEquals(3.0, results[2], 0);
    Assertions.assertEquals(4.0, results[3], 0);
    Assertions.assertEquals(5.0, results[4], 0);

  }

  @Test
  public void calculateAverage2() {

    final List<double[]> scores = new LinkedList<>();
    scores.add(new double[]{2, 1, 5});
    scores.add(new double[]{4, 3, 10});
    scores.add(new double[]{6, 5, 15});

    final ClassificationScoringStrategy strategy = new AverageClassificationScoringStrategy();
    final double[] results = strategy.score(scores);

    Assertions.assertEquals(4.0, results[0], 0);
    Assertions.assertEquals(3.0, results[1], 0);
    Assertions.assertEquals(10.0, results[2], 0);

  }

}
