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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * The {@link Evaluator} is an abstract base class for evaluators.
 *
 * Evaluation results are the arithmetic mean of the
 * scores calculated for each reference sample.
 */
public abstract class Evaluator<T> {
  
  private final boolean isPrintError;
  
  public Evaluator(boolean printError) {
    isPrintError = printError;
  }

  public Evaluator() {
    isPrintError = false;
  }

  /**
   * Evaluates the given reference object.
   *
   * The implementation has to update the score after every invocation and invoke
   * printErrors(...) if requested by user.
   *
   * @param sample the sample to be evaluated
   */
  public abstract void evaluateSample(T sample);

  /**
   * Reads all sample objects from the stream
   * and evaluates each sample object with
   * {@link #evaluateSample(Object)} method.
   *
   * @param samples the stream of reference which
   * should be evaluated.
   */
  public void evaluate(ObjectStream<T> samples) throws IOException {
    T sample;
    while ((sample = samples.read()) != null) {
      evaluateSample(sample);
    }
  }
  
  /**
   * Extensions of this class should check this property to check if should call printErrors method. 
   * @return true if to call printErrors method.
   */
  protected final boolean isPrintError() {
    return isPrintError;
  }

  /**
   * Prints a report informing errors found in this sample
   * 
   * This method should be called by implementations of
   * {@link #evaluateSample(Object)}
   * 
   * @param references
   *          the reference Span
   * @param predictions
   *          the predicted Span
   * @param referenceSample
   *          the reference sample
   * @param predictedSample
   *          the predicted sample
   * @param doc
   *          the document
   */
  protected void printErrors(Span references[], Span predictions[],
      T referenceSample, T predictedSample, String doc) {

    List<Span> falseNegatives = new ArrayList<Span>();
    List<Span> falsePositives = new ArrayList<Span>();

    findErrors(references, predictions, falseNegatives, falsePositives);

    if (falsePositives.size() + falseNegatives.size() > 0) {

      printSamples(referenceSample, predictedSample);

      printErrors(falsePositives, falseNegatives, doc);

    }
  }

  /**
   * Prints a report informing errors found in this sample
   * 
   * This method should be called by implementations of
   * {@link #evaluateSample(Object)}
   * 
   * @param references
   *          the reference Span
   * @param predictions
   *          the predicted Span
   * @param referenceSample
   *          the reference sample
   * @param predictedSample
   *          the predicted sample
   * @param doc
   *          the document
   */
  protected void printErrors(Span references[], Span predictions[],
      T referenceSample, T predictedSample, String[] doc) {

    List<Span> falseNegatives = new ArrayList<Span>();
    List<Span> falsePositives = new ArrayList<Span>();

    findErrors(references, predictions, falseNegatives, falsePositives);

    if (falsePositives.size() + falseNegatives.size() > 0) {

      printSamples(referenceSample, predictedSample);

      printErrors(falsePositives, falseNegatives, doc);

    }

  }

  /**
   * Prints a report informing errors found in this sample
   * 
   * This method should be called by implementations of
   * {@link #evaluateSample(Object)}
   * 
   * @param references
   *          the reference tags
   * @param predictions
   *          the predicted tags
   * @param referenceSample
   *          the reference sample
   * @param predictedSample
   *          the predicted sample
   * @param doc
   *          the document
   */
  protected void printErrors(String references[], String predictions[],
      T referenceSample, T predictedSample, String[] doc) {

    List<String> filteredDoc = new ArrayList<String>();
    List<String> filteredRefs = new ArrayList<String>();
    List<String> filteredPreds = new ArrayList<String>();

    for (int i = 0; i < references.length; i++) {
      if (!references[i].equals(predictions[i])) {
        filteredDoc.add(doc[i]);
        filteredRefs.add(references[i]);
        filteredPreds.add(predictions[i]);
      }
    }

    if (filteredDoc.size() > 0) {

      printSamples(referenceSample, predictedSample);

      printErrors(filteredDoc, filteredRefs, filteredPreds);

    }
  }

  /**
   * Auxiliary method to print tag errors
   * 
   * @param filteredDoc
   *          the document tokens which were tagged wrong
   * @param filteredRefs
   *          the reference tags
   * @param filteredPreds
   *          the predicted tags
   */
  private void printErrors(List<String> filteredDoc, List<String> filteredRefs,
      List<String> filteredPreds) {
    System.err.println("Errors: {");
    System.err.println("Tok: Ref | Pred");
    System.err.println("---------------");
    for (int i = 0; i < filteredDoc.size(); i++) {
      System.err.println(filteredDoc.get(i) + ": " + filteredRefs.get(i)
          + " | " + filteredPreds.get(i));
    }
    System.err.println("}\n");
  }

  /**
   * Auxiliary method to print span errors
   * 
   * @param falsePositives
   *          false positives span
   * @param falseNegatives
   *          false negative span
   * @param doc
   *          the document text
   */
  private void printErrors(List<Span> falsePositives,
      List<Span> falseNegatives, String doc) {
    System.err.println("False positives: {");
    for (Span span : falsePositives) {
      System.err.println(span.getCoveredText(doc));
    }
    System.err.println("} False negatives: {");
    for (Span span : falseNegatives) {
      System.err.println(span.getCoveredText(doc));
    }
    System.err.println("}\n");
  }

  /**
   * Auxiliary method to print span errors
   * 
   * @param falsePositives
   *          false positives span
   * @param falseNegatives
   *          false negative span
   * @param toks
   *          the document tokens
   */
  private void printErrors(List<Span> falsePositives,
      List<Span> falseNegatives, String[] toks) {
    System.err.println("False positives: {");
    System.err.println(print(falsePositives, toks));
    System.err.println("} False negatives: {");
    System.err.println(print(falseNegatives, toks));
    System.err.println("}\n");
  }

  /**
   * Auxiliary method to print spans
   * 
   * @param spans
   *          the span list
   * @param toks
   *          the tokens array
   * @return the spans as string
   */
  private String print(List<Span> spans, String[] toks) {
    return Arrays.toString(Span.spansToStrings(
        spans.toArray(new Span[spans.size()]), toks));
  }

  /**
   * Auxiliary method to print expected and predicted samples.
   * 
   * @param referenceSample
   *          the reference sample
   * @param predictedSample
   *          the predicted sample
   */
  private void printSamples(T referenceSample, T predictedSample) {
    String details = "Expected: {\n" + referenceSample + "}\nPredicted: {\n"
        + predictedSample + "}";
    System.err.println(details);
  }

  /**
   * Outputs falseNegatives and falsePositives spans from the references and
   * predictions list.
   * 
   * @param references
   * @param predictions
   * @param falseNegatives
   *          [out] the false negatives list
   * @param falsePositives
   *          [out] the false positives list
   */
  private void findErrors(Span references[], Span predictions[],
      List<Span> falseNegatives, List<Span> falsePositives) {

    falseNegatives.addAll(Arrays.asList(references));
    falsePositives.addAll(Arrays.asList(predictions));

    for (int referenceIndex = 0; referenceIndex < references.length; referenceIndex++) {

      Span referenceName = references[referenceIndex];

      for (int predictedIndex = 0; predictedIndex < predictions.length; predictedIndex++) {
        if (referenceName.equals(predictions[predictedIndex])) {
          // got it, remove from fn and fp
          falseNegatives.remove(referenceName);
          falsePositives.remove(predictions[predictedIndex]);
        }
      }
    }
  }
  
}
