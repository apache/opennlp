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

import de.hhn.mi.configuration.KernelType;
import de.hhn.mi.configuration.SvmConfigurationImpl;
import de.hhn.mi.configuration.SvmType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvmDoccatConfigurationTest {

  @Test
  void testDefaults() {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder().build();

    assertNotNull(config.getSvmConfiguration());
    assertEquals(TermWeightingStrategy.TF_IDF, config.getTermWeightingStrategy());
    assertEquals(FeatureSelectionStrategy.NONE, config.getFeatureSelectionStrategy());
    assertEquals(-1, config.getMaxFeatures());
    assertTrue(config.isScaleFeatures());
    assertEquals(0.0, config.getScaleLower());
    assertEquals(1.0, config.getScaleUpper());
  }

  @Test
  void testCustomSvmConfiguration() {
    var svmConfig = new SvmConfigurationImpl.Builder()
        .setKernelType(KernelType.POLYNOMIAL)
        .setCost(5.0)
        .setDegree(2)
        .setProbability(true)
        .build();

    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(svmConfig)
        .build();

    assertEquals(KernelType.POLYNOMIAL, config.getSvmConfiguration().getKernelType());
    assertEquals(5.0, config.getSvmConfiguration().getCost());
    assertEquals(2, config.getSvmConfiguration().getDegree());
  }

  @Test
  void testAllWeightingStrategies() {
    for (TermWeightingStrategy strategy : TermWeightingStrategy.values()) {
      SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
          .setTermWeightingStrategy(strategy)
          .build();
      assertEquals(strategy, config.getTermWeightingStrategy());
    }
  }

  @Test
  void testAllFeatureSelectionStrategies() {
    for (FeatureSelectionStrategy strategy : FeatureSelectionStrategy.values()) {
      SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
          .setFeatureSelectionStrategy(strategy)
          .build();
      assertEquals(strategy, config.getFeatureSelectionStrategy());
    }
  }

  @Test
  void testMaxFeatures() {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setMaxFeatures(100)
        .build();
    assertEquals(100, config.getMaxFeatures());
  }

  @Test
  void testScalingDisabled() {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setScaleFeatures(false)
        .build();
    assertFalse(config.isScaleFeatures());
  }

  @Test
  void testCustomScaleRange() {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setScaleRange(-1.0, 1.0)
        .build();
    assertEquals(-1.0, config.getScaleLower());
    assertEquals(1.0, config.getScaleUpper());
  }

  @Test
  void testInvalidScaleRangeThrows() {
    assertThrows(IllegalArgumentException.class, () ->
        new SvmDoccatConfiguration.Builder().setScaleRange(1.0, 0.0));
  }

  @Test
  void testEqualBoundsThrows() {
    assertThrows(IllegalArgumentException.class, () ->
        new SvmDoccatConfiguration.Builder().setScaleRange(0.5, 0.5));
  }

  @Test
  void testNullSvmConfigurationThrows() {
    assertThrows(NullPointerException.class, () ->
        new SvmDoccatConfiguration.Builder().setSvmConfiguration(null));
  }

  @Test
  void testNullWeightingStrategyThrows() {
    assertThrows(NullPointerException.class, () ->
        new SvmDoccatConfiguration.Builder().setTermWeightingStrategy(null));
  }

  @Test
  void testNullFeatureSelectionStrategyThrows() {
    assertThrows(NullPointerException.class, () ->
        new SvmDoccatConfiguration.Builder().setFeatureSelectionStrategy(null));
  }

  @Test
  void testSvmTypePreserved() {
    var svmConfig = new SvmConfigurationImpl.Builder()
        .setSvmType(SvmType.NU_SVC)
        .setProbability(true)
        .build();

    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(svmConfig)
        .build();

    assertEquals(SvmType.NU_SVC, config.getSvmConfiguration().getSvmType());
  }
}
