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

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test class for {@link NameFinderME} and the detection of dates in text.
 * <p>
 * The scope of this test is to verify that the name finder code finds dates in different formats and
 * for multiple languages, German and English for instance.
 * <p>
 * Note:</br>
 * A proper testing and evaluation of the name finder is only possible with a large corpus which contains
 * a huge amount of test sentences.
 * 
 * @implNote Some of the test cases are a reproducer for OPENNLP-1226.
 * These show that trained NER-date models can in fact find valid dates in 'dd.mm.yyyy',
 * given that the training material has a substantial amount of samples. The original
 * reporter of OPENNLP-1226 lacked a broad corpus, such as used in this test case with
 * the synthetic corpus build from 'RandomNewsWithGeneratedDates_DE.train' and its related
 * English variant.
 *
 * @see NameFinderME
 * @see AbstractNameFinderTest
 */
public class NameFinderMEWithDatesTest extends AbstractNameFinderTest {

  private static final String TYPE_DATE = "date";

  private static TokenNameFinderModel v15ModelEN;

  private static NameFinderME nameFinderEN;
  private static NameFinderME nameFinderDE;

  @BeforeAll
  public static void initResources() {
    try {
      downloadVersion15Model("en-ner-date.bin");
      v15ModelEN = new TokenNameFinderModel(Files.newInputStream(
              OPENNLP_DIR.resolve("en-ner-date.bin")));
      TokenNameFinderModel trainedModelEN = trainModel("eng",
              "opennlp/tools/namefind/RandomNewsWithGeneratedDates_EN.train");
      assertNotNull(trainedModelEN);
      assertTrue(hasOtherAsOutcome(trainedModelEN));
      TokenNameFinderModel trainedModelDE = trainModel("deu",
              "opennlp/tools/namefind/RandomNewsWithGeneratedDates_DE.train");
      assertNotNull(trainedModelDE);
      assertTrue(hasOtherAsOutcome(trainedModelDE));
      nameFinderEN = new NameFinderME(trainedModelEN);
      nameFinderDE = new NameFinderME(trainedModelDE);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Uses the English SourceForge V1.5 model with "date" nameType and verifies with sample text.
   */
  @Test
  void testFindWithV15ModelEN() {
    NameFinderME nameFinder = new NameFinderME(v15ModelEN);

    String[] s = {"On", "November", "17", ",", "2021", "the", "third", "season", "appeared", "."};
    String[] s2 = {"The", "book", "edition", "was", "published", "in", "1908", "."};

    // now test if it can detect the sample sentences
    Span[] result = nameFinder.find(s);
    assertEquals(1, result.length);
    Span found = result[0];
    assertNotNull(found);
    assertEquals(TYPE_DATE, found.getType());
    assertEquals("November 17", s[found.getStart()] + " " + s[found.getEnd() - 1]);

    result = nameFinder.find(s2);
    assertEquals(1, result.length);
    found = result[0];
    assertEquals(new Span(6, 7, TYPE_DATE), found);
    assertEquals(TYPE_DATE, found.getType());
    assertEquals("1908", s2[found.getStart()]);
  }

  /**
   * Verifies the v15ModelEN model won't detect uncommon date formats in text English samples.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "On 12.07.2021 the couple got married .",
      "No evidence was found before 28.2.17 ."
  })
  void testFindWithInvalidDateFormatsV15(String input) {
    NameFinderME nameFinderV15EN = new NameFinderME(v15ModelEN);
    Span[] result = nameFinderV15EN.find(input.split("\\s"));
    assertNotNull(result);
    assertEquals(0, result.length);
  }

  /**
   * Verifies the nameFinderEN model for with "date" nameType with English text samples.
   */
  @ParameterizedTest(name = "Verify detection of \"{1}\"")
  @MethodSource(value = "provideDataEN")
  void testFindWithTrainedModelEN(String input, String expDate,
                                       int expStart, int expEnd, boolean hasSepChar) {
    // Tokenize
    String[] t = input.split("\\s");

    // Verify if can detect dates in the sample sentences
    Span[] result = nameFinderEN.find(t);
    verifyDateDetection(t, result, expDate, expStart, expEnd, hasSepChar);
  }

  /**
   * Verifies the nameFinderEN model does not detect uncommon date formats in text samples.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "Last März the couple got married .",
      "No evidence was found before 28.2.17 ."
  })
  void testFindWithInvalidDateFormatsEN(String input) {
    Span[] result = nameFinderEN.find(input.split("\\s"));
    assertNotNull(result);
    assertEquals(0, result.length);
  }

  /**
   * Verifies the nameFinderDE model for with "date" nameType with German text samples.
   */
  @ParameterizedTest(name = "Verify detection of \"{1}\"")
  @MethodSource(value = "provideDataDE")
  void testFindWithTrainedModelDE(String input, String expDate,
                                       int expStart, int expEnd, boolean hasSepChar) {
    // Tokenize
    String[] t = input.split("\\s");
    
    // Verify if can detect dates in the sample sentences
    Span[] result = nameFinderDE.find(t);
    assertNotNull(result);
    verifyDateDetection(t, result, expDate, expStart, expEnd, hasSepChar);
  }

  /**
   * Verifies the nameFinderDE model does not detect uncommon date formats in text samples.
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "Es wurde am 2010-05-08 entdeckt .",
      "Das Beweisstück wurde am 27/04/2009 gefunden .",
      "Das Gesetz wurde am Monday, 12/12/2000 verabschiedet .",
      "Die Ministerin hat sich am 26/1/1998 verabschiedet .",
  })
  void testFindWithInvalidDateFormatsDE(String input) {
    Span[] result = nameFinderDE.find(input.split("\\s"));
    assertNotNull(result);
    assertEquals(0, result.length);
  }

  private void verifyDateDetection(String[] t, Span[] result, String expDate,
                                   int expStart, int expEnd, boolean hasSepChar) {
    assertEquals(1, result.length);
    Span s = result[0];
    assertNotNull(s);
    assertEquals(TYPE_DATE, s.getType());
    assertEquals(expStart, s.getStart());
    assertEquals(expEnd, s.getEnd());
    assertTrue(s.getProb() > 0.5d);
    StringBuilder detectedDate = new StringBuilder();
    for (int i = s.getStart(); i <= (s.getEnd() - 1); i++) {
      if (hasSepChar) {
        detectedDate.append(t[i]);
      } else {
        if (i != s.getEnd() - 1) {
          detectedDate.append(t[i]).append(" ");
        } else {
          detectedDate.append(t[i]);
        }
      }
    }
    assertEquals(expDate, detectedDate.toString());
  }

  // Note: This needs to be public as JUnit 5 requires it like this.
  public static Stream<Arguments> provideDataDE() {
    // Note: Positions (start, end) start at index 0 and are 'start' inclusively, 'end' exclusively!
    return Stream.of(
      Arguments.of("Die erste Ausgabe erschien am 5. Oktober 1907 .",
              "5. Oktober 1907", 5, 8, false),
      Arguments.of("Die dritte Staffel erschien am 17.11.2023 .",
              "17.11.2023", 5, 6, true),
      Arguments.of("Ein bedeutender Durchbruch wurde im März 2024 erzielt .",
              "März 2024", 5, 7, false)
    );
  }

  public static Stream<Arguments> provideDataEN() {
    // Note: Positions (start, end) start at index 0 and are 'start' inclusively, 'end' exclusively!
    return Stream.of(
      Arguments.of("On March 17, 2021 the third season appeared .",
              "March 17, 2021", 1, 4, false),
      Arguments.of("The contract was signed on 2010-05-08 before the event .",
              "2010-05-08", 5, 6, true),
      Arguments.of("His mother talked to him on 27/04/2009 during the evening .",
              "27/04/2009", 6, 7, true)
    );
  }
}
