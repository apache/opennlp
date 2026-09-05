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

package opennlp.tools.namefind;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;

public class RegexNameFinderFactoryTest {

  private static RegexNameFinder regexNameFinder;

  private static final String text = "my email is opennlp@gmail.com and my phone num is" +
      " 123-234-5678 and i like" +
      " https://www.google.com and I visited MGRS  11sku528111 AKA  11S KU 528 111 and" +
      " DMS 45N 123W AKA" +
      "  +45.1234, -123.12 AKA  45.1234N 123.12W AKA 45 30 N 50 30 W";

  @BeforeEach
  void setUp() {
    regexNameFinder = RegexNameFinderFactory.getDefaultRegexNameFinders(
        RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.DEGREES_MIN_SEC_LAT_LON,
        RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.EMAIL,
        RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.MGRS,
        RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.USA_PHONE_NUM,
        RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.URL);
  }

  @Test
  void testEmail() {
    String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(text);
    Span[] find = regexNameFinder.find(tokens);
    List<Span> spanList = Arrays.asList(find);
    Assertions.assertTrue(spanList.contains(new Span(3, 4, "EMAIL")));
    Span emailSpan = new Span(3, 4, "EMAIL");
    Assertions.assertEquals("opennlp@gmail.com", tokens[emailSpan.getStart()]);
  }

  @Test
  void testPhoneNumber() {
    String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(text);
    Span[] find = regexNameFinder.find(tokens);
    List<Span> spanList = Arrays.asList(find);
    Span phoneSpan = new Span(9, 10, "PHONE_NUM");
    Assertions.assertTrue(spanList.contains(phoneSpan));
    Assertions.assertEquals("123-234-5678", tokens[phoneSpan.getStart()]);
  }

  @Test
  void testURL() {
    String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(text);
    Span[] find = regexNameFinder.find(tokens);
    List<Span> spanList = Arrays.asList(find);
    Span urlSpan = new Span(13, 14, "URL");
    Assertions.assertTrue(spanList.contains(urlSpan));
    Assertions.assertEquals("https://www.google.com", tokens[urlSpan.getStart()]);
  }

  @Test
  void testLatLong() {
    String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(text);
    Span[] find = regexNameFinder.find(tokens);
    List<Span> spanList = Arrays.asList(find);
    Span latLongSpan1 = new Span(22, 24, "DEGREES_MIN_SEC_LAT_LON");
    Span latLongSpan2 = new Span(35, 41, "DEGREES_MIN_SEC_LAT_LON");
    Assertions.assertTrue(spanList.contains(latLongSpan1));
    Assertions.assertTrue(spanList.contains(latLongSpan2));
    Assertions.assertEquals("528", tokens[latLongSpan1.getStart()]);
    Assertions.assertEquals("45", tokens[latLongSpan2.getStart()]);
  }

