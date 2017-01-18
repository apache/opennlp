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

package opennlp.tools.sentdetect;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.Span;

/**
 * The Newline Sentence Detector assumes that sentences are line delimited and
 * recognizes one sentence per non-empty line.
 */
public class NewlineSentenceDetector implements SentenceDetector {

  public String[] sentDetect(String s) {
    return Span.spansToStrings(sentPosDetect(s), s);
  }

  public Span[] sentPosDetect(String s) {

    List<Span> sentences = new ArrayList<>();

    int start = 0;

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);

      if (c == '\n' || c == '\r') {
        if (i - start > 0) {
          Span span = new Span(start, i).trim(s);
          if (span.length() > 0) {
            sentences.add(span);
          }

          start = i + 1;
        }
      }
    }

    if (s.length() - start > 0) {
      Span span = new Span(start, s.length()).trim(s);
      if (span.length() > 0) {
        sentences.add(span);
      }
    }

    return sentences.toArray(new Span[sentences.size()]);
  }
}
