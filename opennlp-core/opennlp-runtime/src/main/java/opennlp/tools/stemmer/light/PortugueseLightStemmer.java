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
 * Light Stemmer for Portuguese
 *
 * <p>This stemmer implements the "UniNE" algorithm in:
 * <a href="https://doi.org/10.1145/1141277.1141523"><i>Light Stemming Approaches for the French,
 * Portuguese, German and Hungarian Languages</i></a> by Jacques Savoy (ACM SAC 2006). The inline
 * suffix literals mirror the suffix tables of the cited paper and are kept inline for fidelity
 * to the published algorithm.
 *
 * <p>Adapted from the identically named algorithm in Apache Lucene's analysis-common module.
 * Instances are stateless and safe for concurrent use by multiple threads; each instance is also
 * its own {@link StemmerFactory}. Input is expected to be lowercase, as produced by a
 * case-folding normalization step; the stemmer does not fold case itself.</p>
 */
@ThreadSafe
public final class PortugueseLightStemmer extends AbstractCharArrayStemmer
    implements StemmerFactory {
  /** {@inheritDoc} */
  @Override
  public Stemmer newStemmer() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  int stem(char[] s, int len) {
    if (len < 4) return len;

    len = removeSuffix(s, len);

    if (len > 3 && s[len - 1] == 'a') len = normFeminine(s, len);

    if (len > 4)
      switch (s[len - 1]) {
        case 'e':
        case 'a':
        case 'o':
          len--;
          break;
      }

    for (int i = 0; i < len; i++)
      switch (s[i]) {
        case '\u00E0':
        case '\u00E1':
        case '\u00E2':
        case '\u00E4':
        case '\u00E3':
          s[i] = 'a';
          break;
        case '\u00F2':
        case '\u00F3':
        case '\u00F4':
        case '\u00F6':
        case '\u00F5':
          s[i] = 'o';
          break;
        case '\u00E8':
        case '\u00E9':
        case '\u00EA':
        case '\u00EB':
          s[i] = 'e';
          break;
        case '\u00F9':
        case '\u00FA':
        case '\u00FB':
        case '\u00FC':
          s[i] = 'u';
          break;
        case '\u00EC':
        case '\u00ED':
        case '\u00EE':
        case '\u00EF':
          s[i] = 'i';
          break;
        case '\u00E7':
          s[i] = 'c';
          break;
      }

    return len;
  }

  private int removeSuffix(char[] s, int len) {
    if (len > 4 && endsWith(s, len, "es"))
      switch (s[len - 3]) {
        case 'r':
        case 's':
        case 'l':
        case 'z':
          return len - 2;
      }

    if (len > 3 && endsWith(s, len, "ns")) {
      s[len - 2] = 'm';
      return len - 1;
    }

    if (len > 4 && (endsWith(s, len, "eis") || endsWith(s, len, "\u00E9is"))) {
      s[len - 3] = 'e';
      s[len - 2] = 'l';
      return len - 1;
    }

    if (len > 4 && endsWith(s, len, "ais")) {
      s[len - 2] = 'l';
      return len - 1;
    }

    if (len > 4 && endsWith(s, len, "\u00F3is")) {
      s[len - 3] = 'o';
      s[len - 2] = 'l';
      return len - 1;
    }

    if (len > 4 && endsWith(s, len, "is")) {
      s[len - 1] = 'l';
      return len;
    }

    if (len > 3 && (endsWith(s, len, "\u00F5es") || endsWith(s, len, "\u00E3es"))) {
      len--;
      s[len - 2] = '\u00E3';
      s[len - 1] = 'o';
      return len;
    }

    if (len > 6 && endsWith(s, len, "mente")) return len - 5;

    if (len > 3 && s[len - 1] == 's') return len - 1;
    return len;
  }

  private int normFeminine(char[] s, int len) {
    if (len > 7
        && (endsWith(s, len, "inha") || endsWith(s, len, "iaca") || endsWith(s, len, "eira"))) {
      s[len - 1] = 'o';
      return len;
    }

    if (len > 6) {
      if (endsWith(s, len, "osa")
          || endsWith(s, len, "ica")
          || endsWith(s, len, "ida")
          || endsWith(s, len, "ada")
          || endsWith(s, len, "iva")
          || endsWith(s, len, "ama")) {
        s[len - 1] = 'o';
        return len;
      }

      if (endsWith(s, len, "ona")) {
        s[len - 3] = '\u00E3';
        s[len - 2] = 'o';
        return len - 1;
      }

      if (endsWith(s, len, "ora")) return len - 1;

      if (endsWith(s, len, "esa")) {
        s[len - 3] = '\u00EA';
        return len - 1;
      }

      if (endsWith(s, len, "na")) {
        s[len - 1] = 'o';
        return len;
      }
    }
    return len;
  }
}
