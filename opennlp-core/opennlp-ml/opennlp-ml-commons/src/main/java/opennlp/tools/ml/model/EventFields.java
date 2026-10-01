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
package opennlp.tools.ml.model;

import opennlp.tools.util.StringUtil;

/**
 * Field delimiters and messages shared by {@link FileEventStream},
 * {@link RealValueFileEventStream} and {@link SimpleEventStreamBuilder}.
 */
final class EventFields {

  /** Message prefix for a negative context value; the offending context follows. */
  static final String NEGATIVE_VALUE = "Negative values are not allowed: ";

  /** Message prefix for a NaN or infinite context value; the offending context follows. */
  static final String NON_FINITE_VALUE = "Values must be finite: ";

  /** Message prefix for a blank line; the quoted line follows. */
  static final String MISSING_OUTCOME = "An event line must start with an outcome: \"";

  private static final char SPACE = ' ';
  private static final char TAB = '\t';
  private static final char CARRIAGE_RETURN = '\r';
  private static final char LINE_FEED = '\n';
  private static final char FORM_FEED = '\f';

  private EventFields() {
  }

  /**
   * Splits on runs of space, tab, carriage return, line feed, and form feed. Other
   * characters remain in the fields, and no empty fields are produced.
   *
   * @param text The event text, already checked for {@code null} by the caller.
   * @return The fields in order.
   */
  static String[] split(String text) {
    return StringUtil.splitNonEmpty(text, SPACE, TAB, CARRIAGE_RETURN, LINE_FEED, FORM_FEED);
  }
}
