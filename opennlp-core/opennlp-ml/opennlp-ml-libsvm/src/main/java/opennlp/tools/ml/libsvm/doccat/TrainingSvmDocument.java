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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import de.hhn.mi.domain.SvmClassLabel;
import de.hhn.mi.domain.SvmDocument;
import de.hhn.mi.domain.SvmFeature;

/**
 * An {@link SvmDocument} implementation used internally for training and classification
 * in the OpenNLP SVM document categorizer.
 */
class TrainingSvmDocument implements SvmDocument {

  @Serial
  private static final long serialVersionUID = 1L;

  private final ArrayList<SvmFeature> features;
  private final ArrayList<SvmClassLabel> classLabels = new ArrayList<>();

  /**
   * Instantiates a {@link TrainingSvmDocument} with the given features.
   *
   * @param features The list of {@link SvmFeature features}. Must not be {@code null}.
   */
  TrainingSvmDocument(List<SvmFeature> features) {
    this.features = new ArrayList<>(Objects.requireNonNull(features, "features must not be null"));
  }

  @Override
  public List<SvmFeature> getSvmFeatures() {
    return features;
  }

  @Override
  public SvmClassLabel getClassLabelWithHighestProbability() {
    if (classLabels.isEmpty()) {
      return null;
    }
    return Collections.max(classLabels);
  }

  @Override
  public List<SvmClassLabel> getAllClassLabels() {
    return Collections.unmodifiableList(classLabels);
  }

  @Override
  public void addClassLabel(SvmClassLabel classLabel) {
    Objects.requireNonNull(classLabel, "classLabel must not be null");
    this.classLabels.add(classLabel);
  }
}
