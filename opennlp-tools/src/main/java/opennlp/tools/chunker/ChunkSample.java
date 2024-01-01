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

package opennlp.tools.chunker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import opennlp.tools.commons.Sample;
import opennlp.tools.util.Span;

/**
 * Class for holding chunks for a single unit of text.
 */
public class ChunkSample implements Sample {

  private static final long serialVersionUID = -3092850871020409815L;
  private final List<String> sentence;
  private final List<String> tags;
  private final List<String> preds;

  /**
   * Initializes a {@link ChunkSample} instance.
   *
   * @param sentence
   *          The training sentence.
   * @param tags
   *          The POS tags for the {@code sentence}.
   * @param preds
   *          The chunk tags in B-* I-* notation.
   */
  public ChunkSample(String[] sentence, String[] tags, String[] preds) {

    this(Arrays.asList(sentence), Arrays.asList(tags), Arrays.asList(preds));
  }

  /**
   * Initializes a {@link ChunkSample} instance.
   *
   * @param sentence
   *          The training sentence.
   * @param tags
   *          The POS tags for the {@code sentence}.
   * @param preds
   *          The chunk tags in B-* I-* notation.
   */
  public ChunkSample(List<String> sentence, List<String> tags, List<String> preds) {

    validateArguments(sentence.size(), tags.size(), preds.size());

    this.sentence = List.copyOf(sentence);
    this.tags = List.copyOf(tags);
    this.preds = List.copyOf(preds);
  }

  /**
   * @return Retrieves the training sentence.
   */
  public String[] getSentence() {
    return sentence.toArray(new String[0]);
  }

  /**
   * @return Retrieves the POS Tags for the sentence.
   */
  public String[] getTags() {
    return tags.toArray(new String[0]);
  }

  /**
   * @return Retrieves the chunk tags in B-* I-* notation.
   */
  public String[] getPreds() {
    return preds.toArray(new String[0]);
  }

  /**
   * @return the phrases as an array of spans.
   */
  public Span[] getPhrasesAsSpanList() {
    return phrasesAsSpanList(getSentence(), getTags(), getPreds());
  }

  /**
   * Create arrays of {@link Span spans} of phrases.
   *
   * @param aSentence
   *          The training sentence.
   * @param aTags
   *          The POS tags for the {@code sentence}.
   * @param aPreds
   *          The chunk tags in B-* I-* notation.
   *
   * @return the phrases as an array of spans
   */
  public static Span[] phrasesAsSpanList(String[] aSentence, String[] aTags, String[] aPreds) {

    validateArguments(aSentence.length, aTags.length, aPreds.length);

    // initialize with the list maximum size
    List<Span> phrases = new ArrayList<>(aSentence.length);
    String startTag = "";
    int startIndex = 0;
    boolean foundPhrase = false;

    for (int ci = 0, cn = aPreds.length; ci < cn; ci++) {
      String pred = aPreds[ci];
      if (pred.startsWith("B-")
          || !pred.equals("I-" + startTag) && !pred.equals("O")) { // start
        if (foundPhrase) { // handle the last
          phrases.add(new Span(startIndex, ci, startTag));
        }
        startIndex = ci;
        startTag = pred.substring(2);
        foundPhrase = true;
      } else if (pred.equals("I-" + startTag)) { // middle
        // do nothing
      } else if (foundPhrase) { // end
        phrases.add(new Span(startIndex, ci, startTag));
        foundPhrase = false;
        startTag = "";
      }
    }
    if (foundPhrase) { // leftover
      phrases.add(new Span(startIndex, aPreds.length, startTag));
    }

    return phrases.toArray(new Span[0]);
  }

  private static void validateArguments(int sentenceSize, int tagsSize, int predsSize)
      throws IllegalArgumentException {
    if (sentenceSize != tagsSize || tagsSize != predsSize)
      throw new IllegalArgumentException(
          "All arrays must have the same length: " +
              "sentenceSize: " + sentenceSize +
              ", tagsSize: " + tagsSize +
              ", predsSize: " + predsSize + "!");
  }

  /**
   * Creates a nice to read string for the phrases formatted as following: <br>
   * <code>
   * [NP Rockwell_NNP ] [VP said_VBD ] [NP the_DT agreement_NN ] [VP calls_VBZ ] [SBAR for_IN ]
   * [NP it_PRP ] [VP to_TO supply_VB ] [NP 200_CD additional_JJ so-called_JJ shipsets_NNS ]
   * [PP for_IN ] [NP the_DT planes_NNS ] ._.
   * </code>
   *
   * @return A nice to read string representation of the chunk phases
   */
  public String nicePrint() {

    Span[] spans = getPhrasesAsSpanList();

    StringBuilder result = new StringBuilder(" ");

    for (int tokenIndex = 0; tokenIndex < sentence.size(); tokenIndex++) {
      for (Span span : spans) {
        if (span.getStart() == tokenIndex) {
          result.append("[").append(span.getType()).append(" ");
        }

        if (span.getEnd() == tokenIndex) {
          result.append("]").append(' ');
        }
      }

      result.append(sentence.get(tokenIndex)).append("_").append(tags.get(tokenIndex)).append(' ');
    }

    if (sentence.size() > 1)
      result.setLength(result.length() - 1);

    for (Span span : spans) {
      if (span.getEnd() == sentence.size()) {
        result.append(']');
      }
    }

    return result.toString();
  }

  @Override
  public String toString() {

    StringBuilder chunkString = new StringBuilder();

    for (int ci = 0; ci < preds.size(); ci++) {
      chunkString.append(sentence.get(ci)).append(" ").append(tags.get(ci))
          .append(" ").append(preds.get(ci)).append("\n");
    }
    return chunkString.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(getSentence()),
        Arrays.hashCode(getTags()), Arrays.hashCode(getPreds()));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof ChunkSample a) {

      return Arrays.equals(getSentence(), a.getSentence())
          && Arrays.equals(getTags(), a.getTags())
          && Arrays.equals(getPreds(), a.getPreds());
    }

    return false;
  }
}
