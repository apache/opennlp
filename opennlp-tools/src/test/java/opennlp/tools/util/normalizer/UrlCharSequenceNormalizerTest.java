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

package opennlp.tools.util.normalizer;

import java.nio.CharBuffer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.CompatibilityMode;

public class UrlCharSequenceNormalizerTest {

  private static final UrlCharSequenceNormalizer NORMALIZER =
      UrlCharSequenceNormalizer.getInstance();

  @AfterEach
  void resetMode() {
    CompatibilityMode.reset();
  }

  @Test
  void normalizeUrl() {
    Assertions.assertEquals(
        "asdf   2nnfdf", NORMALIZER.normalize("asdf http://asdf.com/dfa/cxs 2nnfdf"));
    Assertions.assertEquals(
        "asdf   2nnfdf  ", NORMALIZER.normalize("asdf http://asdf.com/dfa/cx" +
            "s 2nnfdf http://asdf.com/dfa/cxs"));
  }

  @Test
  void normalizeEmail() {
    Assertions.assertEquals(
        "asdf   2nnfdf", NORMALIZER.normalize("asdf asd.fdfa@hasdk23.com.br 2nnfdf"));
    Assertions.assertEquals(
        "asdf   2nnfdf  ", NORMALIZER.normalize("asdf asd.fdfa@hasdk23.com.br" +
            " 2nnfdf asd.fdfa@hasdk23.com.br"));
    Assertions.assertEquals(
        "asdf   2nnfdf", NORMALIZER.normalize("asdf asd+fdfa@hasdk23.com.br 2nnfdf"));
    Assertions.assertEquals(
        "asdf  _br 2nnfdf", NORMALIZER.normalize("asdf asd.fdfa@hasdk23.com_br 2nnfdf"));
  }

