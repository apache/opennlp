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

package opennlp.tools.tokenize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import opennlp.tools.tokenize.Detokenizer.DetokenizationOperation;
import opennlp.tools.util.Span;

/**
 * A {@link TokenSample} is text with token spans.
 */
public class TokenSample {

  public static final String DEFAULT_SEPARATOR_CHARS = "<SPLIT>";

  private static final String separatorChars = DEFAULT_SEPARATOR_CHARS;

  private final String text;

  private final List<Span> tokenSpans;

  /**
   * Initializes the current instance.
   *
   * @param text the text which contains the tokens.
   * @param tokenSpans the spans which mark the begin and end of the tokens.
   */
  public TokenSample(String text, Span tokenSpans[]) {
    Objects.requireNonNull(tokenSpans, "tokenSpans must not be null");

    this.text = Objects.requireNonNull(text, "text must not be null");
    this.tokenSpans = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(tokenSpans)));

    for (Span tokenSpan : tokenSpans) {
      if (tokenSpan.getStart() < 0 || tokenSpan.getStart() > text.length() ||
          tokenSpan.getEnd() > text.length() || tokenSpan.getEnd() < 0) {
        throw new IllegalArgumentException("Span " + tokenSpan.toString() +
            " is out of bounds, text length: " + text.length() + "!");
      }
    }
  }

  public TokenSample(Detokenizer detokenizer, String tokens[]) {

    StringBuilder sentence = new StringBuilder();

    DetokenizationOperation[] operations = detokenizer.detokenize(tokens);

    List<Span> mergedTokenSpans = new ArrayList<>();

    for (int i = 0; i < operations.length; i++) {

      boolean isSeparateFromPreviousToken = i > 0 &&
          !isMergeToRight(operations[i - 1]) &&
          !isMergeToLeft(operations[i]);

      if (isSeparateFromPreviousToken) {
        sentence.append(' ');
      }

      int beginIndex = sentence.length();
      sentence.append(tokens[i]);
      mergedTokenSpans.add(new Span(beginIndex, sentence.length()));
    }

    text = sentence.toString();
    tokenSpans = Collections.unmodifiableList(mergedTokenSpans);
  }

  private boolean isMergeToRight(DetokenizationOperation operation) {
    return DetokenizationOperation.MERGE_TO_RIGHT.equals(operation)
        || DetokenizationOperation.MERGE_BOTH.equals(operation);
  }

  private boolean isMergeToLeft(DetokenizationOperation operation) {
    return DetokenizationOperation.MERGE_TO_LEFT.equals(operation)
        || DetokenizationOperation.MERGE_BOTH.equals(operation);
  }

  /**
   * Retrieves the text.
   */
  public String getText() {
    return text;
  }

  /**
   * Retrieves the token spans.
   */
  public Span[] getTokenSpans() {
    return tokenSpans.toArray(new Span[tokenSpans.size()]);
  }

  @Override
  public String toString() {

    StringBuilder sentence = new StringBuilder();

    int lastEndIndex = -1;
    for (Span token : tokenSpans) {

      if (lastEndIndex != -1) {

        // If there are no chars between last token
        // and this token insert the separator chars
        // otherwise insert a space

        String separator;
        if (lastEndIndex == token.getStart())
          separator = separatorChars;
        else
          separator = " ";

        sentence.append(separator);
      }

      sentence.append(token.getCoveredText(text));

      lastEndIndex = token.getEnd();
    }

    return sentence.toString();
  }

  private static void addToken(StringBuilder sample, List<Span> tokenSpans,
      String token, boolean isNextMerged) {

    int tokenSpanStart = sample.length();
    sample.append(token);
    int tokenSpanEnd = sample.length();

    tokenSpans.add(new Span(tokenSpanStart, tokenSpanEnd));

    if (!isNextMerged)
        sample.append(" ");
  }

  public static TokenSample parse(String sampleString, String separatorChars) {
    Objects.requireNonNull(sampleString, "sampleString must not be null");
    Objects.requireNonNull(separatorChars, "separatorChars must not be null");

    Span whitespaceTokenSpans[] = WhitespaceTokenizer.INSTANCE.tokenizePos(sampleString);

    // Pre-allocate 20% for newly created tokens
    List<Span> realTokenSpans = new ArrayList<>((int) (whitespaceTokenSpans.length * 1.2d));

    StringBuilder untaggedSampleString = new StringBuilder();

    for (Span whiteSpaceTokenSpan : whitespaceTokenSpans) {
      String whitespaceToken = whiteSpaceTokenSpan.getCoveredText(sampleString).toString();

      boolean wasTokenReplaced = false;

      int tokStart = 0;
      int tokEnd;
      while ((tokEnd = whitespaceToken.indexOf(separatorChars, tokStart)) > -1) {

        String token = whitespaceToken.substring(tokStart, tokEnd);

        addToken(untaggedSampleString, realTokenSpans, token, true);

        tokStart = tokEnd + separatorChars.length();
        wasTokenReplaced = true;
      }

      if (wasTokenReplaced) {
        // If the token contains the split chars at least once
        // a span for the last token must still be added
        String token = whitespaceToken.substring(tokStart);

        addToken(untaggedSampleString, realTokenSpans, token, false);
      }
      else {
        // If it does not contain the split chars at lest once
        // just copy the original token span

        addToken(untaggedSampleString, realTokenSpans, whitespaceToken, false);
      }
    }

    return new TokenSample(untaggedSampleString.toString(), realTokenSpans.toArray(
        new Span[realTokenSpans.size()]));
  }

  @Override
  public int hashCode() {
    return Objects.hash(getText(), Arrays.hashCode(getTokenSpans()));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof TokenSample) {
      TokenSample a = (TokenSample) obj;

      return getText().equals(a.getText())
          && Arrays.equals(getTokenSpans(), a.getTokenSpans());
    }

    return false;
  }
}
