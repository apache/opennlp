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

import java.util.Arrays;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringUtil;

/**
 * Field delimiters and messages shared by {@link FileEventStream},
 * {@link RealValueFileEventStream} and {@link SimpleEventStreamBuilder}.
 */
final class EventFields {

  /** Message prefix for a negative context value; the offending context follows. */
  private static final String NEGATIVE_VALUE = "Negative values are not allowed: ";

  /** Message prefix for a NaN or infinite context value; the offending context follows. */
  private static final String NON_FINITE_VALUE = "Values must be finite: ";

  /** Message for a blank line; the line is filled in. */
  private static final String MISSING_OUTCOME = "An event line must start with an outcome: \"%s\"";

  /** Space, tab, carriage return, line feed and form feed. */
  private static final char[] SEPARATORS = {' ', '\t', '\r', '\n', '\f'};

  private EventFields() {
  }

  /**
   * The fields of one event line.
   *
   * @param outcome The outcome.
   * @param contexts The contexts, possibly empty.
   */
  record EventLine(String outcome, String[] contexts) {
  }

  /**
   * Splits on runs of space, tab, carriage return, line feed, and form feed. Other
   * characters remain in the fields, and no empty fields are produced.
   *
   * @param text The event text, already checked for {@code null} by the caller.
   * @return The fields in order.
   */
  static String[] split(String text) {
    return StringUtil.splitNonEmpty(text, SEPARATORS);
  }

  /**
   * Splits an event line into its outcome and contexts, see {@link #split(String)}.
   *
   * @param line The event line, already checked for {@code null} by the caller.
   * @return The first field as outcome and the other fields as contexts.
   * @throws InvalidFormatException Thrown if {@code line} is empty or contains only separators.
   */
  static EventLine splitLine(String line) throws InvalidFormatException {
    String[] fields = split(line);
    if (fields.length == 0) {
      throw new InvalidFormatException(String.format(MISSING_OUTCOME, line));
    }
    return new EventLine(fields[0], Arrays.copyOfRange(fields, 1, fields.length));
  }

  /**
   * Checks that a context value is finite and not negative.
   *
   * @param value The parsed value.
   * @param context The context named in the message.
   * @throws IllegalArgumentException Thrown if {@code value} is negative, NaN or infinite.
   */
  static void requireValidValue(float value, String context) {
    if (!Float.isFinite(value)) {
      throw new IllegalArgumentException(NON_FINITE_VALUE + context);
    }
    if (value < 0) {
      throw new IllegalArgumentException(NEGATIVE_VALUE + context);
    }
  }
}
