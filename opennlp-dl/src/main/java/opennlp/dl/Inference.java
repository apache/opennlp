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

package opennlp.dl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WordpieceTokenizer;

/**
 * An abstract class used by OpenNLP implementations using ONNX models.
 */
public abstract class Inference {

  protected final OrtEnvironment env;
  protected final OrtSession session;

  private final Tokenizer tokenizer;
  private final Map<String, Integer> vocabulary;
  protected InferenceOptions inferenceOptions;

  private static final int SPLIT_LENGTH = 125;

  /**
   * Instantiates a new inference class.
   * @param model The ONNX model file.
   * @param vocab The model's vocabulary file.
   * @throws OrtException Thrown if the ONNX model cannot be loaded.
   * @throws IOException Thrown if the ONNX model or vocabulary files cannot be opened or read.
   */
  public Inference(File model, File vocab, InferenceOptions inferenceOptions)
      throws OrtException, IOException {

    this.env = OrtEnvironment.getEnvironment();

    final OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
    if (inferenceOptions.isGpu()) {
      sessionOptions.addCUDA(inferenceOptions.getGpuDeviceId());
    }

    this.session = env.createSession(model.getPath(), sessionOptions);
    this.vocabulary = loadVocab(vocab);
    this.tokenizer = new WordpieceTokenizer(vocabulary.keySet());
    this.inferenceOptions = inferenceOptions;

  }

  /**
   * Tokenize the input text using the {@link WordpieceTokenizer}.
   * @param text The input text.
   * @return The input text's {@link Tokens}.
   */
  public List<Tokens> tokenize(final String text) {

    final List<Tokens> t = new LinkedList<>();

    // In this article as the paper suggests, we are going to segment the input into smaller text and feed
    // each of them into BERT, it means for each row, we will split the text in order to have some
    // smaller text (200 words long each )
    // https://medium.com/analytics-vidhya/text-classification-with-bert-using-transformers-for-long-text-inputs-f54833994dfd

    // Split the input text into 200 word chunks with 50 overlapping between chunks.
    final String[] whitespaceTokenized = text.split("\\s+");

    for (int start = 0; start < whitespaceTokenized.length; start = start + SPLIT_LENGTH) {

      // 200 word length chunk
      // Check the end do don't go past and get a StringIndexOutOfBoundsException
      int end = start + SPLIT_LENGTH;
      if (end > whitespaceTokenized.length) {
        end = whitespaceTokenized.length;
      }

      // The group is that subsection of string.
      final String group = String.join(" ", Arrays.copyOfRange(whitespaceTokenized, start, end));
      //final String group = text.substring(start, end);

      // We want to overlap each chunk by 50 words so scoot back 50 words for the next iteration.
      start = start - 50;

      // Now we can tokenize the group and continue.
      final String[] tokens = tokenizer.tokenize(group);

      final int[] ids = new int[tokens.length];

      for (int x = 0; x < tokens.length; x++) {
        ids[x] = vocabulary.get(tokens[x]);
      }

      final long[] lids = Arrays.stream(ids).mapToLong(i -> i).toArray();

      final long[] mask = new long[ids.length];
      Arrays.fill(mask, 1);

      final long[] types = new long[ids.length];
      Arrays.fill(types, 0);

      t.add(new Tokens(tokens, lids, mask, types));

    }

    return t;

  }

  /**
   * Loads a vocabulary file from disk.
   * @param vocab The vocabulary file.
   * @return A map of vocabulary words to integer IDs.
   * @throws IOException Thrown if the vocabulary file cannot be opened and read.
   */
  public Map<String, Integer> loadVocab(File vocab) throws IOException {

    final Map<String, Integer> v = new HashMap<>();

    BufferedReader br = new BufferedReader(new FileReader(vocab.getPath()));
    String line = br.readLine();
    int x = 0;

    while (line != null) {

      line = br.readLine();
      x++;

      v.put(line, x);

    }

    return v;

  }

  public static int maxIndex(double[] arr) {
    return IntStream.range(0, arr.length)
        .reduce((i, j) -> arr[i] > arr[j] ? i : j)
        .orElse(-1);
  }

  /**
   * Applies softmax to an array of values.
   * @param input An array of values.
   * @return The output array.
   */
  public double[] softmax(final double[] input) {

    final double[] t = new double[input.length];
    double sum = 0.0;

    for (int x = 0; x < input.length; x++) {
      double val = Math.exp(input[x]);
      sum += val;
      t[x] = val;
    }

    final double[] output = new double[input.length];

    for (int x = 0; x < output.length; x++) {
      output[x] = (float) (t[x] / sum);
    }

    return output;

  }

  /**
   * Converts a two-dimensional float array to doubles.
   * @param input The input array.
   * @return The converted array.
   */
  public double[][] convertFloatsToDoubles(float[][] input) {

    final double[][] outputs = new double[input.length][input[0].length];

    for (int i = 0; i < input.length; i++) {
      for (int j = 0; j < input[0].length; j++) {
        outputs[i][j] = (double) input[i][j];
      }
    }

    return outputs;

  }

  /**
   * Converts a three-dimensional float array to doubles.
   * @param input The input array.
   * @return The converted array.
   */
  public double[] convertFloatsToDoubles(float[] input) {

    final double[] output = new double[input.length];

    for (int i = 0; i < input.length; i++) {
      output[i] = input[i];
    }

    return output;

  }

}
