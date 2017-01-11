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


package opennlp.tools.namefind;

import opennlp.tools.util.Span;
import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.FMeasure;

/**
 * The {@link TokenNameFinderEvaluator} measures the performance
 * of the given {@link TokenNameFinder} with the provided
 * reference {@link NameSample}s.
 *
 * @see Evaluator
 * @see TokenNameFinder
 * @see NameSample
 */
public class TokenNameFinderEvaluator extends Evaluator<NameSample> {

  private FMeasure fmeasure = new FMeasure();

  /**
   * The {@link TokenNameFinder} used to create the predicted
   * {@link NameSample} objects.
   */
  private TokenNameFinder nameFinder;

  /**
   * Initializes the current instance with the given
   * {@link TokenNameFinder}.
   *
   * @param nameFinder the {@link TokenNameFinder} to evaluate.
   * @param listeners evaluation sample listeners
   */
  public TokenNameFinderEvaluator(TokenNameFinder nameFinder,
      TokenNameFinderEvaluationMonitor ... listeners) {
    super(listeners);
    this.nameFinder = nameFinder;
  }

  /**
   * Evaluates the given reference {@link NameSample} object.
   *
   * This is done by finding the names with the
   * {@link TokenNameFinder} in the sentence from the reference
   * {@link NameSample}. The found names are then used to
   * calculate and update the scores.
   *
   * @param reference the reference {@link NameSample}.
   *
   * @return the predicted {@link NameSample}.
   */
  @Override
  protected NameSample processSample(NameSample reference) {

    if (reference.isClearAdaptiveDataSet()) {
      nameFinder.clearAdaptiveData();
    }

    Span predictedNames[] = nameFinder.find(reference.getSentence());
    Span references[] = reference.getNames();

    // OPENNLP-396 When evaluating with a file in the old format
    // the type of the span is null, but must be set to default to match
    // the output of the name finder.
    for (int i = 0; i < references.length; i++) {
      if (references[i].getType() == null) {
        references[i] = new Span(references[i].getStart(), references[i].getEnd(), "default");
      }
    }

    fmeasure.updateScores(references, predictedNames);

    return new NameSample(reference.getSentence(), predictedNames, reference.isClearAdaptiveDataSet());
  }

  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
