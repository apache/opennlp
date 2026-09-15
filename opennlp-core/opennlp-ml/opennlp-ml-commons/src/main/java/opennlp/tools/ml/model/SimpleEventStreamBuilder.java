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

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringUtil;

public class SimpleEventStreamBuilder {

  private static final char OUTCOME_SEPARATOR = '/';
  private static final char VALUE_SEPARATOR = ';';
  private static final String FORMAT_ERROR = "format error of the event \"%s\"";

  private final List<Event> eventList = new ArrayList<>();
  private int pos = 0;

  /**
   * Adds one event. The outcome runs up to the first {@code /}; the contexts follow it, separated
   * by runs of whitespace under the Unicode {@code White_Space} property, see
   * {@link StringUtil#isUnicodeWhitespace(int)}, each with an optional value after a {@code ;}:
   * <pre>
   * other/w=he n1w=belongs n2w=to po=other pow=other,He powf=other,ic
   * other/w=he;0.5 n1w=belongs;0.4 n2w=to;0.3 po=other;0.5 pow=other,He;0.25 powf=other,ic;0.5
   * </pre>
   *
   * @param event The event text. Must not be {@code null}.
   * @return This builder.
   * @throws IllegalArgumentException Thrown if {@code event} is {@code null}, if the outcome or
   *         the contexts are missing, if the first context has a value and another one is not
   *         written as {@code name;value} with both parts present and no further {@code ;}, or
   *         if a value is negative.
   * @throws NumberFormatException Thrown if a value is not a number.
   */
  public SimpleEventStreamBuilder add(String event) {
    if (event == null) {
      throw new IllegalArgumentException("event must not be null");
    }
    int slash = event.indexOf(OUTCOME_SEPARATOR);
    if (slash < 1) {
      throw new IllegalArgumentException(String.format(FORMAT_ERROR, event));
    }
    String outcome = event.substring(0, slash);

    String[] cvPairs = StringUtil.splitOnUnicodeWhitespace(event.substring(slash + 1));
    if (cvPairs.length == 0) {
      throw new IllegalArgumentException(String.format(FORMAT_ERROR, event));
    }
    if (cvPairs[0].indexOf(VALUE_SEPARATOR) >= 0) {
      String[] context = new String[cvPairs.length];
      float[] values = new float[cvPairs.length];
      for (int i = 0; i < cvPairs.length; i++) {
        String pair = cvPairs[i];
        int separator = pair.indexOf(VALUE_SEPARATOR);
        if (separator < 1 || separator == pair.length() - 1
            || pair.indexOf(VALUE_SEPARATOR, separator + 1) >= 0) {
          throw new IllegalArgumentException(String.format(FORMAT_ERROR + ". \"%s\" is not name;value",
              event, pair));
        }
        context[i] = pair.substring(0, separator);
        values[i] = Float.parseFloat(pair.substring(separator + 1));
        if (values[i] < 0) {
          throw new IllegalArgumentException("Negative values are not allowed: " + pair);
        }
      }
      eventList.add(new Event(outcome, context, values));
    } else {
      eventList.add(new Event(outcome, cvPairs));
    }

    return this;
  }

  public ObjectStream<Event> build() {
    return () -> {
      if (eventList.size() <= pos) {
        return null;
      }
      return eventList.get(pos++);
    };
  }
}
