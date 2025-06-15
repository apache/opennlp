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

import java.util.function.Predicate;

import opennlp.tools.ml.model.AbstractModel;


/**
 * Stop criteria for model training. If the predicate is met, then the training is aborted.
 *
 * @see Predicate
 * @see AbstractModel
 */
public interface StopCriteria<T extends Number> extends Predicate<T> {

  String FINISHED = "Training Finished after completing %s Iterations successfully.";

  /**
   * @return A detailed message captured upon hitting the {@link StopCriteria} during model training.
   */
  String getMessageIfSatisfied();

}
