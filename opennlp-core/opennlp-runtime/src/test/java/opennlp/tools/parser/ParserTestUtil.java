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

package opennlp.tools.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.parser.lang.en.HeadRules;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;

public class ParserTestUtil {

  private static final String TEST_SENTENCE = "Eric is testing.";

  /**
   * @return A fresh, unparsed {@link Parse} over the tokens of a short English test sentence.
   */
  public static Parse createTestSentence() {
    return Parse.createFromTokens(WhitespaceTokenizer.INSTANCE.tokenize(TEST_SENTENCE));
  }

  /**
   * Asserts that {@code actual} holds the same bracketings with the same probabilities
   * as {@code expected}, in the same order.
   *
   * @param expected The reference parses.
   * @param actual The parses under test.
   */
  public static void assertSameParses(Parse[] expected, Parse[] actual) {
    Assertions.assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      Assertions.assertEquals(expected[i].toStringPennTreebank(), actual[i].toStringPennTreebank());
      Assertions.assertEquals(expected[i].getProb(), actual[i].getProb());
    }
  }

  public static HeadRules createTestHeadRules() throws IOException {
    try (InputStream headRulesIn = ParserTestUtil.class.getResourceAsStream(
            "/opennlp/tools/parser/en_head_rules");
         Reader reader = new BufferedReader(new InputStreamReader(headRulesIn, StandardCharsets.UTF_8))) {
      
      return new HeadRules(reader);
    }

  }

  public static ObjectStream<Parse> openTestTrainingData()
      throws IOException {

    ObjectStream<Parse> resetableSampleStream = new ObjectStream<>() {

      private ObjectStream<Parse> samples;

      @Override
      public void close() throws IOException {
        samples.close();
      }

      @Override
      public Parse read() throws IOException {
        return samples.read();
      }

      @Override
      public void reset() throws IOException {
        try {
          if (samples != null) {
            samples.close();
          }
          InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
                  "/opennlp/tools/parser/parser.train");
          samples = new ParseSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
        } catch (UnsupportedEncodingException e) {
          // Should never happen
          Assertions.fail(e.getMessage());
        }
      }
    };

    resetableSampleStream.reset();

    return resetableSampleStream;
  }

  /**
   * Returns a copy of {@code model} whose {@link ParserModel#getParserTaggerModel()} and
   * {@link ParserModel#getParserChunkerModel()} fail, so a test can prove that a parser
   * built from it never constructs the model's own tagger or chunker.
   *
   * @param model The trained model to copy the build, check, attach and head rule artifacts from.
   * @return A model that fails on access to its tagger or chunker model.
   */
  public static ParserModel withInaccessibleComponentModels(ParserModel model) {
    return new ParserModel(model.getLanguage(), model.getBuildModel(), model.getCheckModel(),
        model.getAttachModel(), model.getParserTaggerModel(), model.getParserChunkerModel(),
        model.getHeadRules(), model.getParserType()) {

      @Override
      public opennlp.tools.postag.POSModel getParserTaggerModel() {
        throw new AssertionError("the model's tagger model must not be accessed");
      }

      @Override
      public opennlp.tools.chunker.ChunkerModel getParserChunkerModel() {
        throw new AssertionError("the model's chunker model must not be accessed");
      }
    };
  }

  /**
   * A {@link POSTagger} that delegates every call and counts them.
   */
  public static final class CountingTagger implements POSTagger {

    private final POSTagger delegate;
    private int calls;

    public CountingTagger(POSTagger delegate) {
      this.delegate = delegate;
    }

    /**
     * @return The number of calls made to this tagger so far.
     */
    public int calls() {
      return calls;
    }

    @Override
    public String[] tag(String[] sentence) {
      calls++;
      return delegate.tag(sentence);
    }

    @Override
    public String[] tag(String[] sentence, Object[] additionalContext) {
      calls++;
      return delegate.tag(sentence, additionalContext);
    }

    @Override
    public Sequence[] topKSequences(String[] sentence) {
      calls++;
      return delegate.topKSequences(sentence);
    }

    @Override
    public Sequence[] topKSequences(String[] sentence, Object[] additionalContext) {
      calls++;
      return delegate.topKSequences(sentence, additionalContext);
    }
  }

  /**
   * A {@link Chunker} that delegates every call and counts them.
   */
  public static final class CountingChunker implements Chunker {

    private final Chunker delegate;
    private int calls;

    public CountingChunker(Chunker delegate) {
      this.delegate = delegate;
    }

    /**
     * @return The number of calls made to this chunker so far.
     */
    public int calls() {
      return calls;
    }

    @Override
    public String[] chunk(String[] toks, String[] tags) {
      calls++;
      return delegate.chunk(toks, tags);
    }

    @Override
    public Span[] chunkAsSpans(String[] toks, String[] tags) {
      calls++;
      return delegate.chunkAsSpans(toks, tags);
    }

    @Override
    public Sequence[] topKSequences(String[] sentence, String[] tags) {
      calls++;
      return delegate.topKSequences(sentence, tags);
    }

    @Override
    public Sequence[] topKSequences(String[] sentence, String[] tags, double minSequenceScore) {
      calls++;
      return delegate.topKSequences(sentence, tags, minSequenceScore);
    }
  }
}
