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

package opennlp.tools.util.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * The {@link FMeasure} is an utility class for evaluators
 * which measure precision, recall and the resulting f-measure.
 *
 * Evaluation results are the arithmetic mean of the precision
 * scores calculated for each reference sample and
 * the arithmetic mean of the recall scores calculated for
 * each reference sample.
 */
public final class FMeasure {
  /**
   * |selected| = true positives + false positives <br>
   * the count of selected (or retrieved) items.
   */
  private long selected;

  /**
   * |target| = true positives + false negatives <br>
   * the count of target (or correct) items.
   */
  private long target;

  /**
   * Storing the number of true positives found.
   */
  private long truePositive;

  /**
   * Retrieves the arithmetic mean of the precision scores calculated for each
   * evaluated sample.
   *
   * @return the arithmetic mean of all precision scores
   */
  public double getPrecisionScore() {
    return selected > 0 ? (double) truePositive / (double) selected : 0;
  }

  /**
   * Retrieves the arithmetic mean of the recall score calculated for each
   * evaluated sample.
   *
   * @return the arithmetic mean of all recall scores
   */
  public double getRecallScore() {
    return target > 0 ? (double) truePositive / (double) target : 0;
  }

  /**
   * Retrieves the f-measure score.
   *
   * f-measure = 2 * precision * recall / (precision + recall)
   * @return the f-measure or -1 if precision + recall &lt;= 0
   */
  public double getFMeasure() {

    if (getPrecisionScore() + getRecallScore() > 0) {
      return 2 * (getPrecisionScore() * getRecallScore())
          / (getPrecisionScore() + getRecallScore());
    } else {
      // cannot divide by zero, return error code
      return -1;
    }
  }

  /**
   * Updates the score based on the number of true positives and
   * the number of predictions and references.
   *
   * @param references the provided references
   * @param predictions the predicted spans
   */
  public void updateScores(final Object[] references, final Object[] predictions) {

    truePositive += countTruePositives(references, predictions);
    selected += predictions.length;
    target += references.length;
  }

  /**
   * Merge results into fmeasure metric.
   * @param measure the fmeasure
   */
  public void mergeInto(final FMeasure measure) {
    this.selected += measure.selected;
    this.target += measure.target;
    this.truePositive += measure.truePositive;
  }

  /**
   * Creates a human read-able {@link String} representation.
   * @return the results
   */
  @Override
  public String toString() {
    return "Precision: " + Double.toString(getPrecisionScore()) + "\n"
        + "Recall: " + Double.toString(getRecallScore()) + "\n" + "F-Measure: "
        + Double.toString(getFMeasure());
  }

  /**
   * This method counts the number of objects which are equal and occur in the
   * references and predictions arrays.
   * Matched items are removed from the prediction list.
   *
   * @param references
   *          the gold standard
   * @param predictions
   *          the predictions
   * @return number of true positives
   */
  static int countTruePositives(final Object[] references, final Object[] predictions) {

    List<Object> predListSpans = new ArrayList<>(predictions.length);
    Collections.addAll(predListSpans, predictions);
    int truePositives = 0;
    Object matchedItem = null;

    for (Object referenceName : references) {
      for (Object predListSpan : predListSpans) {
        if (referenceName.equals(predListSpan)) {
          matchedItem = predListSpan;
          truePositives++;
        }
      }
      if (matchedItem != null) {
        predListSpans.remove(matchedItem);
      }
    }
    return truePositives;
  }


  /**
   * Calculates the precision score for the given reference and predicted spans.
   *
   * @param references
   *          the gold standard spans
   * @param predictions
   *          the predicted spans
   * @return the precision score or NaN if there are no predicted spans
   */
  public static double precision(final Object[] references, final Object[] predictions) {

    if (predictions.length > 0) {
      return countTruePositives(references, predictions)
          / (double) predictions.length;
    } else {
      return Double.NaN;
    }
  }

  /**
   * Calculates the recall score for the given reference and predicted spans.
   *
   * @param references
   *          the gold standard spans
   * @param predictions
   *          the predicted spans
   *
   * @return the recall score or NaN if there are no reference spans
   */
  public static double recall(final Object[] references, final Object[] predictions) {

    if (references.length > 0) {
      return countTruePositives(references, predictions)
          / (double) references.length;
    } else {
      return Double.NaN;
    }
  }
}