  private static String cp(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  /** Valid http and https URLs from the WHATWG URL test data, removed as a whole. */
  private static Stream<Arguments> wholeUrls() {
    return Stream.of(
        Arguments.of("http://f:21/", " "),
        Arguments.of("http://f:21/ b", "  b"),
        Arguments.of("http://example.com/%20foo", " "),
        Arguments.of("http://user:pass@foo.example.com:8080/x?y#z", " "),
        Arguments.of("http://[::1]/", " "),
        Arguments.of("http://[1080::8:800:200C:417A]/index.html", " "),
        Arguments.of("http://example.com/foo\"bar", " \"bar"),
        Arguments.of("http://example.com/a'b(c){d}!e", " "),
        Arguments.of("http://example.com/a`b", " "),
        Arguments.of("http://example.com/foo<bar", " <bar"),
        Arguments.of("http://a.b.c/", " "),
        Arguments.of("HTTP://EXAMPLE.COM/", " "),
        Arguments.of("Https://example.com/", " "),
        Arguments.of("https://xn--mnchen-3ya.de/pfad", " "),
        Arguments.of("http://münchen.de/straße", " "),
        Arguments.of("http://例え.テスト/パス", " "),
        Arguments.of("http://example.com/" + cp(0x1F600) + "/x", " "),
        Arguments.of("http://example.com/" + cp(0x1D400), " "));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @MethodSource("wholeUrls")
  void normalizeRemovesWholeHttpUrls(String text, String expected) {
    Assertions.assertEquals(expected, NORMALIZER.normalize(text));
  }

  /** URLs with another scheme are kept, and no email address is matched inside them. */
  @ParameterizedTest
  @ValueSource(strings = {"git+https://github.com/foo/bar", "blob:https://example.com:443/",
      "telnet://user:pass@foobar.com:23/", "redis://user:pass@host:6379/0",
      "rsync://user:pass@host/module", "ftp://ftp.example.com/pub", "ws://example.com/socket",
      "sc://user:pass@x/", "javascript://example.com/"})
  void normalizeKeepsOtherSchemes(String text) {
    Assertions.assertEquals(text, NORMALIZER.normalize(text));
  }

  /** The scheme needs a left boundary. */
  private static Stream<Arguments> leftBoundaries() {
    return Stream.of(
        Arguments.of("xhttp://example.com/", "xhttp://example.com/"),
        Arguments.of("1http://example.com/", "1http://example.com/"),
        Arguments.of("_http://example.com/", "_http://example.com/"),
        Arguments.of("a.http://example.com/", "a.http://example.com/"),
        Arguments.of("a-http://example.com/", "a-http://example.com/"),
        Arguments.of("ähttp://example.com/", "ähttp://example.com/"),
        Arguments.of("(http://example.com/a)", "( )"),
        Arguments.of("[http://example.com/a]", "[ ]"),
        Arguments.of("<http://example.com/a>", "< >"),
        Arguments.of("\"http://example.com/a\"", "\" \""),
        Arguments.of("«http://example.com/a»", "« »"),
        Arguments.of("see:http://example.com/a", "see:http://example.com/a"),
        Arguments.of("see,http://example.com/a", "see, "),
        Arguments.of("see/http://example.com/a", "see/ "),
        Arguments.of("see http://example.com/a", "see  "),
        Arguments.of("\u201chttp://example.com/a\u201d", "\u201c \u201d"),
        Arguments.of("\u201ehttp://example.com/a\u201c", "\u201e \u201c"));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @MethodSource("leftBoundaries")
  void normalizeNeedsALeftBoundaryBeforeTheScheme(String text, String expected) {
    Assertions.assertEquals(expected, NORMALIZER.normalize(text));
  }

  /** Trailing punctuation, apostrophes and unbalanced closing brackets belong to the text. */
  private static Stream<Arguments> trailingPunctuation() {
    return Stream.of(
        Arguments.of("see http://example.com/a.", "see  ."),
        Arguments.of("see http://example.com/a, then", "see  , then"),
        Arguments.of("http://example.com/a?", " ?"),
        Arguments.of("http://example.com/a!?", " !?"),
        Arguments.of("http://example.com/a:", " :"),
        Arguments.of("http://example.com/a;", " ;"),
        Arguments.of("'http://example.com/a'", "' '"),
        Arguments.of("see http://example.com/a'.", "see  '."),
        Arguments.of("(http://example.com/a).", "( )."),
        Arguments.of("http://example.com/a(b)", " "),
        Arguments.of("http://example.com/a(b).", " ."),
        Arguments.of("http://example.com/a(b))", " )"),
        Arguments.of("http://[::1]", " "),
        Arguments.of("http://example.com/a]", " ]"),
        Arguments.of("http://example.com/a?b=1#c", " "),
        Arguments.of("http://example.com/a?b=1&c=2;d", " "),
        Arguments.of("http://example.com/..", " .."),
        Arguments.of("http://example.com/a/", " "));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @MethodSource("trailingPunctuation")
  void normalizeGivesBackTrailingPunctuation(String text, String expected) {
    Assertions.assertEquals(expected, NORMALIZER.normalize(text));
  }

  /** Whitespace, control characters, delimiters, and unpaired surrogates end the body. */
  private static Stream<Arguments> bodyEnds() {
    return Stream.of(
        Arguments.of("http://example.com/a b", "  b"),
        Arguments.of("http://example.com/a\tb", " \tb"),
        Arguments.of("http://example.com/a\nb", " \nb"),
        Arguments.of("http://example.com/a" + cp(0x00A0) + "b", " " + cp(0x00A0) + "b"),
        Arguments.of("http://example.com/a" + cp(0x3000) + "b", " " + cp(0x3000) + "b"),
        Arguments.of("http://example.com/a" + cp(0x2028) + "b", " " + cp(0x2028) + "b"),
        Arguments.of("http://example.com/a" + cp(0x0085) + "b", " " + cp(0x0085) + "b"),
        Arguments.of("http://example.com/a" + cp(0x0001) + "b", " " + cp(0x0001) + "b"),
        Arguments.of("http://example.com/a" + cp(0x007F) + "b", " " + cp(0x007F) + "b"),
        Arguments.of("http://example.com/a" + (char) 0xD800 + "b", " " + (char) 0xD800 + "b"),
        Arguments.of("http://example.com/a" + (char) 0xDC00, " " + (char) 0xDC00),
        Arguments.of((char) 0xD800 + "http://example.com/a", (char) 0xD800 + " "),
        Arguments.of("http://example.com/a" + cp(0x200B) + "b", " "),
        Arguments.of("http://example.com/a>b", " >b"));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @MethodSource("bodyEnds")
  void normalizeEndsTheBodyAtDelimiters(String text, String expected) {
    Assertions.assertEquals(expected, NORMALIZER.normalize(text));
  }

  /** A scheme without a body, or with only punctuation, is no URL. */
  @ParameterizedTest
  @ValueSource(strings = {"http://", "https://", "http://?", "http://.", "http:// x", "http:/x",
      "http:foo", "http//x", "://x", "http", "https:", ""})
  void normalizeKeepsTextWithoutAUrl(String text) {
    Assertions.assertEquals(text, NORMALIZER.normalize(text));
  }

  private static Stream<Arguments> positionsAndCounts() {
    return Stream.of(
        Arguments.of("http://a.b/ text", "  text"),
        Arguments.of("text http://a.b/", "text  "),
        Arguments.of("http://a.b/", " "),
        Arguments.of("http://a.b/ and http://c.d/", "  and  "),
        Arguments.of("http://a.b/ http://c.d/", "   "),
        Arguments.of("x http://a.b/ y https://c.d/e z", "x   y   z"),
        Arguments.of("http://a.b/?q=http://c.d/", " "));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @MethodSource("positionsAndCounts")
  void normalizeHandlesUrlsAtAnyPosition(String text, String expected) {
    Assertions.assertEquals(expected, NORMALIZER.normalize(text));
  }

  private static Stream<Arguments> mailAddresses() {
    return Stream.of(
        Arguments.of("asdf asd.fdfa@hasdk23.com.br 2nnfdf", "asdf   2nnfdf"),
        Arguments.of("asdf asd+fdfa@hasdk23.com.br 2nnfdf", "asdf   2nnfdf"),
        Arguments.of("asdf asd.fdfa@hasdk23.com_br 2nnfdf", "asdf  _br 2nnfdf"),
        Arguments.of("mail me@example.com or http://example.com/x", "mail   or  "),
        Arguments.of("http://user:pass@example.com/x me@example.com", "   "),
        Arguments.of("a@b", "a@b"),
        Arguments.of("a@bc", " "),
        Arguments.of("@example.com", "@example.com"));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @MethodSource("mailAddresses")
  void normalizeRemovesMailAddressesOutsideUrls(String text, String expected) {
    Assertions.assertEquals(expected, NORMALIZER.normalize(text));
  }

  @Test
  void normalizeNullThrows() {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> NORMALIZER.normalize(null));
    Assertions.assertEquals("The text must not be null.", e.getMessage());
  }

  @Test
  void normalizeReturnsTheInputWhenNothingMatches() {
    String text = "plain text with no url or address";
    Assertions.assertSame(text, NORMALIZER.normalize(text));
  }

  @Test
  void normalizeAcceptsAnyCharSequence() {
    String text = "a http://example.com/x b";
    Assertions.assertEquals("a   b", NORMALIZER.normalize(new StringBuilder(text)));
    Assertions.assertEquals("a   b", NORMALIZER.normalize(CharBuffer.wrap(text.toCharArray())));
  }

  @ParameterizedTest
  @ValueSource(strings = {")", "]", "}", ")]}", ").]!}"})
  void normalizeLongBracketSuffixWithLinearWork(String suffix) {
    String tail = suffix.repeat(1000);
    String input = "http://example.org/" + tail;
    Assertions.assertEquals(" " + tail, NORMALIZER.normalize(new ReadBudget(input)));
  }

  @Test
  void normalizeBalancedBracketsBeforeExcessClosers() {
    String tail = ")]}".repeat(1000);
    String input = "http://example.org/(a)[b]{c}" + tail;
    Assertions.assertEquals(" " + tail, NORMALIZER.normalize(new ReadBudget(input)));
  }

  /** Counts character accesses so complexity regressions fail without a timing threshold. */
  private static final class ReadBudget implements CharSequence {
    private final String text;
    private int remaining;

    private ReadBudget(String text) {
      this.text = text;
      remaining = 32 * text.length();
    }

    @Override
    public int length() {
      return text.length();
    }

    @Override
    public char charAt(int index) {
      Assertions.assertTrue(remaining-- > 0, "Normalization exceeded the linear character-read budget");
      return text.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return text.subSequence(start, end);
    }

    @Override
    public String toString() {
      return text;
    }
  }

  /** The output under {@link CompatibilityMode#LEGACY}, as language detector models were trained on it. */
  private static Stream<Arguments> legacyOutput() {
    return Stream.of(
        Arguments.of("http://f:21/", " :21/"),
        Arguments.of("http://example.com/%20foo", " %20foo"),
        Arguments.of("http://user:pass@foo.example.com:8080/x", " : :8080/x"),
        Arguments.of("http://[::1]/", "http://[::1]/"),
        Arguments.of("http://example.com/foo\"bar", " \"bar"),
        Arguments.of("http://example.com/a'b", " 'b"),
        Arguments.of("HTTP://EXAMPLE.COM/", "HTTP://EXAMPLE.COM/"),
        Arguments.of("http://münchen.de/", " ünchen.de/"),
        Arguments.of("git+https://github.com/foo/bar", "git+ "),
        Arguments.of("blob:https://example.com:443/", "blob: :443/"),
        Arguments.of("telnet://user:pass@foobar.com:23/", "telnet://user: :23/"),
        Arguments.of("redis://user:pass@host:6379/0", "redis://user: :6379/0"),
        Arguments.of("http://256.256.256.256", " "),
        Arguments.of("http://?", " "),
        Arguments.of("see http://example.com/a.", "see  "),
        Arguments.of("(http://example.com/a)", "( )"));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @MethodSource("legacyOutput")
  void normalizeProducesTheLegacyOutput(String text, String expected) {
    CompatibilityMode.setActive(CompatibilityMode.LEGACY);
    Assertions.assertEquals(expected, NORMALIZER.normalize(text));
  }

  @Test
  void normalizeLegacyKeepsUnmatchedText() {
    CompatibilityMode.setActive(CompatibilityMode.LEGACY);
    final String text = "plain text with no url or address";
    Assertions.assertSame(text, NORMALIZER.normalize(text));
    Assertions.assertEquals("asdf   2nnfdf", NORMALIZER.normalize("asdf http://asdf.com/dfa/cxs 2nnfdf"));
  }
}
