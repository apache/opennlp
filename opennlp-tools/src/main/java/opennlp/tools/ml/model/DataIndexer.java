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

package opennlp.tools.ml.model;

import java.io.IOException;
import java.util.Map;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Represents an indexer which compresses events in memory and performs feature selection.
 *
 * @see ObjectStream
 * @see TrainingParameters
 */
public interface DataIndexer {
  
  /**
   * @return Retrieves a 2-dimensional array whose first dimension is the event
   *         index and array this refers to contains the contexts for that event.
   */
  int[][] getContexts();

  /**
   * @return Retrieves an array indexed by the event index indicating
   *         the number of times a particular event was seen.
   */
  int[] getNumTimesEventsSeen();

  /**
   * @return Retrieves an array indicating the outcome index for each event.
   */
  int[] getOutcomeList();

  /**
   * @return Retrieves an array of predicate/context names indexed by context index.
   * These indices are the value of the array returned by {@link #getContexts()}.
   */
  String[] getPredLabels();

  /**
   * @return Retrieves an array of the count of each predicate in the events.
   */
  int[] getPredCounts();

  /**
   * @return Retrieves an array of outcome names indexed by outcome index.
   */
  String[] getOutcomeLabels();

  /**
   * @return Retrieves the values associated with each event context or
   *         {@code null} if integer values are to be used.
   */
  float[][] getValues();

  /**
   * @return Retrieves the number of total events indexed.
   */
  int getNumEvents();
  
  /**
   * Sets parameters used during the data indexing.
   *
   * @param trainParams The {@link TrainingParameters} to be used.
   * @param reportMap The {@link Map} used for reporting.
   */
  void init(TrainingParameters trainParams, Map<String,String> reportMap);

  /**
   * Performs the data indexing.
   * <p> 
   * <b>Note:</b>
   * Make sure the {@link #init(TrainingParameters, Map)} method is called first.
   * 
   * @param eventStream A {@link ObjectStream<Event>} of events used as input.
   *                    
   * @throws IOException Thrown if IO errors occurred during indexing.
   */
  void index(ObjectStream<Event> eventStream) throws IOException;
}
