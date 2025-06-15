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

import opennlp.tools.ml.perceptron.PerceptronTrainer;
import opennlp.tools.util.TrainingParameters;

/**
 * A {@link StopCriteria} implementation to identify whether the absolute
 * difference between the training accuracy of current and previous iteration is under the defined tolerance.
 */
public class IterDeltaAccuracyUnderTolerance implements StopCriteria<Double> {

  public static final String STOP = "Stopping: change in training set accuracy less than {%s}";
  private final TrainingParameters trainingParameters;

  public IterDeltaAccuracyUnderTolerance(TrainingParameters trainingParameters) {
    this.trainingParameters = trainingParameters;
  }

  @Override
  public String getMessageIfSatisfied() {
    return String.format(STOP, getTolerance());
  }

  @Override
  public boolean test(Double deltaAccuracy) {
    return StrictMath.abs(deltaAccuracy) < getTolerance();
  }

  private double getTolerance() {
    return trainingParameters != null ? trainingParameters.getDoubleParameter("Tolerance",
        PerceptronTrainer.TOLERANCE_DEFAULT) : PerceptronTrainer.TOLERANCE_DEFAULT;
  }

}
