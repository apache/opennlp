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

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import opennlp.tools.util.Span;

/**
 * A {@link Tokenizer} that uses a {@link BreakIterator} 
 * to identify the tokens in the input.
 *
 */
public class BreakIteratorTokenizer extends AbstractTokenizer {

  private BreakIterator breakIterator;
  
  /**
   * Creates a tokenizer using the English {@link Locale}.
   */
  public BreakIteratorTokenizer() {
    breakIterator = BreakIterator.getWordInstance(Locale.ENGLISH);
  }
  
  /**
   * Creates a tokenizer.
   * @param locale The [@link Locale} for the tokenizer.
   */
  public BreakIteratorTokenizer(Locale locale) {
    breakIterator = BreakIterator.getWordInstance(locale);
  }

  @Override
  public Span[] tokenizePos(String d) {

    List<Span> tokens = new ArrayList<>();    

    breakIterator.setText(d);
  
    int lastIndex = breakIterator.first();
      
    while (lastIndex != BreakIterator.DONE) {
        
      int firstIndex = lastIndex;
      lastIndex = breakIterator.next();

      if (lastIndex != BreakIterator.DONE && Character.isLetterOrDigit(d.charAt(firstIndex))) {
        tokens.add(new Span(firstIndex, lastIndex));            
      }
      
    }
      
    return tokens.toArray(new Span[tokens.size()]);
    
  }

}
