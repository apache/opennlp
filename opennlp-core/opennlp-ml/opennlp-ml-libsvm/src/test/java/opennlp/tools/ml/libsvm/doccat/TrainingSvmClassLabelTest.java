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

package opennlp.tools.ml.libsvm.doccat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingSvmClassLabelTest {

  @Test
  void testGetters() {
    TrainingSvmClassLabel label = new TrainingSvmClassLabel(2.0, "sports", 0.85);
    assertEquals(2.0, label.getNumeric());
    assertEquals("sports", label.getName());
    assertEquals(0.85, label.getProbability());
  }

  @Test
  void testCompareToHigherProbability() {
    TrainingSvmClassLabel low = new TrainingSvmClassLabel(0, "a", 0.2);
    TrainingSvmClassLabel high = new TrainingSvmClassLabel(1, "b", 0.8);

    assertTrue(low.compareTo(high) < 0);
    assertTrue(high.compareTo(low) > 0);
  }

  @Test
  void testCompareToEqualProbability() {
    TrainingSvmClassLabel a = new TrainingSvmClassLabel(0, "a", 0.5);
    TrainingSvmClassLabel b = new TrainingSvmClassLabel(1, "b", 0.5);

    assertEquals(0, a.compareTo(b));
  }

  @Test
  void testCompareToZeroProbability() {
    TrainingSvmClassLabel zero = new TrainingSvmClassLabel(0, "z", 0.0);
    TrainingSvmClassLabel nonZero = new TrainingSvmClassLabel(1, "nz", 0.01);

    assertTrue(zero.compareTo(nonZero) < 0);
  }
}
