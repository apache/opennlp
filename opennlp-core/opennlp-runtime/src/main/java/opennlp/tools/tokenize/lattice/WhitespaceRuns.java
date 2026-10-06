/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.tokenize.lattice;

import opennlp.tools.util.StringUtil;

/**
 * Splits a text into the maximal stretches that contain no whitespace, as judged by
 * {@link StringUtil#isWhitespace(char)}.
 */
final class WhitespaceRuns {

  /** Receives one whitespace-free stretch of a text. */
  @FunctionalInterface
  interface RunHandler {

    /**
     * Handles one stretch.
     *
     * @param from The stretch start.
     * @param to The exclusive stretch end.
     */
    void accept(int from, int to);
  }

  private WhitespaceRuns() {
  }

  /**
   * Passes each whitespace-free stretch of {@code text} to {@code handler}, in text order.
   *
   * @param text The text to split.
   * @param handler Receives each stretch.
   */
  static void forEachNonWhitespaceRun(String text, RunHandler handler) {
    int start = 0;
    while (start < text.length()) {
      if (StringUtil.isWhitespace(text.charAt(start))) {
        start++;
        continue;
      }
      int end = start;
      while (end < text.length() && !StringUtil.isWhitespace(text.charAt(end))) {
        end++;
      }
      handler.accept(start, end);
      start = end;
    }
  }
}
