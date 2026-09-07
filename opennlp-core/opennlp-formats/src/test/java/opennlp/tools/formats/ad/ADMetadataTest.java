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

package opennlp.tools.formats.ad;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class ADMetadataTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
      "1001 p=1 ref=\"1001.porto-poesia=removeme=-2\" source=\"SELVA 1001.porto\"|1001|1",
      "1001 p=12 title box source=\"x\"|1001|12",
      "LIT-12 p=3|12|3",
      "LIT12 p=3|12|3",
      "-12 p=3|12|3",
      "12p=9|12|9",
      // leading zeros are digits
      "0012 p=007|12|7",
      // the first p= that a digit follows counts
      "12 p= p=8|12|8",
      "12 p=a p=8|12|8",
      "12 pp=7|12|7",
      "1 ap=2|1|2",
      // the text id ends at the first character that is no digit
      "12x34 p=5 p=6|12|5"
  })
  void testParseTextAndParagraph(String meta, int text, int paragraph) {
    Assertions.assertArrayEquals(new int[] {text, paragraph},
        ADMetadata.parseTextAndParagraph(meta));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "x p=1", "12", "12 p=", "12 P=1", "12 p=a", " 12 p=1", "p=1",
      "1 p==2", "LIT p=1", "LIT-p=1",
      // digits from other scripts are no ASCII digits
      "١٢ p=1", "12 p=١",
      // metadata is one line
      "12 p=1\n", "12\u2028 p=1", "12 p=1\u0085", "\r12 p=1"})
  void testParseTextAndParagraphRejects(String meta) {
    Assertions.assertNull(ADMetadata.parseTextAndParagraph(meta));
    Assertions.assertNull(ADMetadata.textId(meta));
    Assertions.assertNull(ADMetadata.textPrefix(meta));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "1001 p=1 source=\"x\"|1001",
      "LIT-1001 p=1|1001",
      "0012 p=1|0012",
      "12x34 p=5|12"
  })
  void testTextId(String meta, String textId) {
    Assertions.assertEquals(textId, ADMetadata.textId(meta));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "LIT-1001 p=1|LIT-",
      "LIT1001 p=1|LIT",
      "LITx1 p=1|LITx",
      "--1 p=1|--",
      "a1p=1|a"
  })
  void testTextPrefix(String meta, String prefix) {
    Assertions.assertEquals(prefix, ADMetadata.textPrefix(meta));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1001 p=1", "1001 p=1 LIT", "LIT1", "LIT1 p=", "LITé1 p=1"})
  void testTextPrefixRejects(String meta) {
    Assertions.assertNull(ADMetadata.textPrefix(meta));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, value = {
      "CIE source=\"abc\" x|abc",
      "CIE1 p=1 ref=\"r\" source=\"SELVA 1001.porto\"|SELVA 1001.porto",
      "CIE source=\"\"|''",
      "source=\"a\"|a",
      " source=\"a\"|a",
      // the first source attribute counts, up to the next double quote
      "source=\"a\"source=\"b\"|a",
      "source=\"source=\"x\"|source=",
      "source=\" a \" |' a '"
  })
  void testSource(String meta, String source) {
    Assertions.assertEquals(source, ADMetadata.source(meta));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "CIE x", "CIE source=\"a", "CIE source=a\"", "CIE Source=\"a\"",
      "source='a'",
      // metadata is one line
      "source=\"a\nb\"", "source=\"a\"\n", "source=\"a\"\u2028", "\u0085source=\"a\""})
  void testSourceRejects(String meta) {
    Assertions.assertNull(ADMetadata.source(meta));
  }
}