  /**
   * Crafted inputs that used to drive the built-in EMAIL and URL patterns into
   * catastrophic backtracking / deep recursion (ReDoS, CWE-1333 / CWE-400 / CWE-674).
   * The hardened patterns must finish quickly regardless of input length.
   * <p>
   * Each attack runs as its own test so a regression names the exact input that
   * got slow. Each case does one warmup run outside the timed section, so the
   * budget measures the match itself rather than JVM cold start on throttled
   * machines.
   */
  private static Stream<Arguments> reDoSAttacks() {
    return Stream.of(
        Arguments.of(Named.of("emailLocalBlowup", "a".repeat(100_000) + "@ ")),
        Arguments.of(Named.of("emailDomainBlowup", "x@a" + "-a".repeat(60_000) + " ")),
        Arguments.of(Named.of("urlPathRecursion", "http://a.com/" + "a".repeat(100_000) + " ")),
        Arguments.of(Named.of("urlNestedBlowup", "http://a.com" + "/a".repeat(50_000) + "%z")),
        // The reported StackOverflowError repro: a long ?a&a&a&... query string, which
        // drove the nested (&(...)+ ... )* group in the old URL pattern into deep recursion.
        Arguments.of(Named.of("urlQueryRecursion", "http://a.com/p?a" + "&a".repeat(50_000) + "= ")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("reDoSAttacks")
  void testBuiltinPatternsAreNotVulnerableToReDoS(String attack) {
    String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(attack);
    // Warmup: JIT-compile the pattern engine before the timed run.
    regexNameFinder.find(tokens);

    Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
        () -> regexNameFinder.find(tokens));
  }

  /**
   * Regression tests for the hardened built-in patterns: a trailing slash or a
   * sentence-final period must not cause the match to be abandoned entirely.
   */
  @Test
  void testUrlWithTrailingSlashOrSentencePunctuation() {
    RegexNameFinder urlFinder = RegexNameFinderFactory.getDefaultRegexNameFinders(
        RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.URL);
    final String input = "see http://a.com/ or http://example.com/path/ or www.google.com.";
    Span[] spans = urlFinder.find(input);
    Assertions.assertEquals(3, spans.length);
    Assertions.assertEquals("http://a.com",
        input.substring(spans[0].getStart(), spans[0].getEnd()));
    Assertions.assertEquals("http://example.com/path",
        input.substring(spans[1].getStart(), spans[1].getEnd()));
    Assertions.assertEquals("www.google.com",
        input.substring(spans[2].getStart(), spans[2].getEnd()));
  }

  @Test
  void testUrlKeepsPortWhenFollowedBySlash() {
    RegexNameFinder urlFinder = RegexNameFinderFactory.getDefaultRegexNameFinders(
        RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.URL);
    final String input = "http://example.com:8080/";
    Span[] spans = urlFinder.find(input);
    Assertions.assertEquals(1, spans.length);
    Assertions.assertEquals("http://example.com:8080",
        input.substring(spans[0].getStart(), spans[0].getEnd()));
  }

  @Test
  void testEmailAtEndOfSentence() {
    RegexNameFinder emailFinder = RegexNameFinderFactory.getDefaultRegexNameFinders(
        RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.EMAIL);
    final String input = "mail me at a@b.com. and a@sub.b.co.uk.";
    Span[] spans = emailFinder.find(input);
    Assertions.assertEquals(2, spans.length);
    Assertions.assertEquals("a@b.com", input.substring(spans[0].getStart(), spans[0].getEnd()));
    Assertions.assertEquals("a@sub.b.co.uk",
        input.substring(spans[1].getStart(), spans[1].getEnd()));
  }

  /**
   * The hardened URL pattern must still match a URL that is followed by a trailing
   * delimiter (a path slash, a sentence-final period, {@code #} or {@code &}), stopping
   * the span before the delimiter rather than abandoning the match.
   */
  @Test
  void testUrlTrailingDelimiters() {
    assertFirstUrl("http://a.com", "http://a.com/");
    assertFirstUrl("http://example.com/path", "http://example.com/path/");
    assertFirstUrl("ftp://files.example.com/pub", "ftp://files.example.com/pub/");
    assertFirstUrl("www.google.com", "check www.google.com/ now");
    assertFirstUrl("http://example.com", "I saw http://example.com. Then");
    assertFirstUrl("www.google.com", "www.google.com.");
    assertFirstUrl("http://a.com/path", "http://a.com/path.");
    assertFirstUrl("http://a.com", "http://a.com/#");
    assertFirstUrl("http://example.com:8080", "http://example.com:8080/");
    assertFirstUrl("http://a.com/p?q=1", "http://a.com/p?q=1&");
  }

  private static void assertFirstUrl(String expected, String input) {
    RegexNameFinder urlFinder = RegexNameFinderFactory.getDefaultRegexNameFinders(
        RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER.URL);
    Span[] spans = urlFinder.find(input);
    Assertions.assertTrue(spans.length > 0, "no URL match for: " + input);
    Assertions.assertEquals(expected, input.substring(spans[0].getStart(), spans[0].getEnd()));
  }

  @Test
  void testMgrs() {
    String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(text);
    Span[] find = regexNameFinder.find(tokens);
    List<Span> spanList = Arrays.asList(find);
    Span mgrsSpan1 = new Span(18, 19, "MGRS");
    Span mgrsSpan2 = new Span(20, 24, "MGRS");
    Assertions.assertTrue(spanList.contains(mgrsSpan1));
    Assertions.assertTrue(spanList.contains(mgrsSpan2));
    Assertions.assertEquals("11SKU528111".toLowerCase(), tokens[mgrsSpan1.getStart()]);
    Assertions.assertEquals("11S", tokens[mgrsSpan2.getStart()]);
  }
}

