/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractModelReaderPatternCountTest {

  private static class TestableReader extends AbstractModelReader {

    TestableReader(int predicateCount) {
      super(new BinaryFileDataReader(new DataInputStream(new ByteArrayInputStream(new byte[0]))));
      NUM_PREDS = predicateCount;
    }

    TestableReader(DataReader reader) {
      super(reader);
    }

    @Override
    public void checkModelType() {}

    @Override
    public AbstractModel constructModel() { return null; }

    Context[] parameters(int[][] patterns) throws IOException {
      return getParameters(patterns);
    }

    int[][] patterns() throws IOException {
      return getOutcomePatterns();
    }
  }

  private static Stream<Arguments> invalidPatterns() {
    return Stream.of(
        Arguments.of(new int[][] {{-1}}, "negative"),
        Arguments.of(new int[][] {{2}}, "exceeds"),
        Arguments.of(new int[][] {{0}}, "do not match"),
        Arguments.of(new int[][] {{}}, "empty"),
        Arguments.of(new int[][] {{Integer.MAX_VALUE}}, "exceeds"),
        Arguments.of(new int[][] {{1}, {1}}, "exceeds"));
  }

  @ParameterizedTest
  @MethodSource("invalidPatterns")
  void rejectsInvalidPatternCounts(int[][] patterns, String message) {
    InvalidFormatException exception = assertThrows(InvalidFormatException.class,
        () -> new TestableReader(1).parameters(patterns));
    assertNotNull(exception.getMessage());
    assertTrue(exception.getMessage().contains(message));
  }

  @Test
  void acceptsZeroCountAndMatchingTotal() throws IOException {
    Context[] contexts = new TestableReader(1).parameters(new int[][] {{0}, {1}});
    assertEquals(1, contexts.length);
    assertNotNull(contexts[0]);
  }

  @Test
  void rejectsNonNumericPatternCount() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(1);
      output.writeUTF("invalid 0");
    }
    TestableReader reader = new TestableReader(new BinaryFileDataReader(
        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    InvalidFormatException exception = assertThrows(InvalidFormatException.class, reader::patterns);
    assertTrue(exception.getMessage().contains("invalid"));
  }
}
