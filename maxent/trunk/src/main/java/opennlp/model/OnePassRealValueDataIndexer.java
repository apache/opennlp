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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * An indexer for maxent model data which handles cutoffs for uncommon
 * contextual predicates and provides a unique integer index for each of the
 * predicates and maintains event values.  
 * @author Tom Morton
 */
public class OnePassRealValueDataIndexer extends OnePassDataIndexer {

  float[][] values;
  
  public OnePassRealValueDataIndexer(EventStream eventStream, int cutoff, boolean sort) throws IOException {
    super(eventStream,cutoff,sort);
  }
  
  /**
   * Two argument constructor for DataIndexer.
   * @param eventStream An Event[] which contains the a list of all the Events
   *               seen in the training data.
   * @param cutoff The minimum number of times a predicate must have been
   *               observed in order to be included in the model.
   */
  public OnePassRealValueDataIndexer(EventStream eventStream, int cutoff) throws IOException {
    super(eventStream,cutoff);
  }
  
  public float[][] getValues() {
    return values;
  }

  protected int sortAndMerge(List eventsToCompare,boolean sort) {
    int numUniqueEvents = super.sortAndMerge(eventsToCompare,sort);
    values = new float[numUniqueEvents][];
    int numEvents = eventsToCompare.size();
    for (int i = 0, j = 0; i < numEvents; i++) {
      ComparableEvent evt = (ComparableEvent) eventsToCompare.get(i);
      if (null == evt) {
        continue; // this was a dupe, skip over it.
      }
      values[j++] = evt.values;
    }
    return numUniqueEvents;
  }
  
  protected List index(LinkedList<Event> events, Map<String,Integer> predicateIndex) {
    Map<String,Integer> omap = new HashMap<String,Integer>();
    
    int numEvents = events.size();
    int outcomeCount = 0;
    List eventsToCompare = new ArrayList(numEvents);
    List<Integer> indexedContext = new ArrayList<Integer>();
    
    for (int eventIndex=0; eventIndex<numEvents; eventIndex++) {
      Event ev = (Event)events.removeFirst();
      String[] econtext = ev.getContext();
      ComparableEvent ce;
      
      int ocID;
      String oc = ev.getOutcome();
      
      if (omap.containsKey(oc)) {
        ocID = omap.get(oc);
      } else {
        ocID = outcomeCount++;
        omap.put(oc, ocID);
      }
      
      for (int i=0; i<econtext.length; i++) {
        String pred = econtext[i];
        if (predicateIndex.containsKey(pred)) {
          indexedContext.add(predicateIndex.get(pred));
        }
      }
      
      //drop events with no active features
      if (indexedContext.size() > 0) {
        int[] cons = new int[indexedContext.size()];
        for (int ci=0;ci<cons.length;ci++) {
          cons[ci] = indexedContext.get(ci);
        }
        ce = new ComparableEvent(ocID, cons, ev.getValues());
        eventsToCompare.add(ce);
      }
      else {
        System.err.println("Dropped event "+ev.getOutcome()+":"+Arrays.asList(ev.getContext()));
      }
//    recycle the TIntArrayList
      indexedContext.clear();
    }
    outcomeLabels = toIndexedStringArray(omap);
    predLabels = toIndexedStringArray(predicateIndex);
    return eventsToCompare;
  }

}
