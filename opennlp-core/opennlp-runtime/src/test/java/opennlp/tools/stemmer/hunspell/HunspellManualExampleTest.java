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

package opennlp.tools.stemmer.hunspell;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.stemmer.Stemmer;

/**
 * Runs the manual's Hunspell examples (docbkx {@code stemmer.xml}) verbatim: every
 * value the chapter states is asserted here, so a change breaking this test breaks the
 * manual. The fixture dictionary is authored inside this class; no external dictionary
 * data is involved.
 */
public class HunspellManualExampleTest {

  /**
   * Affix fixture matching the chapter: agentive {@code -er} with continuation class
   * {@code S}, and the plural {@code -s}.
   */
  private static final String AFFIX = String.join("\n",
      "SET UTF-8",
      "SFX E Y 1",
      "SFX E 0 er/S .",
      "SFX S Y 1",
      "SFX S 0 s [^sxy]",
      "");

  /** Word-list fixture: {@code work} accepts both suffixes. */
  private static final String WORDS = "1\nwork/ES\n";

  /**
   * Loads the chapter's miniature dictionary, stems through a factory-minted stemmer,
   * and asserts the exact stems the manual prints.
   *
   * @throws IOException Thrown if the in-memory fixture fails to load.
   */
  @Test
  void testLoadAndStemWorkers() throws IOException {
    final HunspellDictionary dictionary = HunspellDictionary.load(
        new ByteArrayInputStream(AFFIX.getBytes(StandardCharsets.UTF_8)),
        new ByteArrayInputStream(WORDS.getBytes(StandardCharsets.UTF_8)));
    final Stemmer stemmer = new HunspellStemmerFactory(dictionary).newStemmer();

    Assertions.assertEquals("work", stemmer.stem("workers").toString());
    Assertions.assertEquals("work", stemmer.stem("worker").toString());
    Assertions.assertEquals(List.of("work"),
        stemmer.stemAll("workers").stream().map(CharSequence::toString).toList());
    // unknown vocabulary passes through unchanged
    Assertions.assertEquals("table", stemmer.stem("table").toString());
  }
}
