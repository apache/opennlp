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
import java.util.List;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.Span;

/**
 * This class is a stream filter which reads in string encoded samples and creates
 * {@link TokenSample}s out of them. The input string sample is tokenized if a
 * whitespace or the special separator chars occur.
 * <p>
 * Sample:<b>
 * "token1 token2 token3<SPLIT>token4"
 * The tokens token1 and token2 are separated by a whitespace, token3 and token3
 * are separated by the special character sequence, in this case the default
 * split sequence.
 * 
 * The sequence must be unique in the input string and is not escaped.
 */
public class TokenSampleStream implements ObjectStream<TokenSample> {
  
  public static final String DEFAULT_SEPARATOR_CHARS = "<SPLIT>";
  
  private final String separatorChars;
  
  private ObjectStream<String> sampleStrings;
  
  public TokenSampleStream(ObjectStream<String> sampleStrings, String separatorChars) {
    
    if (sampleStrings == null || separatorChars == null) {
      throw new IllegalArgumentException("parameters must not be null!");
    }
    
    this.sampleStrings = sampleStrings;
    this.separatorChars= separatorChars;
  }
  
  public TokenSampleStream(ObjectStream<String> sentences) {
    this(sentences, DEFAULT_SEPARATOR_CHARS);
  }
  
  private static void addToken(StringBuilder sample, List<Span> tokenSpans, String token, boolean isNextMerged) {
    
    int tokenSpanStart = sample.length();
    sample.append(token);
    int tokenSpanEnd = sample.length();
    
    tokenSpans.add(new Span(tokenSpanStart, tokenSpanEnd));
    
    if (!isNextMerged)
        sample.append(" ");
  }
  
  public TokenSample read() throws ObjectStreamException {
    String sampleString = sampleStrings.read();
    
    if (sampleString != null) {
      
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
    else {
      return null;
    }
  }

  public void reset() throws ObjectStreamException,
      UnsupportedOperationException {
    sampleStrings.reset();
  }
  
  public void close() throws ObjectStreamException {
    sampleStrings.close();
  }
}
