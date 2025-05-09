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

package opennlp.tools.util;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.monitoring.StopCriteria;
import opennlp.tools.monitoring.TrainingProgressMonitor;

/**
 * Configuration used for {@link AbstractModel} training.
 * @param progMon {@link TrainingProgressMonitor} used to monitor the training progress.
 * @param stopCriteria {@link StopCriteria} used to abort training when the criteria is met.
 */
public record TrainingConfiguration(TrainingProgressMonitor progMon, StopCriteria<Double> stopCriteria) {}
