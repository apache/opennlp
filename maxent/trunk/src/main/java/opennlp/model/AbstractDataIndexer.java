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

package opennlp.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Abstract class for collecting event and context counts used in training. 
 *
 */
public abstract class AbstractDataIndexer implements DataIndexer {

  private int numEvents;
  /** The integer contexts associated with each unique event. */ 
  protected int[][] contexts;
  /** The integer outcome associated with each unique event. */ 
  protected int[] outcomeList;
  /** The number of times an event occured in the training data. */
  protected int[] numTimesEventsSeen;
  /** The predicate/context names. */
  protected String[] predLabels;
  /** The names of the outcomes. */
  protected String[] outcomeLabels;
  /** The number of times each predicate occured. */
  protected int[] predCounts;

  public int[][] getContexts() {
    return contexts;
  }

  public int[] getNumTimesEventsSeen() {
    return numTimesEventsSeen;
  }

  public int[] getOutcomeList() {
    return outcomeList;
  }

  public String[] getPredLabels() {
    return predLabels;
  }

  public String[] getOutcomeLabels() {
    return outcomeLabels;
  }
  
  

  public int[] getPredCounts() {
    return predCounts;
  }

  /**
   * Sorts and uniques the array of comparable events and return the number of unique events.
   * This method will alter the eventsToCompare array -- it does an in place
   * sort, followed by an in place edit to remove duplicates.
   *
   * @param eventsToCompare a <code>ComparableEvent[]</code> value
   * @return The number of unique events in the specified list.
   * @since maxent 1.2.6
   */
  protected int sortAndMerge(List eventsToCompare, boolean sort) {
    int numUniqueEvents = 1;
    numEvents = eventsToCompare.size();
    if (sort) {
      Collections.sort(eventsToCompare);
      if (numEvents <= 1) {
        return numUniqueEvents; // nothing to do; edge case (see assertion)
      }

      ComparableEvent ce = (ComparableEvent) eventsToCompare.get(0);
      for (int i = 1; i < numEvents; i++) {
        ComparableEvent ce2 = (ComparableEvent) eventsToCompare.get(i);

        if (ce.compareTo(ce2) == 0) { 
          ce.seen++; // increment the seen count
          eventsToCompare.set(i, null); // kill the duplicate
        }
        else {
          ce = ce2; // a new champion emerges...
          numUniqueEvents++; // increment the # of unique events
        }
      }
    }
    else {
      numUniqueEvents = eventsToCompare.size();
    }
    if (sort) System.out.println("done. Reduced " + numEvents + " events to " + numUniqueEvents + ".");

    contexts = new int[numUniqueEvents][];
    outcomeList = new int[numUniqueEvents];
    numTimesEventsSeen = new int[numUniqueEvents];

    for (int i = 0, j = 0; i < numEvents; i++) {
      ComparableEvent evt = (ComparableEvent) eventsToCompare.get(i);
      if (null == evt) {
        continue; // this was a dupe, skip over it.
      }
      numTimesEventsSeen[j] = evt.seen;
      outcomeList[j] = evt.outcome;
      contexts[j] = evt.predIndexes;
      ++j;
    }
    return numUniqueEvents;
  }
  
  
  public int getNumEvents() {
    return numEvents;
  }
  
  /**
   * Updates the set of predicated and counter with the specified event contexts and cutoff. 
   * @param ec The contexts/features which occur in a event.
   * @param predicateSet The set of predicates which will be used for model building.
   * @param counter The predicate counters.
   * @param cutoff The cutoff which determines whether a predicate is included.
   */
   protected static void update(String[] ec, Set predicateSet, Map<String,Integer> counter, int cutoff) {
    for (int j=0; j<ec.length; j++) {
      Integer i = counter.get(ec[j]);
      if (i == null) {
        counter.put(ec[j], 1);
      }
      else {
        counter.put(ec[j], i+1);
      }
      if (!predicateSet.contains(ec[j]) && counter.get(ec[j]) >= cutoff) {
        predicateSet.add(ec[j]);
      }
    }
  }

  /**
   * Utility method for creating a String[] array from a map whose
   * keys are labels (Strings) to be stored in the array and whose
   * values are the indices (Integers) at which the corresponding
   * labels should be inserted.
   *
   * @param labelToIndexMap a <code>TObjectIntHashMap</code> value
   * @return a <code>String[]</code> value
   * @since maxent 1.2.6
   */
  protected static String[] toIndexedStringArray(Map<String,Integer> labelToIndexMap) {
    final String[] array = new String[labelToIndexMap.size()];
    for (String label : labelToIndexMap.keySet()) {
      array[labelToIndexMap.get(label)] = label;
    }
    return array;
  }

  public float[][] getValues() {
    return null;
  }
  
  
}
