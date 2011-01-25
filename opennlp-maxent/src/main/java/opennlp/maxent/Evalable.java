/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.maxent;

import java.io.Reader;

import opennlp.model.EventCollector;
import opennlp.model.MaxentModel;

/**
 * Interface for components which use maximum entropy models and can evaluate
 * the performace of the models using the TrainEval class.
 */
public interface Evalable {

  /**
   * The outcome that should be considered a negative result. This is used for
   * computing recall. In the case of binary decisions, this would be the false
   * one.
   * 
   * @return the events that this EventCollector has gathered
   */
  public String getNegativeOutcome();

  /**
   * Returns the EventCollector that is used to collect all relevant information
   * from the data file. This is used for to test the predictions of the model.
   * Note that if some of your features are the oucomes of previous events, this
   * method will give you results assuming 100% performance on the previous
   * events. If you don't like this, use the localEval method.
   * 
   * @param r
   *          A reader containing the data for the event collector
   * @return an EventCollector
   */
  public EventCollector getEventCollector(Reader r);

  /**
   * If the -l option is selected for evaluation, this method will be called
   * rather than TrainEval's evaluation method. This is good if your features
   * includes the outcomes of previous events.
   * 
   * @param model
   *          the maxent model to evaluate
   * @param r
   *          Reader containing the data to process
   * @param e
   *          The original Evalable. Probably not relevant.
   * @param verbose
   *          a request to print more specific processing information
   */
  public void localEval(MaxentModel model, Reader r, Evalable e, boolean verbose);
}
