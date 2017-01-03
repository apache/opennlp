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

package opennlp.tools.cmdline;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.util.Span;
import opennlp.tools.util.eval.EvaluationMonitor;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public abstract class EvaluationErrorPrinter<T> implements EvaluationMonitor<T> {

  private PrintStream printStream;

  protected EvaluationErrorPrinter(OutputStream outputStream) {
    this.printStream = new PrintStream(outputStream);
  }

  // for the sentence detector
  protected void printError(Span references[], Span predictions[],
      T referenceSample, T predictedSample, String sentence) {
    List<Span> falseNegatives = new ArrayList<>();
    List<Span> falsePositives = new ArrayList<>();

    findErrors(references, predictions, falseNegatives, falsePositives);

    if (falsePositives.size() + falseNegatives.size() > 0) {

      printSamples(referenceSample, predictedSample);

      printErrors(falsePositives, falseNegatives, sentence);

    }
  }

  // for namefinder, chunker...
  protected void printError(String id, Span references[], Span predictions[],
      T referenceSample, T predictedSample, String[] sentenceTokens) {
    List<Span> falseNegatives = new ArrayList<>();
    List<Span> falsePositives = new ArrayList<>();

    findErrors(references, predictions, falseNegatives, falsePositives);

    if (falsePositives.size() + falseNegatives.size() > 0) {

      if (id != null) {
        printStream.println("Id: {" + id + "}");
      }

      printSamples(referenceSample, predictedSample);

      printErrors(falsePositives, falseNegatives, sentenceTokens);

    }
  }

  protected void printError(Span references[], Span predictions[],
      T referenceSample, T predictedSample, String[] sentenceTokens) {
    printError(null, references, predictions, referenceSample, predictedSample, sentenceTokens);
  }

  // for pos tagger
  protected void printError(String references[], String predictions[],
      T referenceSample, T predictedSample, String[] sentenceTokens) {
    List<String> filteredDoc = new ArrayList<>();
    List<String> filteredRefs = new ArrayList<>();
    List<String> filteredPreds = new ArrayList<>();

    for (int i = 0; i < references.length; i++) {
      if (!references[i].equals(predictions[i])) {
        filteredDoc.add(sentenceTokens[i]);
        filteredRefs.add(references[i]);
        filteredPreds.add(predictions[i]);
      }
    }

    if (filteredDoc.size() > 0) {

      printSamples(referenceSample, predictedSample);

      printErrors(filteredDoc, filteredRefs, filteredPreds);

    }
  }

  // for others
  protected void printError(T referenceSample, T predictedSample) {
    printSamples(referenceSample, predictedSample);
    printStream.println();
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
    printStream.println("Errors: {");
    printStream.println("Tok: Ref | Pred");
    printStream.println("---------------");
    for (int i = 0; i < filteredDoc.size(); i++) {
      printStream.println(filteredDoc.get(i) + ": " + filteredRefs.get(i)
          + " | " + filteredPreds.get(i));
    }
    printStream.println("}\n");
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
    printStream.println("False positives: {");
    for (Span span : falsePositives) {
      printStream.println(span.getCoveredText(doc));
    }
    printStream.println("} False negatives: {");
    for (Span span : falseNegatives) {
      printStream.println(span.getCoveredText(doc));
    }
    printStream.println("}\n");
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
    printStream.println("False positives: {");
    printStream.println(print(falsePositives, toks));
    printStream.println("} False negatives: {");
    printStream.println(print(falseNegatives, toks));
    printStream.println("}\n");
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
  private <S> void printSamples(S referenceSample, S predictedSample) {
    String details = "Expected: {\n" + referenceSample + "}\nPredicted: {\n"
        + predictedSample + "}";
    printStream.println(details);
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

  public void correctlyClassified(T reference, T prediction) {
    // do nothing
  }

  public abstract void missclassified(T reference, T prediction) ;

}
