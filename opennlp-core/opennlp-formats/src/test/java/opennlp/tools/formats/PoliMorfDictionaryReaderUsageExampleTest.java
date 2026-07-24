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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.lemmatizer.DictionaryLemmatizer;

/**
 * Runs the manual's PoliMorf reader examples (docbkx {@code lemmatizer.xml}) verbatim over a
 * small illustrative dictionary in PoliMorf's {@code surfaceForm\tlemma\ttag} format. It is a
 * hand-built fixture, not the PoliMorf distribution; every value the chapter states is asserted
 * here, so a change breaking this test breaks the manual.
 */
public class PoliMorfDictionaryReaderUsageExampleTest {

  private static final String[] ROWS = {
      "pies\tpies\tsubst:sg:nom:m2",
      "psa\tpies\tsubst:sg:gen:m2",
      "psy\tpies\tsubst:pl:nom:m2",
      "kota\tkot\tsubst:sg:gen:m2",
      // Same (form, tag) listed with two lemmas: the reader merges them into one entry.
      "formy\tforma\tsubst:pl:nom:f",
      "formy\tform\tsubst:pl:nom:f",
  };

  private static InputStream dictionary(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

  private static DictionaryLemmatizer fixture() throws IOException {
    return PoliMorfDictionaryReader.read(dictionary(String.join("\n", ROWS) + "\n"));
  }

  /** A form and its tag resolve to the base form; a homograph tag or unknown form yields "O". */
  @Test
  void testResolvesFormAndTagToLemma() throws IOException {
    final DictionaryLemmatizer lemmatizer = fixture();

    Assertions.assertArrayEquals(new String[] {"pies"},
        lemmatizer.lemmatize(new String[] {"psa"}, new String[] {"subst:sg:gen:m2"}));
    Assertions.assertArrayEquals(new String[] {"kot"},
        lemmatizer.lemmatize(new String[] {"kota"}, new String[] {"subst:sg:gen:m2"}));
    // A known form under a tag it never carries is a miss.
    Assertions.assertArrayEquals(new String[] {"O"},
        lemmatizer.lemmatize(new String[] {"psa"}, new String[] {"adj:sg:nom:m2:pos"}));
    // An unknown form is a miss.
    Assertions.assertArrayEquals(new String[] {"O"},
        lemmatizer.lemmatize(new String[] {"kanapa"}, new String[] {"subst:sg:nom:f"}));
  }

  /** Lookup is case-insensitive because forms are lower-cased on load. */
  @Test
  void testLookupIsCaseInsensitive() throws IOException {
    Assertions.assertArrayEquals(new String[] {"pies"},
        fixture().lemmatize(new String[] {"Psa"}, new String[] {"subst:sg:gen:m2"}));
  }

  /** Every lemma listed for a form and tag is kept, in first-seen order. */
  @Test
  void testAlternativeLemmasAreMerged() throws IOException {
    final List<List<String>> lemmas =
        fixture().lemmatize(List.of("formy"), List.of("subst:pl:nom:f"));

    Assertions.assertEquals(List.of(List.of("forma", "form")), lemmas);
  }

  /** Blank lines are skipped rather than treated as entries. */
  @Test
  void testBlankLinesAreSkipped() throws IOException {
    final DictionaryLemmatizer lemmatizer = PoliMorfDictionaryReader.read(
        dictionary("\npsa\tpies\tsubst:sg:gen:m2\n   \n"));

    Assertions.assertArrayEquals(new String[] {"pies"},
        lemmatizer.lemmatize(new String[] {"psa"}, new String[] {"subst:sg:gen:m2"}));
  }

  /** A non-blank line with fewer than three fields fails loudly. */
  @Test
  void testTooFewFieldsThrows() {
    Assertions.assertThrows(IOException.class,
        () -> PoliMorfDictionaryReader.read(dictionary("psa\tpies\n")));
  }

  /** A null dictionary stream is rejected at the boundary. */
  @Test
  void testNullDictionaryRejected() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PoliMorfDictionaryReader.read(null));
  }
}
