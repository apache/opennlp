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

package opennlp.tools.entitylinker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityLinkerManualExamplesTest {

  private static final String DOCUMENT_TEXT = "Visit Paris\nThen New York";

  @Test
  void testNameTokenIndexesBecomeDocumentOffsets() {
    EntityLinker<LinkedSpan<GazetteerLink>> linker = new GazetteerLinker(Map.of(
        "Paris", new GazetteerLink("Q90", "Paris"),
        "New York", new GazetteerLink("Q60", "New York")));

    List<? extends Span> results = linkEntities(DOCUMENT_TEXT, new FixedSentenceDetector(),
        WhitespaceTokenizer.INSTANCE, new FixedNameFinder(), linker);

    assertEquals(2, results.size());
    assertLinkedSpan((LinkedSpan<?>) results.get(0), 6, 11, 0, "Paris", "Q90");
    assertLinkedSpan((LinkedSpan<?>) results.get(1), 17, 25, 1, "New York", "Q60");
  }

  private static List<? extends Span> linkEntities(String documentText,
      SentenceDetector sentenceDetector, Tokenizer tokenizer,
      TokenNameFinder nameFinder, EntityLinker<? extends Span> linker) {

    Span[] sentences = sentenceDetector.sentPosDetect(documentText);
    Span[][] tokensBySentence = new Span[sentences.length][];
    Span[][] namesBySentence = new Span[sentences.length][];

    for (int i = 0; i < sentences.length; i++) {
      Span sentence = sentences[i];
      String sentenceText = sentence.getCoveredText(documentText).toString();
      Span[] relativeTokenSpans = tokenizer.tokenizePos(sentenceText);
      String[] tokenTexts = Span.spansToStrings(relativeTokenSpans, sentenceText);

      namesBySentence[i] = nameFinder.find(tokenTexts);
      tokensBySentence[i] = new Span[relativeTokenSpans.length];
      for (int j = 0; j < relativeTokenSpans.length; j++) {
        // Tokenizer offsets are sentence-relative; EntityLinker expects document offsets.
        tokensBySentence[i][j] = new Span(relativeTokenSpans[j], sentence.getStart());
      }
    }

    return linker.find(documentText, sentences, tokensBySentence, namesBySentence);
  }

  private static void assertLinkedSpan(LinkedSpan<?> span, int start, int end,
      int sentenceId, String searchTerm, String itemId) {
    assertAll(
        () -> assertEquals(start, span.getStart()),
        () -> assertEquals(end, span.getEnd()),
        () -> assertEquals("location", span.getType()),
        () -> assertEquals(sentenceId, span.getSentenceid()),
        () -> assertEquals(searchTerm, span.getSearchTerm()),
        () -> assertEquals(itemId, span.getLinkedEntries().get(0).getItemID()));
  }

  private static final class FixedSentenceDetector implements SentenceDetector {

    @Override
    public String[] sentDetect(CharSequence text) {
      return Span.spansToStrings(sentPosDetect(text), text);
    }

    @Override
    public Span[] sentPosDetect(CharSequence text) {
      return new Span[] {new Span(0, 11), new Span(12, 25)};
    }
  }

  private static final class FixedNameFinder implements TokenNameFinder {

    @Override
    public Span[] find(String[] tokens) {
      if (tokens.length == 2) {
        return new Span[] {new Span(1, 2, "location")};
      }
      return new Span[] {new Span(1, 3, "location")};
    }

    @Override
    public void clearAdaptiveData() {
      // The fixed test implementation does not retain adaptive data.
    }
  }

  private static final class GazetteerLink extends BaseLink {

    private GazetteerLink(String id, String name) {
      super(null, id, name, "location");
    }
  }

  private static final class GazetteerLinker
      implements EntityLinker<LinkedSpan<GazetteerLink>> {

    private final Map<String, GazetteerLink> entries;

    private GazetteerLinker(Map<String, GazetteerLink> entries) {
      this.entries = entries;
    }

    @Override
    public void init(EntityLinkerProperties properties) {
      // The test linker receives its entries in memory and needs no external initialization.
    }

    @Override
    public List<LinkedSpan<GazetteerLink>> find(String documentText,
        Span[] sentences, Span[][] tokensBySentence, Span[][] namesBySentence) {
      List<LinkedSpan<GazetteerLink>> results = new ArrayList<>();
      for (int i = 0; i < sentences.length; i++) {
        results.addAll(find(documentText, sentences, tokensBySentence,
            namesBySentence, i));
      }
      return results;
    }

    @Override
    public List<LinkedSpan<GazetteerLink>> find(String documentText,
        Span[] sentences, Span[][] tokensBySentence, Span[][] namesBySentence,
        int sentenceIndex) {
      List<LinkedSpan<GazetteerLink>> results = new ArrayList<>();
      Span[] tokens = tokensBySentence[sentenceIndex];

      for (Span name : namesBySentence[sentenceIndex]) {
        // Name spans contain token indexes, so map their boundaries through the token spans.
        int start = tokens[name.getStart()].getStart();
        int end = tokens[name.getEnd() - 1].getEnd();
        String searchTerm = documentText.substring(start, end);
        GazetteerLink match = entries.get(searchTerm);

        if (match != null) {
          ArrayList<GazetteerLink> matches = new ArrayList<>();
          matches.add(match);
          LinkedSpan<GazetteerLink> linkedSpan =
              new LinkedSpan<>(matches, start, end, name.getType());
          linkedSpan.setSentenceid(sentenceIndex);
          linkedSpan.setSearchTerm(searchTerm);
          results.add(linkedSpan);
        }
      }
      return results;
    }
  }
}
