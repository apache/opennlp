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

/** Object which compresses events in memory and performs feature selection.
 */
public interface DataIndexer {
  /**
   * Returns the array of predicates seen in each event.
   * @return a 2-D array whose first dimension is the event index and array this refers to contains
   *     the contexts for that event.
   */
  int[][] getContexts();

  /**
   * Returns an array indicating the number of times a particular event was seen.
   * @return an array indexed by the event index indicating the number of times a particular event was seen.
   */
  int[] getNumTimesEventsSeen();

  /**
   * Returns an array indicating the outcome index for each event.
   * @return an array indicating the outcome index for each event.
   */
  int[] getOutcomeList();

  /**
   * Returns an array of predicate/context names.
   * @return an array of predicate/context names indexed by context index.  These indices are the
   *     value of the array returned by <code>getContexts</code>.
   */
  String[] getPredLabels();

  /**
   * Returns an array of the count of each predicate in the events.
   * @return an array of the count of each predicate in the events.
   */
  int[] getPredCounts();

  /**
   * Returns an array of outcome names.
   * @return an array of outcome names indexed by outcome index.
   */
  String[] getOutcomeLabels();

  /**
   * Returns the values associated with each event context or null if integer values are to be used.
   * @return the values associated with each event context.
   */
  float[][] getValues();

  /**
   * Returns the number of total events indexed.
   * @return The number of total events indexed.
   */
  int getNumEvents();
  
  /**
   * Sets parameters used during the data indexing.
   * @param trainParams {@link TrainingParameters}
   */
  void init(TrainingParameters trainParams,Map<String,String> reportMap);

  /**
   * Performs the data indexing. Make sure the init(...) method is called first.
   * 
   * @param eventStream {@link ObjectStream<Event>}
   */
  void index(ObjectStream<Event> eventStream) throws IOException;
}
