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

package opennlp.tools.depparse;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link DependencyEvaluationMonitor} that records the samples it is notified about,
 * for the evaluator and cross validator tests.
 */
final class RecordingMonitor implements DependencyEvaluationMonitor {

  /** The reference samples reported as correctly parsed, in order. */
  final List<DependencySample> correct = new ArrayList<>();

  /** The reference samples reported as misparsed, in order. */
  final List<DependencySample> wrong = new ArrayList<>();

  /** The predictions reported as misparsed, aligned with {@link #wrong}. */
  final List<DependencySample> predictions = new ArrayList<>();

  @Override
  public void correctlyClassified(DependencySample reference, DependencySample prediction) {
    correct.add(reference);
  }

  @Override
  public void misclassified(DependencySample reference, DependencySample prediction) {
    wrong.add(reference);
    predictions.add(prediction);
  }
}
