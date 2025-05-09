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


import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static opennlp.tools.monitoring.StopCriteria.FINISHED;

/**
 * The default implementation of {@link TrainingProgressMonitor}.
 * This publishes model training progress to the chosen logging destination.
 */
public class DefaultTrainingProgressMonitor implements TrainingProgressMonitor {

  private static final Logger logger = LoggerFactory.getLogger(DefaultTrainingProgressMonitor.class);

  /**
   * Keeps a track of whether training was already finished.
   */
  private volatile boolean isTrainingFinished;

  /**
   * An underlying list to capture training progress events.
   */
  private final List<String> progress;

  public DefaultTrainingProgressMonitor() {
    this.progress = new LinkedList<>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void finishedIteration(int iteration, int numberCorrectEvents, int totalEvents,
                                             TrainingMeasure measure, double measureValue) {
    progress.add(String.format("%s: (%s/%s) %s : %s", iteration, numberCorrectEvents, totalEvents,
        measure.getMeasureName(), measureValue));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void finishedTraining(int iterations, StopCriteria<?> stopCriteria) {
    if (!Objects.isNull(stopCriteria)) {
      progress.add(stopCriteria.getMessageIfSatisfied());
    } else {
      progress.add(String.format(FINISHED, iterations));
    }
    isTrainingFinished = true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void display(boolean clear) {
    progress.stream().forEach(logger::info);
    if (clear) {
      progress.clear();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isTrainingFinished() {
    return isTrainingFinished;
  }
}
