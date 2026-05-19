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

package opennlp.tools.stopword;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.CollectionObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Unit tests for {@link StopwordFilterStream}.
 */
public class StopwordFilterStreamTest {

  private static final Set<String> STOP = Set.of("the", "quick");

  private static StopwordFilter newFilter() {
    return new StopwordFilter() {
      @Override
      public boolean isStopword(CharSequence token) {
        return token != null && STOP.contains(token.toString().toLowerCase());
      }

      @Override
      public boolean isStopword(String... tokens) {
        if (tokens == null || tokens.length == 0) {
          return false;
        }
        if (tokens.length == 1) {
          return isStopword((CharSequence) tokens[0]);
        }
        return false;
      }

      @Override
      public String[] filter(String[] tokens) {
        if (tokens == null) {
          return new String[0];
        }
        List<String> kept = new ArrayList<>(tokens.length);
        for (String t : tokens) {
          if (!isStopword((CharSequence) t)) {
            kept.add(t);
          }
        }
        return kept.toArray(new String[0]);
      }

      @Override
      public boolean isCaseSensitive() {
        return false;
      }

      @Override
      public Set<String> stopwords() {
        return Collections.unmodifiableSet(STOP);
      }
    };
  }

  private static ObjectStream<String[]> samplesStream() {
    return new CollectionObjectStream<>(Arrays.asList(
        new String[] {"The", "quick", "brown", "fox"},
        new String[] {"The", "lazy", "dog"}
    ));
  }

  @Test
  void readReturnsFilteredArraysInOrderAndThenNull() throws IOException {
    try (StopwordFilterStream stream = new StopwordFilterStream(samplesStream(), newFilter())) {
      Assertions.assertArrayEquals(new String[] {"brown", "fox"}, stream.read());
      Assertions.assertArrayEquals(new String[] {"lazy", "dog"}, stream.read());
      Assertions.assertNull(stream.read());
    }
  }

  @Test
  void resetRewindsToFirstFilteredArray() throws IOException {
    try (StopwordFilterStream stream = new StopwordFilterStream(samplesStream(), newFilter())) {
      Assertions.assertArrayEquals(new String[] {"brown", "fox"}, stream.read());
      Assertions.assertArrayEquals(new String[] {"lazy", "dog"}, stream.read());
      Assertions.assertNull(stream.read());

      stream.reset();

      Assertions.assertArrayEquals(new String[] {"brown", "fox"}, stream.read());
    }
  }

  @Test
  void nullSamplesThrowsIae() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new StopwordFilterStream(null, newFilter()));
  }

  @Test
  void nullFilterThrowsIae() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new StopwordFilterStream(samplesStream(), null));
  }
}
