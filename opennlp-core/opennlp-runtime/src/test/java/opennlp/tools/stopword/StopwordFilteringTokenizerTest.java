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
  void tokenizePosDropsMultiWordEntry() {
    final StopwordFilter filter = DictionaryStopwordFilter.builder()
        .add("new", "york")
        .build();
    final Tokenizer t = new StopwordFilteringTokenizer(SimpleTokenizer.INSTANCE, filter);

    final String input = "I love New York city";
    final Span[] spans = t.tokenizePos(input);

    final String[] kept = new String[spans.length];
    for (int i = 0; i < spans.length; i++) {
      kept[i] = spans[i].getCoveredText(input).toString();
    }
    // "New" and "York" form a registered multi-word entry and are dropped
    // together, mirroring tokenize() and StopwordFilterStream.
    Assertions.assertArrayEquals(new String[] {"I", "love", "city"}, kept);
    Assertions.assertArrayEquals(kept, t.tokenize(input));
    // Surviving span offsets still refer to positions in the original string.
    final Span citySpan = spans[spans.length - 1];
    Assertions.assertEquals("city", input.substring(citySpan.getStart(), citySpan.getEnd()));
  }

  @Test
  void tokenizePosRemovesFinnishParadigmFormsFromBundledList() {
    // End-to-end check tying the fi.txt expansion to the Span-based path:
    // the expanded pronoun forms (minä, sinä) and the conjunction (ja) must
    // be dropped by tokenizePos exactly as by tokenize.
    final StopwordFilter fi = StopwordLists.forLanguage("fi");
    final Tokenizer t = new StopwordFilteringTokenizer(SimpleTokenizer.INSTANCE, fi);

    final String input = "minä ja sinä koira";

    Assertions.assertArrayEquals(new String[] {"koira"}, t.tokenize(input));

    final Span[] spans = t.tokenizePos(input);
    Assertions.assertEquals(1, spans.length);
    Assertions.assertEquals("koira", spans[0].getCoveredText(input).toString());
    // The surviving span offset still refers to the original input string.
    Assertions.assertEquals(13, spans[0].getStart());
    Assertions.assertEquals(18, spans[0].getEnd());
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
