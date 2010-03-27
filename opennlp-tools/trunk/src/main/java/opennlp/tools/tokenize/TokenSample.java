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
}
