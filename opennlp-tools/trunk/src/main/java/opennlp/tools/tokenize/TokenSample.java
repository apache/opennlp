/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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
import java.util.List;

import opennlp.tools.util.Span;

/**
 * A {@link TokenSample} is text with token spans.
 */
public class TokenSample {

  private String text;

  private Span tokenSpans[];

  /**
   * Initializes the current instance.
   *
   * @param text the text which contains the tokens.
   * @param tokenSpans the spans which mark the begin and end of the tokens.
   */
  public TokenSample(String text, Span tokenSpans[]) {
    
    if (text == null)
      throw new IllegalArgumentException("text must not be null!");
    
    if (tokenSpans == null)
      throw new IllegalArgumentException("tokenSpans must not be null! ");
    
    this.text = text;
    this.tokenSpans = tokenSpans;

    for (int i = 0; i < tokenSpans.length; i++) {
      if (tokenSpans[i].getStart() < 0 || tokenSpans[i].getStart() > text.length() ||
          tokenSpans[i].getEnd() > text.length() || tokenSpans[i].getEnd() < 0) {
        throw new IllegalArgumentException("Span " + tokenSpans[i].toString() +
            " is out of bounds!");
      }
    }
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
    return tokenSpans;
  }

  @Override
  public String toString() {
    return getText();
  }
  
  private static void addToken(StringBuilder sample, List<Span> tokenSpans, String token, boolean isNextMerged) {
    
    int tokenSpanStart = sample.length();
    sample.append(token);
    int tokenSpanEnd = sample.length();
    
    tokenSpans.add(new Span(tokenSpanStart, tokenSpanEnd));
    
    if (!isNextMerged)
        sample.append(" ");
  }
  
  public static TokenSample parse(String sampleString, String separatorChars) {
    
    if (sampleString == null || separatorChars == null)
        throw new IllegalArgumentException("arguments must not be null!");
    
    Span whitespaceTokenSpans[] = WhitespaceTokenizer.INSTANCE.tokenizePos(sampleString);
    
    // Pre-allocate 20% for newly created tokens
    List<Span> realTokenSpans = new ArrayList<Span>((int) (whitespaceTokenSpans.length * 1.2d));
    
    StringBuilder untaggedSampleString = new StringBuilder();
    
    for (Span whiteSpaceTokenSpan : whitespaceTokenSpans) {
      String whitespaceToken = whiteSpaceTokenSpan.getCoveredText(sampleString);
      
      boolean wasTokenReplaced = false;
      
      int tokStart = 0;
      int tokEnd = -1;
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
    
    return new TokenSample(untaggedSampleString.toString(), (Span[]) realTokenSpans.toArray(
        new Span[realTokenSpans.size()]));
  }
}
