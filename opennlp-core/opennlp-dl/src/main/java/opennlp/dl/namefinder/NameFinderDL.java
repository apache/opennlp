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
import java.io.IOException;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import opennlp.dl.AbstractDL;
import opennlp.dl.InferenceOptions;
import opennlp.dl.SpanEnd;
import opennlp.dl.Tokens;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.WordpieceTokenizer;
import opennlp.tools.util.Span;

/**
 * An implementation of {@link TokenNameFinder} that uses ONNX models.
 *
 * @see TokenNameFinder
 * @see InferenceOptions
 */
public class NameFinderDL extends AbstractDL implements TokenNameFinder {

  public static final String I_PER = "I-PER";
  public static final String B_PER = "B-PER";
  public static final String SEPARATOR = "[SEP]";

  private static final String CHARS_TO_REPLACE = "##";

  private final SentenceDetector sentenceDetector;
  private final Map<Integer, String> ids2Labels;
  private final InferenceOptions inferenceOptions;

  /**
   * Instantiates a {@link TokenNameFinder name finder} using ONNX models.
   * 
   * @param model The ONNX model file.
   * @param vocabulary The model file's vocabulary file.
   * @param ids2Labels The mapping of ids to labels.
   * @param sentenceDetector The {@link SentenceDetector} to be used.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   */
  public NameFinderDL(File model, File vocabulary, Map<Integer, String> ids2Labels,
                      SentenceDetector sentenceDetector) throws IOException, OrtException {

    this(model, vocabulary, ids2Labels, new InferenceOptions(), sentenceDetector);

  }

  /**
   * Instantiates a {@link TokenNameFinder name finder} using ONNX models.
   *
   * @param model The ONNX model file.
   * @param vocabulary The model file's vocabulary file.
   * @param ids2Labels The mapping of ids to labels.
   * @param inferenceOptions {@link InferenceOptions} to control the inference.
   * @param sentenceDetector The {@link SentenceDetector} to be used.
   *
   * @throws OrtException Thrown if the {@code model} cannot be loaded.
   * @throws IOException Thrown if errors occurred loading the {@code model} or {@code vocabulary}.
   */
  public NameFinderDL(File model, File vocabulary, Map<Integer, String> ids2Labels,
                      InferenceOptions inferenceOptions,
                      SentenceDetector sentenceDetector) throws IOException, OrtException {

    this.env = OrtEnvironment.getEnvironment();

    final OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
    if (inferenceOptions.isGpu()) {
      sessionOptions.addCUDA(inferenceOptions.getGpuDeviceId());
    }

    this.session = env.createSession(model.getPath(), sessionOptions);
    this.ids2Labels = ids2Labels;
    this.vocab = loadVocab(vocabulary);
    this.tokenizer = new WordpieceTokenizer(vocab.keySet());
    this.inferenceOptions = inferenceOptions;
    this.sentenceDetector = sentenceDetector;

  }

