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
package opennlp.geo;

import java.util.List;

import opennlp.tools.util.Span;

/**
 * The shared argument validation of this module's {@link opennlp.tools.geo.Geocoder}
 * implementations.
 */
final class GeocoderInput {

  /** Prevents instantiation; this is a static utility. */
  private GeocoderInput() {
  }

  /**
   * Validates the arguments of
   * {@link opennlp.tools.geo.Geocoder#resolve(CharSequence, List)}: the text and the
   * mention list must not be {@code null}, the list must not contain a {@code null} element,
   * and every mention must lie inside the text.
   *
   * @param text             The text the mentions refer into. Must not be {@code null}.
   * @param locationMentions The mention spans to validate. Must not be {@code null} or contain
   *                         {@code null} elements, and no span may end beyond the text.
   * @throws IllegalArgumentException Thrown if any of the above conditions is violated.
   */
  static void validateResolveArguments(CharSequence text, List<Span> locationMentions) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    if (locationMentions == null) {
      throw new IllegalArgumentException("LocationMentions must not be null");
    }
    for (final Span mention : locationMentions) {
      if (mention == null) {
        throw new IllegalArgumentException(
            "LocationMentions must not contain a null element, got: " + locationMentions);
      }
      if (mention.getEnd() > text.length()) {
        throw new IllegalArgumentException("Mention " + mention
            + " is outside the text, whose length is " + text.length());
      }
    }
  }
}
