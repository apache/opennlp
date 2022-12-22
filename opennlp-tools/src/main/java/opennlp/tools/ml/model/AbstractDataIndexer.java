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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;


/**
 * Abstract {@link DataIndexer} implementation for collecting
 * event and context counts used in training.
 * 
 * @see DataIndexer
 */
public abstract class AbstractDataIndexer implements DataIndexer {

  public static final String CUTOFF_PARAM = AbstractTrainer.CUTOFF_PARAM;
  public static final int CUTOFF_DEFAULT = AbstractTrainer.CUTOFF_DEFAULT;

  public static final String SORT_PARAM = "sort";
  public static final boolean SORT_DEFAULT = true;

  protected TrainingParameters trainingParameters;
  protected Map<String,String> reportMap;

  protected boolean printMessages;

  /**
   * {@inheritDoc}
   */
  @Override
  public void init(TrainingParameters indexingParameters, Map<String, String> reportMap) {
    this.reportMap = reportMap;
    if (this.reportMap == null) reportMap = new HashMap<>();
    trainingParameters = indexingParameters;

    printMessages = trainingParameters.getBooleanParameter(AbstractTrainer.VERBOSE_PARAM,
        AbstractTrainer.VERBOSE_DEFAULT);
  }

  private int numEvents;
  /** The integer contexts associated with each unique event. */
  protected int[][] contexts;
  /** The integer outcome associated with each unique event. */
  protected int[] outcomeList;
  /** The number of times an event occurred in the training data. */
  protected int[] numTimesEventsSeen;
  /** The predicate/context names. */
  protected String[] predLabels;
  /** The names of the outcomes. */
  protected String[] outcomeLabels;
  /** The number of times each predicate occurred. */
  protected int[] predCounts;

  /**
   * {@inheritDoc}
   */
  public int[][] getContexts() {
    return contexts;
  }

  /**
   * {@inheritDoc}
   */
  public int[] getNumTimesEventsSeen() {
    return numTimesEventsSeen;
  }

  /**
   * {@inheritDoc}
   */
  public int[] getOutcomeList() {
    return outcomeList;
  }

  /**
   * {@inheritDoc}
   */
  public String[] getPredLabels() {
    return predLabels;
  }

  /**
   * {@inheritDoc}
   */
  public String[] getOutcomeLabels() {
    return outcomeLabels;
  }

  /**
   * {@inheritDoc}
   */
  public int[] getPredCounts() {
    return predCounts;
  }

  /**
   * {@inheritDoc}
   */
  public int getNumEvents() {
    return numEvents;
  }
  
  /**
   * Sorts and uniques the array of comparable events and return the number of unique events.
   * This method will alter the {@code eventsToCompare} list.
   * <p>
   * It does an in place sort, followed by an in place edit to remove duplicates.
   *
   * @param eventsToCompare The {@link List<ComparableEvent>} events used as input.
   * @param sort Whether to use sorting, or not.
   *
   * @return The number of unique events in the specified list.
   * @throws InsufficientTrainingDataException Thrown if not enough events are provided
   * @since maxent 1.2.6
   */
  protected int sortAndMerge(List<ComparableEvent> eventsToCompare, boolean sort)
      throws InsufficientTrainingDataException {
    int numUniqueEvents = 1;
    numEvents = eventsToCompare.size();
    if (sort && eventsToCompare.size() > 0) {

      Collections.sort(eventsToCompare);

      ComparableEvent ce = eventsToCompare.get(0);
      for (int i = 1; i < numEvents; i++) {
        ComparableEvent ce2 = eventsToCompare.get(i);

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

    if (numUniqueEvents == 0) {
      throw new InsufficientTrainingDataException("Insufficient training data to create model.");
    }

    if (sort) display("done. Reduced " + numEvents + " events to " + numUniqueEvents + ".\n");

    contexts = new int[numUniqueEvents][];
    outcomeList = new int[numUniqueEvents];
    numTimesEventsSeen = new int[numUniqueEvents];

    for (int i = 0, j = 0; i < numEvents; i++) {
      ComparableEvent evt = eventsToCompare.get(i);
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

  /**
   * Performs the data indexing.
   * <p>
   * <b>Note:</b>
   * Make sure the {@link #init(TrainingParameters, Map)} method is called first.
   *
   * @param events A {@link ObjectStream<Event>} of events used as input.
   * @param predicateIndex A {@link Map} providing the data of a predicate index.
   *
   * @throws IOException Thrown if IO errors occurred during indexing.
   */
  protected List<ComparableEvent> index(ObjectStream<Event> events,
                                        Map<String, Integer> predicateIndex) throws IOException {
    Map<String, Integer> omap = new HashMap<>();

    List<ComparableEvent> eventsToCompare = new ArrayList<>();

    Event ev;
    while ((ev = events.read()) != null) {

      omap.putIfAbsent(ev.getOutcome(), omap.size());

      int[] cons = Arrays.stream(ev.getContext())
          .map(predicateIndex::get)
          .filter(Objects::nonNull)
          .mapToInt(i -> i).toArray();

      // drop events with no active features
      if (cons.length > 0) {
        int ocID = omap.get(ev.getOutcome());
        eventsToCompare.add(new ComparableEvent(ocID, cons, ev.getValues()));
      } else {
        display("Dropped event " + ev.getOutcome() + ":"
            + Arrays.asList(ev.getContext()) + "\n");
      }
    }
    outcomeLabels = toIndexedStringArray(omap);
    predLabels = toIndexedStringArray(predicateIndex);
    return eventsToCompare;
  }

  /**
   * Updates the set of predicates and counter with the specified event contexts and cutoff.
   *
   * @param ec The contexts/features which occur in a event.
   * @param predicateSet The set of predicates which will be used for model building.
   * @param counter The predicate counters.
   * @param cutoff The cutoff which determines whether a predicate is included.
   *
   * @deprecated Use {{@link #update(String[], Map)}}. This method will be removed after 1.8.1 release
   */
  @Deprecated
  protected static void update(String[] ec, Set<String> predicateSet,
      Map<String,Integer> counter, int cutoff) {
    for (String s : ec) {
      counter.merge(s, 1, (value, one) -> value + one);

      if (!predicateSet.contains(s) && counter.get(s) >= cutoff) {
        predicateSet.add(s);
      }
    }
  }

  /**
   * Updates the {@link Map} of predicates and counter with the specified event contexts.
   *
   * @param ec The contexts/features which occur in an event.
   * @param counter The predicate counters in form of a {@link Map}.
   */
  protected static void update(String[] ec, Map<String,Integer> counter) {
    for (String s : ec) {
      counter.merge(s, 1, (value, one) -> value + one);
    }
  }

  /**
   * Utility method for creating a {@code String[]} from a map whose
   * keys are labels (Strings) to be stored in the array and whose
   * values are the indices (Integers) at which the corresponding
   * labels should be inserted.
   *
   * @param labelToIndexMap A {@link Map} that holds labels to index positions.
   * @return The resulting {@code String[]}.
   */
  protected static String[] toIndexedStringArray(Map<String, Integer> labelToIndexMap) {
    return labelToIndexMap.entrySet().stream()
            .sorted(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey).toArray(String[]::new);
  }

  @Override
  public float[][] getValues() {
    return null;
  }

  protected void display(String s) {
    if (printMessages) {
      System.out.print(s);
    }
  }
}
