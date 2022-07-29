/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl.namefinder;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.dl.Inference;
import opennlp.dl.InferenceOptions;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;

/**
 * An implementation of {@link TokenNameFinder} that uses ONNX models.
 */
public class NameFinderDL implements TokenNameFinder {

  public static final String I_PER = "I-PER";
  public static final String B_PER = "B-PER";

  private final Inference inference;
  private final Map<Integer, String> ids2Labels;

  /**
   * Creates a new NameFinderDL for entity recognition using ONNX models.
   *
   * @param model     The ONNX model file.
   * @param vocab     The model's vocabulary file.
   * @param ids2Labels  A map of values and their assigned labels used to train the model.
   * @throws Exception Thrown if the models cannot be loaded.
   */
  public NameFinderDL(File model, File vocab, Map<Integer, String> ids2Labels)
          throws Exception {

    this.ids2Labels = ids2Labels;
    this.inference = new NameFinderInference(model, vocab, new InferenceOptions());

  }

  /**
   * Creates a new NameFinderDL for entity recognition using ONNX models.
   *
   * @param model     The ONNX model file.
   * @param vocab     The model's vocabulary file.
   * @param ids2Labels  A map of values and their assigned labels used to train the model.
   * @param inferenceOptions The {@link InferenceOptions} used to customize the inference process.
   * @throws Exception Thrown if the models cannot be loaded.
   */
  public NameFinderDL(File model, File vocab, Map<Integer, String> ids2Labels,
                      InferenceOptions inferenceOptions) throws Exception {

    this.ids2Labels = ids2Labels;
    this.inference = new NameFinderInference(model, vocab, inferenceOptions);

  }

  /**
   * Creates a new NameFinderDL for entity recognition using ONNX models.
   *
   * @param ids2Labels  A map of values and their assigned labels used to train the model.
   * @param inference A custom implementation of {@link Inference}.
   * @throws Exception Thrown if the models cannot be loaded.
   */
  public NameFinderDL(Map<Integer, String> ids2Labels,
                      Inference inference) throws Exception {

    this.ids2Labels = ids2Labels;
    this.inference = inference;

  }

  @Override
  public Span[] find(String[] tokens) {

    final List<Span> spans = new LinkedList<>();
    final String text = String.join(" ", tokens);

    try {

      final float[][][] vectors = (float[][][]) inference.infer(text);
      final double[][] v = inference.convertFloatsToDoubles(vectors[0]);

      // Find consecutive B-PER and I-PER labels and combine the spans where necessary.
      // There are also B-LOC and I-LOC tags for locations that might be useful at some point.

      // Keep track of where the last span was so when there are multiple/duplicate
      // spans we can get the next one instead of the first one each time.
      int characterStart = 0;

      // We are looping over the vector for each word,
      // finding the index of the array that has the maximum value,
      // and then finding the token classification that corresponds to that index.
      for (int x = 0; x < v.length; x++) {

        final double[] arr = v[x];
        final int maxIndex = Inference.maxIndex(arr);
        final String label = ids2Labels.get(maxIndex);

        final double probability = arr[maxIndex];

        if (B_PER.equalsIgnoreCase(label)) {

          // This is the start of a person entity.
          final String spanText;

          // Find the end index of the span in the array (where the label is not I-PER).
          final int endIndex = findSpanEnd(v, x, ids2Labels);

          // If the end is -1 it means this is a single-span token.
          // If the end is != -1 it means this is a multi-span token.
          if (endIndex != -1) {

            // Subtract one for the beginning token not part of the text.
            spanText = String.join(" ", Arrays.copyOfRange(tokens, x - 1, endIndex));

            spans.add(new Span(x - 1, endIndex, spanText, probability));

            x = endIndex;

          } else {

            // This is a single-token span so there is nothing else to do except grab the token.
            spanText = tokens[x];

            // Subtract one for the beginning token not part of the text.
            spans.add(new Span(x - 1, endIndex, spanText, probability));

          }

        }

      }

    } catch (Exception ex) {
      System.err.println("Error performing namefinder inference: " + ex.getMessage());
    }

    return spans.toArray(new Span[0]);

  }

  @Override
  public void clearAdaptiveData() {
    // No use for this in this implementation.
  }

  private int findSpanEnd(double[][] v, int startIndex, Map<Integer, String> id2Labels) {

    // This will be the index of the last token in the span.
    // -1 means there is no follow-up token, so it is a single-token span.
    int index = -1;

    // Starts at the span start in the vector.
    // Looks at the next token to see if it is an I-PER.
    // Go until the next token is something other than I-PER.
    // When the next token is not I-PER, return the previous index.

    for (int x = startIndex + 1; x < v[0].length; x++) {

      // Get the next item.
      final double[] arr = v[x];

      // See if the next token has an I-PER label.
      final String nextTokenClassification = id2Labels.get(Inference.maxIndex(arr));

      if (!I_PER.equalsIgnoreCase(nextTokenClassification)) {
        index = x - 1;
        break;
      }

    }

    return index;

  }

}
