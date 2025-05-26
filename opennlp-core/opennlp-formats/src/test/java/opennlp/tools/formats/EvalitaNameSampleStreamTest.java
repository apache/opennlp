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

package opennlp.tools.formats;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.formats.EvalitaNameSampleStream.LANGUAGE;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Note:
 * Sample training data must be UTF-8 encoded and uncompressed!
 */
public class EvalitaNameSampleStreamTest extends AbstractSampleStreamTest {

  private static final String SAMPLE_01 = "evalita-ner-it-01.sample";
  private static final String SAMPLE_02 = "evalita-ner-it-02.sample";
  private static final String SAMPLE_03 = "evalita-ner-it-03.sample";
  private static final String SAMPLE_BROKEN = "evalita-ner-it-broken.sample";
  private static final String SAMPLE_INCORRECT = "evalita-ner-it-incorrect.sample";

  @ParameterizedTest
  @MethodSource(value = "provideData")
  void testReadItalianDifferentEntityTypes(String file, int nerType, int expectedSentLength,
                                  int expectedStart, int expectedEnd) throws IOException {

    try (ObjectStream<NameSample> sampleStream = openData(file, nerType)) {
      NameSample ne = sampleStream.read();
      assertNotNull(ne);

      assertEquals(expectedSentLength, ne.getSentence().length);
      assertEquals(1, ne.getNames().length);
      assertTrue(ne.isClearAdaptiveDataSet());

      Span nameSpan = ne.getNames()[0];
      assertEquals(expectedStart, nameSpan.getStart());
      assertEquals(expectedEnd, nameSpan.getEnd());
      assertTrue(ne.isClearAdaptiveDataSet());

      if (SAMPLE_01.equals(file)) { // this file has an extra sentence
        assertEquals(0, sampleStream.read().getNames().length);
      }
      assertNull(sampleStream.read());
    }
  }

  @Test
  void testReadWithIncorrectInput() {
    assertThrows(IOException.class, () -> {
      try (ObjectStream<NameSample> sampleStream = openData(
              SAMPLE_INCORRECT, EvalitaNameSampleStream.GENERATE_PERSON_ENTITIES)) {
        sampleStream.read();
      }
    });
  }

  @Test
  void testReadWithBrokenDocument() {
    assertThrows(IOException.class, () -> {
      try (ObjectStream<NameSample> sampleStream = openData(
              SAMPLE_BROKEN, EvalitaNameSampleStream.GENERATE_PERSON_ENTITIES)) {
        sampleStream.read();
      }
    });
  }

  @Test
  void testReset() throws IOException {
    try (ObjectStream<NameSample> sampleStream = openData(SAMPLE_01,
            EvalitaNameSampleStream.GENERATE_PERSON_ENTITIES)) {
      NameSample sample = sampleStream.read();
      sampleStream.reset();
      assertEquals(sample, sampleStream.read());
    }
  }

  // Note: This needs to be public as JUnit 5 requires it like this.
  public static Stream<Arguments> provideData() {
    return Stream.of(
      Arguments.of(SAMPLE_01, EvalitaNameSampleStream.GENERATE_PERSON_ENTITIES, 11, 8, 10),
      Arguments.of(SAMPLE_02, EvalitaNameSampleStream.GENERATE_PERSON_ENTITIES, 27, 11, 13),
      Arguments.of(SAMPLE_02, EvalitaNameSampleStream.GENERATE_ORGANIZATION_ENTITIES, 27, 10, 11),
      Arguments.of(SAMPLE_03, EvalitaNameSampleStream.GENERATE_GPE_ENTITIES, 20, 18, 19)
    );
  }

  private ObjectStream<NameSample> openData(String fileName, int nerType) throws IOException {
    return new EvalitaNameSampleStream(LANGUAGE.IT, getFactory(fileName), nerType);
  }
}
