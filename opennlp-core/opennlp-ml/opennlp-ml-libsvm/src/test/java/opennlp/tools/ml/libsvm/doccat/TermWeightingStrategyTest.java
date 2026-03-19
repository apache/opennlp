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

class TermWeightingStrategyTest {

  @Test
  void testBinary() {
    assertEquals(1.0, TermWeightingStrategy.BINARY.weight(1, 0.0));
    assertEquals(1.0, TermWeightingStrategy.BINARY.weight(5, 2.0));
    assertEquals(0.0, TermWeightingStrategy.BINARY.weight(0, 0.0));
  }

  @Test
  void testTermFrequency() {
    assertEquals(3.0, TermWeightingStrategy.TERM_FREQUENCY.weight(3, 0.0));
    assertEquals(0.0, TermWeightingStrategy.TERM_FREQUENCY.weight(0, 0.0));
    assertEquals(1.0, TermWeightingStrategy.TERM_FREQUENCY.weight(1, 5.0));
  }

  @Test
  void testTfIdf() {
    double idf = Math.log(10.0 / 2.0); // ~1.609
    assertEquals(3.0 * idf, TermWeightingStrategy.TF_IDF.weight(3, idf), 1e-9);
    assertEquals(0.0, TermWeightingStrategy.TF_IDF.weight(0, idf));
  }

  @Test
  void testLogNormalizedTf() {
    assertEquals(1.0 + Math.log(3), TermWeightingStrategy.LOG_NORMALIZED_TF.weight(3, 0.0), 1e-9);
    assertEquals(1.0, TermWeightingStrategy.LOG_NORMALIZED_TF.weight(1, 0.0));
    assertEquals(0.0, TermWeightingStrategy.LOG_NORMALIZED_TF.weight(0, 0.0));
  }
}