  @Override
  public Span[] find(String[] input) {

    final List<Span> spans = new LinkedList<>();

    // Join the tokens here because they will be tokenized using Wordpiece during inference.
    final String text = String.join(" ", input);

    final String[] sentences = sentenceDetector.sentDetect(text);

    for (String sentence : sentences) {

      // The WordPiece tokenized text. This changes the spacing in the text.
      final List<Tokens> wordpieceTokens = tokenize(sentence);

      for (final Tokens tokens : wordpieceTokens) {

        try {

          // The inputs to the ONNX model.
          final Map<String, OnnxTensor> inputs = new HashMap<>();
          inputs.put(INPUT_IDS, OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.ids()),
              new long[] {1, tokens.ids().length}));

          if (inferenceOptions.isIncludeAttentionMask()) {
            inputs.put(ATTENTION_MASK, OnnxTensor.createTensor(env,
                LongBuffer.wrap(tokens.mask()), new long[] {1, tokens.mask().length}));
          }

          if (inferenceOptions.isIncludeTokenTypeIds()) {
            inputs.put(TOKEN_TYPE_IDS, OnnxTensor.createTensor(env,
                LongBuffer.wrap(tokens.types()), new long[] {1, tokens.types().length}));
          }

          // The outputs from the model.
          final float[][][] v = (float[][][]) session.run(inputs).get(0).getValue();

          // Find consecutive B-PER and I-PER labels and combine the spans where necessary.
          // There are also B-LOC and I-LOC tags for locations that might be useful at some point.

          // Keep track of where the last span was so when there are multiple/duplicate
          // spans we can get the next one instead of the first one each time.
          int characterStart = 0;

          final String[] toks = tokens.tokens();

          // We are looping over the vector for each word,
          // finding the index of the array that has the maximum value,
          // and then finding the token classification that corresponds to that index.
          for (int x = 0; x < v[0].length; x++) {

            final float[] arr = v[0][x];
            final int maxIndex = maxIndex(arr);
            final String label = ids2Labels.get(maxIndex);

            // TODO: Need to make sure this value is between 0 and 1?
            // Can we do thresholding without it between 0 and 1?
            final double confidence = arr[maxIndex]; // / 10;

            // Is this is the start of a person entity.
            if (B_PER.equals(label)) {

              String spanText;

              // Find the end index of the span in the array (where the label is not I-PER).
              final SpanEnd spanEnd = findSpanEnd(v, x, ids2Labels, toks);

              // If the end is -1 it means this is a single-span token.
              // If the end is != -1 it means this is a multi-span token.
              if (spanEnd.index() != -1) {

                final StringBuilder sb = new StringBuilder();

                // We have to concatenate the tokens.
                // Add each token in the array and separate them with a space.
                // We'll separate each with a single space because later we'll find the original span
                // in the text and ignore spacing between individual tokens in findByRegex().
                int end = spanEnd.index();
                for (int i = x; i <= end; i++) {

                  // If the next token starts with ##, combine it with this token.
                  if (toks[i + 1].startsWith(CHARS_TO_REPLACE)) {

                    sb.append(toks[i]).append(toks[i + 1].replace(CHARS_TO_REPLACE, ""));

                    // Append a space unless the next (next) token starts with ##.
                    if (!toks[i + 2].startsWith(CHARS_TO_REPLACE)) {
                      sb.append(" ");
                    }

                    // Skip the next token since we just included it in this iteration.
                    i++;

                  } else {

                    sb.append(toks[i].replace(CHARS_TO_REPLACE, ""));

                    // Append a space unless the next token is a period.
                    if (!".".equals(toks[i + 1])) {
                      sb.append(" ");
                    }

                  }

                }

                // This is the text of the span. We use the whole original input text and not one
                // of the splits. This gives us accurate character positions.
                spanText = findByRegex(text, sb.toString().trim()).trim();

              } else {

                // This is a single-token span so there is nothing else to do except grab the token.
                spanText = toks[x];

              }

              if (!SEPARATOR.equals(spanText)) {

                spanText = spanText.replace(CHARS_TO_REPLACE, "");

                // This ignores other potential matches in the same sentence
                // by only taking the first occurrence.
                characterStart = text.indexOf(spanText, characterStart);

                // TODO: This check should not be needed because the span was found.
                // If we aren't finding it now it's because there's a whitespace difference.
                if (characterStart != -1) {

                  final int characterEnd = characterStart + spanText.length();

                  spans.add(new Span(characterStart, characterEnd, spanText, confidence));

                  // OP-1: Only increment characterStart by one.
                  characterStart++;

                }

              }

            }

          }

        } catch (OrtException ex) {
          throw new RuntimeException("Error performing namefinder inference: " + ex.getMessage(), ex);
        }

      }

    }

    return spans.toArray(new Span[0]);

  }

  @Override
  public void clearAdaptiveData() {
    // No use in this implementation.
  }

  private SpanEnd findSpanEnd(float[][][] v, int startIndex, Map<Integer, String> id2Labels,
                              String[] tokens) {

    // -1 means there is no follow-up token, so it is a single-token span.
    int index = -1;
    int characterEnd = 0;

    // Starts at the span start in the vector.
    // Looks at the next token to see if it is an I-PER.
    // Go until the next token is something other than I-PER.
    // When the next token is not I-PER, return the previous index.

    for (int x = startIndex + 1; x < v[0].length; x++) {

      // Get the next item.
      final float[] arr = v[0][x];

      // See if the next token has an I-PER label.
      final String nextTokenClassification = id2Labels.get(maxIndex(arr));

      if (!I_PER.equals(nextTokenClassification)) {
        index = x - 1;
        break;
      }

    }

    // Find where the span ends based on the tokens.
    for (int x = 1; x <= index && x < tokens.length; x++) {
      characterEnd += tokens[x].length();
    }

    // Account for the number of spaces (that is the number of tokens).
    // (One space per token.)
    characterEnd += index - 1;

    return new SpanEnd(index, characterEnd);

  }

  private int maxIndex(float[] arr) {

    double max = Float.NEGATIVE_INFINITY;
    int index = -1;

    for (int x = 0; x < arr.length; x++) {
      if (arr[x] > max) {
        index = x;
        max = arr[x];
      }
    }

    return index;

  }

  private static String findByRegex(String text, String span) {

    final String regex = span
        .replaceAll(" ", "\\\\s+")
        .replaceAll("\\)", "\\\\)")
        .replaceAll("\\(", "\\\\(");

    final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    final Matcher matcher = pattern.matcher(text);

    if (matcher.find()) {
      return matcher.group(0);
    }

    // For some reason the regex match wasn't found. Just return the original span.
    return span;

  }

  private List<Tokens> tokenize(final String text) {

    final List<Tokens> t = new LinkedList<>();

    // In this article as the paper suggests, we are going to segment the input into smaller text and feed
    // each of them into BERT, it means for each row, we will split the text in order to have some
    // smaller text (200 words long each)
    // https://medium.com/analytics-vidhya/text-classification-with-bert-using-transformers-for-long-text-inputs-f54833994dfd

    // Split the input text into 200 word chunks with 50 overlapping between chunks.
    final String[] whitespaceTokenized = text.split("\\s+");

    for (int start = 0; start < whitespaceTokenized.length;
         start = start + inferenceOptions.getDocumentSplitSize()) {

      // 200 word length chunk
      // Check the end do don't go past and get a StringIndexOutOfBoundsException
      int end = start + inferenceOptions.getDocumentSplitSize();
      if (end > whitespaceTokenized.length) {
        end = whitespaceTokenized.length;
      }

      // The group is that subsection of string.
      final String group = String.join(" ", Arrays.copyOfRange(whitespaceTokenized, start, end));

      // We want to overlap each chunk by 50 words so scoot back 50 words for the next iteration.
      start = start - inferenceOptions.getSplitOverlapSize();

      // Now we can tokenize the group and continue.
      final String[] tokens = tokenizer.tokenize(group);

      final int[] ids = new int[tokens.length];

      for (int x = 0; x < tokens.length; x++) {
        ids[x] = vocab.get(tokens[x]);
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

}
