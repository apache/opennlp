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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

