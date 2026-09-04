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

package opennlp.tools.stemmer.light;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Runs the manual's light stemmer examples (docbkx {@code stemmer.xml}) verbatim: every
 * value the chapter states is asserted here, so a change breaking this test breaks the
 * manual.
 */
class LightStemmerUsageExampleTest {

  /** German light stemming of a plural form. */
  @Test
  void testGermanLightStemmerStemsPlural() {
    final GermanLightStemmer light = new GermanLightStemmer();
    Assertions.assertEquals("haus", light.stem("h\u00E4usern").toString());
  }

  /** Spanish minimal stemming reduces a plural ending. */
  @Test
  void testSpanishMinimalStemmerReducesPlural() {
    Assertions.assertEquals("jersey",
        new SpanishMinimalStemmer().stem("jerseis").toString());
  }

  /** Norwegian stemming selects a written standard at construction. */
  @Test
  void testNorwegianLightStemmerSelectsBokmaal() {
    final NorwegianLightStemmer bokmaal =
        new NorwegianLightStemmer(NorwegianVariety.BOKMAAL);
    Assertions.assertEquals("hemmelig", bokmaal.stem("hemmeligheten").toString());
  }
}
