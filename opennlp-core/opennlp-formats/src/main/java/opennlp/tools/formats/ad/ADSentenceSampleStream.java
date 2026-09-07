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

package opennlp.tools.formats.ad;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.commons.Internal;
import opennlp.tools.formats.ad.ADSentenceStream.Sentence;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

/**
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class ADSentenceSampleStream implements ObjectStream<SentenceSample> {

  private final ObjectStream<ADSentenceStream.Sentence> adSentenceStream;

  private int text = -1;
  private int para = -1;
  private boolean isSameText;
  private boolean isSamePara;
  private Sentence sent;
  private final boolean isIncludeTitles;
  private boolean isTitle;

  private final char[] ptEosCharacters;

  /**
   * Initializes a new {@link ADSentenceSampleStream} from a {@link ObjectStream<String>},
   * that could be a {@link PlainTextByLineStream} object.
   *
   * @param lineStream A stream of lines as {@link String}.
   * @param includeHeadlines If {@code true} will output the sentences marked
   *                         as news headlines.
   */
  public ADSentenceSampleStream(ObjectStream<String> lineStream, boolean includeHeadlines) {
    this.adSentenceStream = new ADSentenceStream(lineStream);
    ptEosCharacters = Factory.ptEosCharacters;
    Arrays.sort(ptEosCharacters);
    this.isIncludeTitles = includeHeadlines;
  }

  /**
   * Initializes a new {@link ADSentenceSampleStream}.
   *
   * @param in The {@link InputStreamFactory} for the corpus.
   * @param charsetName The {@link java.nio.charset.Charset charset} to use
   *                    for reading of the corpus.
   * @param includeHeadlines If {@code true} will output the sentences marked
   *                         as news headlines.
   * @throws IOException Thrown if IO errors occurred.
   */
  public ADSentenceSampleStream(InputStreamFactory in, String charsetName, boolean includeHeadlines)
          throws IOException {
    this(new PlainTextByLineStream(in, charsetName), includeHeadlines);
  }

  // The Arvores Deitadas Corpus has information about texts and paragraphs.
  @Override
  public SentenceSample read() throws IOException {

    if (sent == null) {
      sent = this.adSentenceStream.read();
      updateMeta();
      if (sent == null) {
        return null;
      }
    }

    StringBuilder document = new StringBuilder();
    List<Span> sentences = new ArrayList<>();
    do {
      do {
        if (!isTitle || isIncludeTitles) {
          if (hasPunctuation(sent.text())) {
            int start = document.length();
            document.append(sent.text());
            sentences.add(new Span(start, document.length()));
            document.append(" ");
          }

        }
        sent = this.adSentenceStream.read();
        updateMeta();
      }
      while (isSamePara);
      // break; // got one paragraph!
    }
    while (isSameText);

    String doc;
    if (!document.isEmpty()) {
      doc = document.substring(0, document.length() - 1);
    } else {
      doc = document.toString();
    }

    return new SentenceSample(doc, sentences.toArray(new Span[0]));
  }

  private boolean hasPunctuation(String text) {
    text = text.trim();
    if (!text.isEmpty()) {
      char lastChar = text.charAt(text.length() - 1);
      return Arrays.binarySearch(ptEosCharacters, lastChar) >= 0;
    }
    return false;
  }

  private void updateMeta() {
    if (this.sent != null) {
      String meta = this.sent.metadata();
      int[] textAndPara = parseTextAndParagraph(meta);
      if (textAndPara == null) {
        throw new RuntimeException("Invalid metadata: " + meta);
      }
      int currentText = textAndPara[0];
      int currentPara = textAndPara[1];
      isSamePara = isSameText = false;
      if (currentText == text)
        isSameText = true;

      if (isSameText && currentPara == para)
        isSamePara = true;

      isTitle = meta.contains("title");

      text = currentText;
      para = currentPara;

    } else {
      this.isSamePara = this.isSameText = false;
    }
  }

  /**
   * Parses the text and paragraph ids from sentence metadata, which differs between corpora:
   * the text id is the ASCII digit run after any leading ASCII letters and hyphens, the
   * paragraph id is the digit run after the first {@code p=} that at least one digit follows.
   *
   * @param meta The metadata.
   * @return The text id and the paragraph id, or {@code null} if either is missing.
   */
  private int[] parseTextAndParagraph(String meta) {
    int i = 0;
    while (i < meta.length() && (isAsciiLetter(meta.charAt(i)) || meta.charAt(i) == '-')) {
      i++;
    }
    int textStart = i;
    while (i < meta.length() && isAsciiDigit(meta.charAt(i))) {
      i++;
    }
    if (i == textStart) {
      return null;
    }
    int text = Integer.parseInt(meta.substring(textStart, i));
    int from = i;
    while (from <= meta.length() - "p=".length()) {
      int p = meta.indexOf("p=", from);
      if (p == -1) {
        return null;
      }
      int paraStart = p + 2;
      int paraEnd = paraStart;
      while (paraEnd < meta.length() && isAsciiDigit(meta.charAt(paraEnd))) {
        paraEnd++;
      }
      if (paraEnd > paraStart) {
        return new int[] {text, Integer.parseInt(meta.substring(paraStart, paraEnd))};
      }
      from = p + 1;
    }
    return null;
  }

  /**
   * Tests for an ASCII letter.
   *
   * @param c The character.
   * @return {@code true} for {@code a} to {@code z} or {@code A} to {@code Z}.
   */
  private boolean isAsciiLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  /**
   * Tests for an ASCII digit.
   *
   * @param c The character.
   * @return {@code true} for {@code 0} to {@code 9}.
   */
  private boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    adSentenceStream.reset();
  }

  @Override
  public void close() throws IOException {
    adSentenceStream.close();
  }
}
