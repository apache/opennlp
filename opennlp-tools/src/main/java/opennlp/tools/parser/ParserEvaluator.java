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

package opennlp.tools.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.util.Span;
import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.FMeasure;

/**
 * Class for ParserEvaluator.
 * This ParserEvaluator behaves like EVALB with no exceptions, e.g,
 * without removing punctuation tags, or equality between ADVP and PRT
 * (as in COLLINS convention). To follow parsing evaluation conventions
 * (Bikel, Collins, Charniak, etc.) as in EVALB, options are to be added
 * to the {@code ParserEvaluatorTool}.
 *
 */
public class ParserEvaluator extends Evaluator<Parse> {

  /**
   * fmeasure.
   */
  private FMeasure fmeasure = new FMeasure();
  /**
   * The parser to evaluate.
   */
  private final Parser parser;

  /**
   * Construct a parser with some evaluation monitors.
   * @param aParser
   * @param monitors the evaluation monitors
   */
  public ParserEvaluator(final Parser aParser, final ParserEvaluationMonitor... monitors) {
    super(monitors);
    this.parser = aParser;
  }

  /**
   * Obtain {@code Span}s for every parse in the sentence.
   * @param parse the parse from which to obtain the spans
   * @return an array containing every span for the parse
   */
  private static Span[] getConstituencySpans(final Parse parse) {

    Stack<Parse> stack = new Stack<>();

    if (parse.getChildCount() > 0) {
      for (Parse child : parse.getChildren()) {
        stack.push(child);
      }
    }
    List<Span> consts = new ArrayList<>();

    while (!stack.isEmpty()) {

      Parse constSpan = stack.pop();

      if (!constSpan.isPosTag()) {
        Span span = constSpan.getSpan();
        consts.add(new Span(span.getStart(), span.getEnd(), constSpan.getType()));

        for (Parse child : constSpan.getChildren()) {
          stack.push(child);
        }
      }
    }

    return consts.toArray(new Span[consts.size()]);
  }

  @Override
  protected final Parse processSample(final Parse reference) {
    List<String> tokens = new ArrayList<>();
    for (Parse token : reference.getTokenNodes()) {
      tokens.add(token.getSpan().getCoveredText(reference.getText()).toString());
    }

    Parse[] predictions = ParserTool.parseLine(String.join(" ", tokens), parser, 1);

    Parse prediction = null;
    if (predictions.length > 0) {
      prediction = predictions[0];
    }

    fmeasure.updateScores(getConstituencySpans(reference), getConstituencySpans(prediction));

    return prediction;
  }

  /**
   * It returns the fmeasure result.
   * @return the fmeasure value
   */
  public final FMeasure getFMeasure() {
    return fmeasure;
  }
}
