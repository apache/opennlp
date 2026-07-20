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

package opennlp.tools.stemmer;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmerFactory;

/**
 * Runs the manual's stemmer factory examples (docbkx {@code stemmer.xml}) verbatim: every
 * value the chapter states is asserted here, so a change breaking this test breaks the
 * manual.
 */
public class StemmerFactoryUsageExampleTest {

  /**
   * Factory to {@code newStemmer()} to a single stem and the default {@code stemAll} list.
   */
  @Test
  void testSnowballFactoryStemsOneWord() {
    final StemmerFactory factory =
        new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    final Stemmer stemmer = factory.newStemmer();

    Assertions.assertEquals("run", stemmer.stem("running").toString());
    Assertions.assertEquals(List.of("run"),
        stemmer.stemAll("running").stream().map(CharSequence::toString).toList());
  }

  /**
   * {@link SharingStemmer} and {@link CachingStemmer} produce the same stem as a fresh
   * factory stemmer for the same word.
   */
  @Test
  void testSharingAndCachingStemmersMatchFactoryStem() {
    final StemmerFactory factory =
        new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    final CharSequence expected = factory.newStemmer().stem("running");

    Assertions.assertEquals(expected, new SharingStemmer(factory).stem("running"));
    Assertions.assertEquals(expected, new CachingStemmer(factory).stem("running"));
  }
}
