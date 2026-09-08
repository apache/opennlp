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
 * Minimal plural stemmer for English.
 *
 * <p>This stemmer implements the "S-Stemmer" from
 * <a href="https://doi.org/10.1002/(SICI)1097-4571(199101)42:1%3C7::AID-ASI2%3E3.0.CO;2-P">
 * <i>How Effective Is Suffixing?</i></a> by Donna Harman (JASIS 42(1), 1991).
 *
 * <p>Based on Apache Lucene's implementation. Instances are stateless, thread-safe, and implement
 * {@link StemmerFactory}. Input must use lowercase NFC; the stemmer does not apply case folding or
 * Unicode normalization.</p>
 *
 * @see <a href="https://github.com/apache/lucene/blob/4965e8d4d960445a0522fae512c60c6d8f11fc29/lucene/analysis/common/src/java/org/apache/lucene/analysis/en/EnglishMinimalStemmer.java">
 *     Apache Lucene EnglishMinimalStemmer</a>
 * @since 3.0.0
 */
@ThreadSafe
public final class EnglishMinimalStemmer extends AbstractCharArrayStemmer
    implements StemmerFactory {
  /** {@inheritDoc} */
  @Override
  public Stemmer newStemmer() {
    return new EnglishMinimalStemmer();
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("fallthrough")
  int stem(char[] s, int len) {
    if (len < 3 || s[len - 1] != 's') return len;

    switch (s[len - 2]) {
      case 'u':
      case 's':
        return len;
      case 'e':
        if (len > 3 && s[len - 3] == 'i' && s[len - 4] != 'a' && s[len - 4] != 'e') {
          s[len - 3] = 'y';
          return len - 2;
        }
        if (s[len - 3] == 'i' || s[len - 3] == 'a' || s[len - 3] == 'o' || s[len - 3] == 'e')
          return len; /* intentional fallthrough */
      default:
        return len - 1;
    }
  }
}
