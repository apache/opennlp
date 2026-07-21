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

/*
 * This algorithm is updated based on code located at:
 * http://members.unine.ch/jacques.savoy/clef/
 *
 * Full copyright for that code follows:
 */
/*
 * Copyright (c) 2005, Jacques Savoy
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. Redistributions in binary
 * form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials
 * provided with the distribution. Neither the name of the author nor the names
 * of its contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package opennlp.tools.stemmer.light;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;

import static opennlp.tools.stemmer.light.StemmerUtil.endsWith;

/**
 * Light Stemmer for Norwegian.
 *
 * <p>Parts of this stemmer are adapted from the {@link SwedishLightStemmer}; while the Swedish
 * algorithm has a pre-defined rule set and a corresponding corpus to validate against, the
 * Norwegian one is hand crafted.
 *
 * <p>Adapted from the identically named algorithm in Apache Lucene's analysis-common module.
 * Instances are immutable and safe for concurrent use by multiple threads; each instance is also
 * its own {@link StemmerFactory}. Input is expected to be lowercase, as produced by a
 * case-folding normalization step; the stemmer does not fold case itself.</p>
 */
@ThreadSafe
public final class NorwegianLightStemmer extends AbstractCharArrayStemmer
    implements StemmerFactory {

  private final boolean useBokmaal;
  private final boolean useNynorsk;

  /**
   * Instantiates the stemmer for one or both written standards.
   *
   * @param first The first standard whose endings are removed; must not be null.
   * @param more  Further standards; must not be or contain null.
   * @throws IllegalArgumentException Thrown if {@code first} or {@code more} is null,
   *         or {@code more} contains null.
   */
  public NorwegianLightStemmer(NorwegianVariety first, NorwegianVariety... more) {
    if (first == null) {
      throw new IllegalArgumentException("first must not be null");
    }
    if (more == null) {
      throw new IllegalArgumentException("more must not be null");
    }
    boolean bokmaal = first == NorwegianVariety.BOKMAAL;
    boolean nynorsk = first == NorwegianVariety.NYNORSK;
    for (final NorwegianVariety variety : more) {
      if (variety == null) {
        throw new IllegalArgumentException("more must not contain null");
      }
      bokmaal |= variety == NorwegianVariety.BOKMAAL;
      nynorsk |= variety == NorwegianVariety.NYNORSK;
    }
    useBokmaal = bokmaal;
    useNynorsk = nynorsk;
  }

  /** {@inheritDoc} */
  @Override
  public Stemmer newStemmer() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  int stem(char[] s, int len) {
    // Remove possessive -s (bilens -> bilen) and continue checking
    if (len > 4 && s[len - 1] == 's') len--;

    // Remove common endings, single-pass
    if (len > 7
        && ((endsWith(s, len, "heter") && useBokmaal)
            || // general ending (hemmelig-heter -> hemmelig)
            (endsWith(s, len, "heten") && useBokmaal)
            || // general ending (hemmelig-heten -> hemmelig)
            (endsWith(s, len, "heita")
                && useNynorsk))) // general ending (hemmeleg-heita -> hemmeleg)
      return len - 5;

    // Remove Nynorsk common endings, single-pass
    if (len > 8
        && useNynorsk
        && (endsWith(s, len, "heiter")
            || // general ending (hemmeleg-heiter -> hemmeleg)
            endsWith(s, len, "leiken")
            || // general ending (trygg-leiken -> trygg)
            endsWith(s, len, "leikar"))) // general ending (trygg-leikar -> trygg)
      return len - 6;

    if (len > 5
        && (endsWith(s, len, "dom")
            || // general ending (kristen-dom -> kristen)
            (endsWith(s, len, "het") && useBokmaal))) // general ending (hemmelig-het -> hemmelig)
      return len - 3;

    if (len > 6
        && useNynorsk
        && (endsWith(s, len, "heit")
            || // general ending (hemmeleg-heit -> hemmeleg)
            endsWith(s, len, "semd")
            || // general ending (verk-semd -> verk)
            endsWith(s, len, "leik"))) // general ending (trygg-leik -> trygg)
      return len - 4;

    if (len > 7
        && (endsWith(s, len, "elser")
            || // general ending (f\u00F8l-elser -> f\u00F8l)
            endsWith(s, len, "elsen"))) // general ending (f\u00F8l-elsen -> f\u00F8l)
      return len - 5;

    if (len > 6
        && ((endsWith(s, len, "ende") && useBokmaal)
            || // (sov-ende -> sov)
            (endsWith(s, len, "ande") && useNynorsk)
            || // (sov-ande -> sov)
            endsWith(s, len, "else")
            || // general ending (f\u00F8l-else -> f\u00F8l)
            (endsWith(s, len, "este") && useBokmaal)
            || // adj (fin-este -> fin)
            (endsWith(s, len, "aste") && useNynorsk)
            || // adj (fin-aste -> fin)
            (endsWith(s, len, "eren") && useBokmaal)
            || // masc
            (endsWith(s, len, "aren") && useNynorsk))) // masc
      return len - 4;

    if (len > 5
        && ((endsWith(s, len, "ere") && useBokmaal)
            || // adj (fin-ere -> fin)
            (endsWith(s, len, "are") && useNynorsk)
            || // adj (fin-are -> fin)
            (endsWith(s, len, "est") && useBokmaal)
            || // adj (fin-est -> fin)
            (endsWith(s, len, "ast") && useNynorsk)
            || // adj (fin-ast -> fin)
            endsWith(s, len, "ene")
            || // masc/fem/neutr pl definite (hus-ene)
            (endsWith(s, len, "ane") && useNynorsk))) // masc pl definite (gut-ane)
      return len - 3;

    if (len > 4
        && (endsWith(s, len, "er")
            || // masc/fem indefinite
            endsWith(s, len, "en")
            || // masc/fem definite
            endsWith(s, len, "et")
            || // neutr definite
            (endsWith(s, len, "ar") && useNynorsk)
            || // masc pl indefinite
            (endsWith(s, len, "st") && useBokmaal)
            || // adj (billig-st -> billig)
            endsWith(s, len, "te"))) return len - 2;

    if (len > 3)
      switch (s[len - 1]) {
        case 'a': // fem definite
        case 'e': // to get correct stem for nouns ending in -e (kake -> kak, kaker -> kak)
        case 'n':
          return len - 1;
      }

    return len;
  }
}
