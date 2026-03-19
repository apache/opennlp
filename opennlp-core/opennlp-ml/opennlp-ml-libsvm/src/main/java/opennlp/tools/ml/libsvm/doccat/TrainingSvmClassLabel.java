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

import java.io.Serial;

import de.hhn.mi.domain.SvmClassLabel;

/**
 * An {@link SvmClassLabel} implementation used internally for training and classification
 * in the OpenNLP SVM document categorizer.
 */
class TrainingSvmClassLabel implements SvmClassLabel {

  @Serial
  private static final long serialVersionUID = 1L;

  private final double numeric;
  private final String name;
  private final double probability;

  /**
   * Instantiates a {@link TrainingSvmClassLabel}.
   *
   * @param numeric     The numeric label value.
   * @param name        The category name.
   * @param probability The probability estimate.
   */
  TrainingSvmClassLabel(double numeric, String name, double probability) {
    this.numeric = numeric;
    this.name = name;
    this.probability = probability;
  }

  @Override
  public double getNumeric() {
    return numeric;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public double getProbability() {
    return probability;
  }

  @Override
  public int compareTo(SvmClassLabel other) {
    return Double.compare(this.probability, other.getProbability());
  }
}
