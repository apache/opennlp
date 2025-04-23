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

import opennlp.tools.util.TrainingParameters;

import static opennlp.tools.ml.maxent.GISTrainer.LOG_LIKELIHOOD_THRESHOLD_DEFAULT;
import static opennlp.tools.ml.maxent.GISTrainer.LOG_LIKELIHOOD_THRESHOLD_PARAM;

/**
 * A {@link StopCriteria} implementation to identify whether the
 * difference between the log likelihood of current and previous iteration is under the defined threshold.
 */
public class LogLikelihoodThresholdBreached implements StopCriteria<Double> {

  public static String STOP = "Stopping: Difference between log likelihood of current" +
      " and previous iteration is less than threshold %s .";

  private final TrainingParameters trainingParameters;

  public LogLikelihoodThresholdBreached(TrainingParameters trainingParameters) {
    this.trainingParameters = trainingParameters;
  }

  @Override
  public String getMessageIfSatisfied() {
    return String.format(STOP, getThreshold());

  }

  @Override
  public boolean test(Double currVsPrevLLDiff) {
    return currVsPrevLLDiff < getThreshold();
  }

  private double getThreshold() {
    return trainingParameters != null ? trainingParameters.getDoubleParameter(LOG_LIKELIHOOD_THRESHOLD_PARAM,
        LOG_LIKELIHOOD_THRESHOLD_DEFAULT) : LOG_LIKELIHOOD_THRESHOLD_DEFAULT;
  }

}
