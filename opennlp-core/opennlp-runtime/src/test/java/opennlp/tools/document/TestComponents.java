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

package opennlp.tools.document;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

/**
 * Deterministic stand-in components shared by the document pipeline tests, so every
 * expected span in those tests follows directly from the definitions here.
 */
final class TestComponents {

  /**
   * A deterministic sentence detector that ends a sentence after every period and
   * expects a single space between sentences. Only the span-producing method is
   * implemented because the adapter calls no other method.
   */
  static final SentenceDetector PERIOD_SPLITTER = new SentenceDetector() {

    @Override
    public String[] sentDetect(CharSequence s) {
      throw new UnsupportedOperationException("the adapter only calls sentPosDetect");
    }

    @Override
    public Span[] sentPosDetect(CharSequence s) {
      final String text = s.toString();
      final List<Span> spans = new ArrayList<>();
      int start = 0;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '.') {
          spans.add(new Span(start, i + 1));
          start = i + 2;
        }
      }
      return spans.toArray(new Span[0]);
    }
  };

  /**
   * A deterministic tokenizer that splits on single space characters and keeps all
   * other characters, including sentence-final periods, attached to their token. Only
   * the span-producing method is implemented because the adapter calls no other method.
   */
  static final Tokenizer SPACE_TOKENIZER = new Tokenizer() {

    @Override
    public String[] tokenize(String s) {
      throw new UnsupportedOperationException("the adapter only calls tokenizePos");
    }

    @Override
    public Span[] tokenizePos(String s) {
      final List<Span> spans = new ArrayList<>();
      int start = -1;
      for (int i = 0; i <= s.length(); i++) {
        final boolean boundary = i == s.length() || s.charAt(i) == ' ';
        if (boundary && start >= 0) {
          spans.add(new Span(start, i));
          start = -1;
        } else if (!boundary && start < 0) {
          start = i;
        }
      }
      return spans.toArray(new Span[0]);
    }
  };

  private TestComponents() {
    // Not instantiated; this class provides shared test fixtures only.
  }
}
