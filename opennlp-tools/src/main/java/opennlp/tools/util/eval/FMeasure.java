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

	/** |selected| = true positives + false positives <br>
	 * the count of selected (or retrieved) items  */
	private long selected;
	
	/** |target| = true positives + false negatives <br>
	 * the count of target (or correct) items */
	private long target;
	
	private long truePositive;
  
  /**
   * Retrieves the arithmetic mean of the precision scores
   * calculated for each evaluated sample.
   *
   * @return the arithmetic mean of all precision scores
   */
  public double getPrecisionScore() {
    return selected > 0 ? (double)truePositive / (double)selected : 0;
  }

  /**
   * Retrieves the arithmetic mean of the recall score
   * calculated for each evaluated sample.
   *
   * @return the arithmetic mean of all recall scores
   */
  public double getRecallScore() {
    return target > 0 ? (double)truePositive / (double)target : 0;
  }
  
  /**
   * Retrieves the f-measure score.
   *
   * f-measure = 2 * precision * recall / (precision + recall)
   *
   * @return the f-measure or -1 if precision + recall <= 0
   */
  public double getFMeasure() {

    if (getPrecisionScore() + getRecallScore() > 0) {
      return 2 * (getPrecisionScore() * getRecallScore()) /
          (getPrecisionScore() + getRecallScore());
    }
    else {
      // cannot divide by zero, return error code
      return -1;
    }
  }
 
  public void updateScores(Object references[], Object predictions[]) {
	  
	  truePositive += countTruePositives(references, predictions);
	  selected += predictions.length;
	  target += references.length;
  }
  
  public void mergeInto(FMeasure measure) {
    this.selected += measure.selected;
    this.target += measure.target;
    this.truePositive += measure.truePositive;
  }
  
  /**
   * Creates a human read-able {@link String} representation.
   */
  @Override
  public String toString() {
    return "Precision: " + Double.toString(getPrecisionScore()) + "\n" +
        "Recall: " + Double.toString(getRecallScore()) + "\n" +
        "F-Measure: " + Double.toString(getFMeasure());
  }
  
  /**
   * This method counts the number of objects which are equal and
   * occur in the references and predictions arrays.
   *
   * These are the number of true positives.
   *
   * @param references the gold standard
   * @param predictions the predictions
   *
   * @return number of true positives
   */
  static int countTruePositives(Object references[],
      Object predictions[]) {

    int truePositives = 0;

    // Note: Maybe a map should be used to improve performance
    for (int referenceIndex = 0; referenceIndex < references.length;
        referenceIndex++) {

      Object referenceName = references[referenceIndex];

      for (int predictedIndex = 0; predictedIndex < predictions.length;
          predictedIndex++) {
        if (referenceName.equals(predictions[predictedIndex])) {
          truePositives++;
        }
      }
    }

    return truePositives;
  }

  /**
   * Calculates the precision score for the given reference and
   * predicted spans.
   *
   * @param references the gold standard spans
   * @param predictions the predicted spans
   *
   * @return the precision score or NaN if there are no predicted spans
   */
  public static double precision(Object references[], Object predictions[]) {

    if (predictions.length > 0) {
      return countTruePositives(references, predictions) /
          (double) predictions.length;
    }
    else {
      return Double.NaN;
    }
  }

  /**
   * Calculates the recall score for the given reference and
   * predicted spans.
   *
   * @param references the gold standard spans
   * @param predictions the predicted spans
   *
   * @return the recall score or NaN if there are no reference spans
   */
  public static double recall(Object references[], Object predictions[]) {

    if (references.length > 0) {
      return countTruePositives(references, predictions) /
          (double) references.length;
    }
    else {
        return Double.NaN;
    }
  }
}
