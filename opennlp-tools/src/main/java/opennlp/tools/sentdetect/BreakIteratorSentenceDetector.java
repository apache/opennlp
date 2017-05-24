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

package opennlp.tools.sentdetect;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import opennlp.tools.util.Span;

/**
 * An implementation of {@link SentenceDetector} that uses
 * a {@link BreakIterator} to identify the sentences in
 * the input.
 *
 */
public class BreakIteratorSentenceDetector implements SentenceDetector {

  private BreakIterator breakIterator;

  /**
   * Creates a sentence detector using the English {@link Locale}.
   */
  public BreakIteratorSentenceDetector() {
    breakIterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
  }
  
  /**
   * Creates a sentence detector.
   * @param locale The {@link Locale} for the sentence detector.
   */
  public BreakIteratorSentenceDetector(Locale locale) {
    breakIterator = BreakIterator.getSentenceInstance(locale);
  }

  @Override
  public String[] sentDetect(String s) {
    return Span.spansToStrings(sentPosDetect(s), s);
  }

  @Override
  public Span[] sentPosDetect(String s) {

    List<Span> sentences = new ArrayList<>();

    breakIterator.setText(s);
    
    int lastIndex = breakIterator.first();
    while (lastIndex != BreakIterator.DONE) {
      int firstIndex = lastIndex;
      lastIndex = breakIterator.next();

      if (lastIndex != BreakIterator.DONE) {
        sentences.add(new Span(firstIndex, lastIndex));
      }   
    }

    return sentences.toArray(new Span[sentences.size()]);
  }
}
