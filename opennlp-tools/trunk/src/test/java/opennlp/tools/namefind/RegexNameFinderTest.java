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


package opennlp.tools.namefind;

import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import opennlp.tools.util.Span;

import org.junit.Test;

/**
 * Tests for the {@link RegexNameFinder} class.
 */
public class RegexNameFinderTest {

  @Test
  public void testFindSingleTokenPattern() {
    Pattern testPattern = Pattern.compile("test");

    String sentence[] = new String[]{"a", "test", "b", "c"};

    RegexNameFinder finder =
      new RegexNameFinder(new Pattern[]{testPattern});

    Span[] result = finder.find(sentence);

    assertTrue(result.length == 1);

    assertTrue(result[0].getStart() == 1);
    assertTrue(result[0].getEnd() == 1);
  }

  @Test
  public void testFindTokenizdPattern() {
    Pattern testPattern = Pattern.compile("[0-9]+ year");

    String sentence[] = new String[]{"a", "80", "year", "b", "c"};

    RegexNameFinder finder =
      new RegexNameFinder(new Pattern[]{testPattern});

    Span[] result = finder.find(sentence);

    assertTrue(result.length == 1);

    assertTrue(result[0].getStart() == 1);
    assertTrue(result[0].getEnd() == 2);
  }

  @Test
  public void testFindMatchingPatternWithoutMatchingTokenBounds() {
    Pattern testPattern = Pattern.compile("[0-8] year"); // does match "0 year"

    String sentence[] = new String[]{"a", "80", "year", "c"};

    RegexNameFinder finder =
      new RegexNameFinder(new Pattern[]{testPattern});

    Span[] result = finder.find(sentence);

    assertTrue(result.length == 0);
  }
}