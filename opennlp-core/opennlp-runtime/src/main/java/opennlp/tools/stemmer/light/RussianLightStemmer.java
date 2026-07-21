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
 * Light Stemmer for Russian.
 *
 * <p>This stemmer implements the following algorithm:
 * <a href="https://doi.org/10.1002/asi.21191"><i>Indexing and Searching Strategies for the
 * Russian Language</i></a> by Ljiljana Dolamic and Jacques Savoy (JASIST 60(12), 2009). The
 * inline suffix literals mirror the suffix tables of the cited paper and are kept inline for
 * fidelity to the published algorithm.
 *
 * <p>Adapted from the identically named algorithm in Apache Lucene's analysis-common module.
 * Instances are stateless and safe for concurrent use by multiple threads; each instance is also
 * its own {@link StemmerFactory}. Input is expected to be lowercase, as produced by a
 * case-folding normalization step; the stemmer does not fold case itself.</p>
 */
@ThreadSafe
public final class RussianLightStemmer extends AbstractCharArrayStemmer
    implements StemmerFactory {
  /** {@inheritDoc} */
  @Override
  public Stemmer newStemmer() {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  int stem(char[] s, int len) {
    len = removeCase(s, len);
    return normalize(s, len);
  }

  private int normalize(char[] s, int len) {
    if (len > 3)
      switch (s[len - 1]) {
        case '\u044C':
        case '\u0438':
          return len - 1;
        case '\u043D':
          if (s[len - 2] == '\u043D') return len - 1;
      }
    return len;
  }

  private int removeCase(char[] s, int len) {
    if (len > 6
        && (endsWith(s, len, "\u0438\u044F\u043C\u0438")
            || endsWith(s, len, "\u043E\u044F\u043C\u0438"))) return len - 4;

    if (len > 5
        && (endsWith(s, len, "\u0438\u044F\u043C")
            || endsWith(s, len, "\u0438\u044F\u0445")
            || endsWith(s, len, "\u043E\u044F\u0445")
            || endsWith(s, len, "\u044F\u043C\u0438")
            || endsWith(s, len, "\u043E\u044F\u043C")
            || endsWith(s, len, "\u043E\u044C\u0432")
            || endsWith(s, len, "\u0430\u043C\u0438")
            || endsWith(s, len, "\u0435\u0433\u043E")
            || endsWith(s, len, "\u0435\u043C\u0443")
            || endsWith(s, len, "\u0435\u0440\u0438")
            || endsWith(s, len, "\u0438\u043C\u0438")
            || endsWith(s, len, "\u043E\u0433\u043E")
            || endsWith(s, len, "\u043E\u043C\u0443")
            || endsWith(s, len, "\u044B\u043C\u0438")
            || endsWith(s, len, "\u043E\u0435\u0432"))) return len - 3;

    if (len > 4
        && (endsWith(s, len, "\u0430\u044F")
            || endsWith(s, len, "\u044F\u044F")
            || endsWith(s, len, "\u044F\u0445")
            || endsWith(s, len, "\u044E\u044E")
            || endsWith(s, len, "\u0430\u0445")
            || endsWith(s, len, "\u0435\u044E")
            || endsWith(s, len, "\u0438\u0445")
            || endsWith(s, len, "\u0438\u044F")
            || endsWith(s, len, "\u0438\u044E")
            || endsWith(s, len, "\u044C\u0432")
            || endsWith(s, len, "\u043E\u044E")
            || endsWith(s, len, "\u0443\u044E")
            || endsWith(s, len, "\u044F\u043C")
            || endsWith(s, len, "\u044B\u0445")
            || endsWith(s, len, "\u0435\u044F")
            || endsWith(s, len, "\u0430\u043C")
            || endsWith(s, len, "\u0435\u043C")
            || endsWith(s, len, "\u0435\u0439")
            || endsWith(s, len, "\u0451\u043C")
            || endsWith(s, len, "\u0435\u0432")
            || endsWith(s, len, "\u0438\u0439")
            || endsWith(s, len, "\u0438\u043C")
            || endsWith(s, len, "\u043E\u0435")
            || endsWith(s, len, "\u043E\u0439")
            || endsWith(s, len, "\u043E\u043C")
            || endsWith(s, len, "\u043E\u0432")
            || endsWith(s, len, "\u044B\u0435")
            || endsWith(s, len, "\u044B\u0439")
            || endsWith(s, len, "\u044B\u043C")
            || endsWith(s, len, "\u043C\u0438"))) return len - 2;

    if (len > 3)
      switch (s[len - 1]) {
        case '\u0430':
        case '\u0435':
        case '\u0438':
        case '\u043E':
        case '\u0443':
        case '\u0439':
        case '\u044B':
        case '\u044F':
        case '\u044C':
          return len - 1;
      }

    return len;
  }
}
