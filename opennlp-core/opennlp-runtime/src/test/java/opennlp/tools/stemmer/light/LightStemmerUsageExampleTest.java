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

import opennlp.tools.stemmer.SharingStemmer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;

/**
 * Runs the manual's light stemmer examples (docbkx {@code stemmer.xml}) verbatim: every
 * value the chapter states is asserted here, so a change breaking this test breaks the
 * manual.
 */
public class LightStemmerUsageExampleTest {

  /**
   * German light stemming of a plural form, and the same result through
   * {@link SharingStemmer}.
   */
  @Test
  void testGermanLightStemmerStemsPlural() {
    final GermanLightStemmer light = new GermanLightStemmer();
    Assertions.assertEquals("haus", light.stem("h\u00E4usern").toString());

    final StemmerFactory factory = light;
    final Stemmer shared = new SharingStemmer(factory);
    Assertions.assertEquals("haus", shared.stem("h\u00E4usern").toString());
  }

  /**
   * German minimal stemming keeps more of the surface form than the light tier.
   */
  @Test
  void testGermanMinimalStemmerIsShallower() {
    Assertions.assertEquals("vaterhauser",
        new GermanMinimalStemmer().stem("vaterh\u00E4usern").toString());
  }
}
