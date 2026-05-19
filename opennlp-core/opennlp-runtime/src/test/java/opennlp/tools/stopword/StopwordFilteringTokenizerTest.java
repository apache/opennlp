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

package opennlp.tools.stopword;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

/**
 * Unit tests for {@link StopwordFilteringTokenizer}.
 */
public class StopwordFilteringTokenizerTest {

  private static final Set<String> STOP = Set.of("the", "quick");

  /**
   * Creates a minimal {@link StopwordFilter} instance independent of any
   * concrete implementation so the test compiles even before the runtime
   * implementation is in place.
   */
  private static StopwordFilter newFilter() {
    return new StopwordFilter() {
      @Override
      public boolean isStopword(CharSequence token) {
        return token != null && STOP.contains(token.toString().toLowerCase());
      }

      @Override
      public boolean isStopword(String... tokens) {
        if (tokens == null || tokens.length == 0) {
          return false;
        }
        if (tokens.length == 1) {
          return isStopword((CharSequence) tokens[0]);
        }
        return false;
      }

      @Override
      public String[] filter(String[] tokens) {
        if (tokens == null) {
          return new String[0];
        }
        List<String> kept = new ArrayList<>(tokens.length);
        for (String t : tokens) {
          if (!isStopword((CharSequence) t)) {
            kept.add(t);
          }
        }
        return kept.toArray(new String[0]);
      }

      @Override
      public boolean isCaseSensitive() {
        return false;
      }

      @Override
      public Set<String> stopwords() {
        return Collections.unmodifiableSet(STOP);
      }
    };
  }

  @Test
  void tokenizeRemovesStopwords() {
    Tokenizer t = new StopwordFilteringTokenizer(SimpleTokenizer.INSTANCE, newFilter());

    String[] tokens = t.tokenize("The quick brown fox");

    Assertions.assertArrayEquals(new String[] {"brown", "fox"}, tokens);
  }

  @Test
  void tokenizePosKeepsOffsetsForNonStopwordSpans() {
    String input = "The quick brown fox";
    Tokenizer t = new StopwordFilteringTokenizer(SimpleTokenizer.INSTANCE, newFilter());

    Span[] spans = t.tokenizePos(input);

    Assertions.assertEquals(2, spans.length);
    Assertions.assertEquals("brown", spans[0].getCoveredText(input).toString());
    Assertions.assertEquals("fox", spans[1].getCoveredText(input).toString());
    Assertions.assertEquals(10, spans[0].getStart());
    Assertions.assertEquals(15, spans[0].getEnd());
    Assertions.assertEquals(16, spans[1].getStart());
    Assertions.assertEquals(19, spans[1].getEnd());
  }

  @Test
  void tokenizePosReturnsEmptyArrayWhenAllAreStopwords() {
    Tokenizer t = new StopwordFilteringTokenizer(SimpleTokenizer.INSTANCE, newFilter());

    Span[] spans = t.tokenizePos("the quick");

    Assertions.assertEquals(0, spans.length);
  }

  @Test
  void nullDelegateThrowsIae() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new StopwordFilteringTokenizer(null, newFilter()));
  }

  @Test
  void nullFilterThrowsIae() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new StopwordFilteringTokenizer(SimpleTokenizer.INSTANCE, null));
  }
}
