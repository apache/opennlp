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

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;

/**
 * Minimal plural stemmer for Spanish.
 *
 * <p>This stemmer implements the "plurals" stemmer for the Spanish language.
 *
 * <p>Adapted from the identically named algorithm in Apache Lucene's analysis-common module.
 * Instances are stateless and safe for concurrent use by multiple threads; each instance is also
 * its own {@link StemmerFactory}. Input is expected to be lowercase, as produced by a
 * case-folding normalization step; the stemmer does not fold case itself.</p>
 */
@ThreadSafe
public final class SpanishMinimalStemmer extends AbstractCharArrayStemmer
    implements StemmerFactory {
  /** {@inheritDoc} */
  @Override
  public Stemmer newStemmer() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  int stem(char[] s, int len) {
    if (len < 4 || s[len - 1] != 's') return len;

    for (int i = 0; i < len; i++)
      switch (s[i]) {
        case '\u00E0':
        case '\u00E1':
        case '\u00E2':
        case '\u00E4':
          s[i] = 'a';
          break;
        case '\u00F2':
        case '\u00F3':
        case '\u00F4':
        case '\u00F6':
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
        case '\u00F1':
          s[i] = 'n';
          break;
      }

    switch (s[len - 1]) {
      case 's':
        if (s[len - 2] == 'a' || s[len - 2] == 'o') {
          return len - 1;
        }
        if (s[len - 2] == 'e') {
          if (s[len - 3] == 's' && s[len - 4] == 'e') {
            return len - 2;
          }
          if (s[len - 3] == 'c') {
            s[len - 3] = 'z';
            return len - 2;
          } else {
            return len - 2;
          }
        } else {
          return len - 1;
        }
    }

    return len;
  }
}
