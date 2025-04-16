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

package opennlp.tools.monitoring;

/**
 * An interface to capture Training Progress of a {@link opennlp.tools.ml.model.AbstractModel}.
 */

public interface TrainingProgressMonitor {

  /**
   * Captures the Iteration progress.
   *
   * @param iteration           The completed iteration number.
   * @param numberCorrectEvents Number of correctly predicted events in this iteration.
   * @param totalEvents         Total count of events processed in this iteration.
   * @param measure             Measure used to quantify training success.
   * @param measureValue        measure value corresponding to the applicable {@link TrainingMeasure}.
   */
  void finishedIteration(int iteration, int numberCorrectEvents, int totalEvents,
                         TrainingMeasure measure, double measureValue);

  /**
   * Captures the Training completion progress.
   *
   * @param iterations   Total number of iterations configured for training.
   * @param stopCriteria Exit criteria for training.
   */
  void finishedTraining(int iterations, StopCriteria stopCriteria);

  /**
   * Checks whether the training has finished.   *
   * @return A boolean value to identify whether the training has finished.
   */
  boolean isTrainingFinished();

  /**
   * Display the Training progress and clear the underlying data structure
   * used to store the training progress events. Callers of this method can invoke it periodically
   * during training progress to avoid storing too much progress related data.
   */
  void displayAndClear();
}
