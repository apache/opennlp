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

package opennlp.spellcheck.dictionary;

import java.io.IOException;

/**
 * Signals that a line in a plain-text frequency dictionary did not match the expected
 * {@code word<TAB>count} (or {@code w1 w2<TAB>count}) shape and could not be parsed.
 *
 * <p>The (1-based) line number and a truncated copy of the offending line are recorded
 * to make the failure easy to locate in large dictionaries.</p>
 */
public class MalformedDictionaryLineException extends IOException {

  private static final long serialVersionUID = 1L;

  /** Offending lines are truncated to this length in the message. */
  private static final int MAX_LINE_PREVIEW = 120;

  private final long lineNumber;

  /**
   * Creates a new exception.
   *
   * @param lineNumber the 1-based number of the offending line
   * @param line       the raw text of the offending line
   * @param reason     a human-readable description of what was expected
   */
  public MalformedDictionaryLineException(long lineNumber, String line, String reason) {
    super("malformed dictionary line " + lineNumber + " (" + reason + "): " + preview(line));
    this.lineNumber = lineNumber;
  }

  /** @return the 1-based number of the offending line. */
  public long getLineNumber() {
    return lineNumber;
  }

  private static String preview(String line) {
    if (line == null) {
      return "<null>";
    }
    final String escaped = line.replace("\t", "\\t");
    if (escaped.length() <= MAX_LINE_PREVIEW) {
      return '"' + escaped + '"';
    }
    return '"' + escaped.substring(0, MAX_LINE_PREVIEW) + "...\"";
  }
}
