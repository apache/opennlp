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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that crafted model files with oversized count fields are rejected before array
 * allocation occurs, preventing OOM DoS. See OPENNLP-1821.
 */
class AbstractModelReaderOomTest {

  /**
   * Minimal concrete subclass that exposes the three protected methods under test.
   */
  static class TestableReader extends AbstractModelReader {
    TestableReader(DataReader dr) { super(dr); }

    @Override public void checkModelType() {}
    @Override public AbstractModel constructModel() { return null; }

    String[] outcomes() throws IOException { return getOutcomes(); }
    int[][] outcomePatterns() throws IOException { return getOutcomePatterns(); }
    String[] predicates() throws IOException { return getPredicates(); }
  }

  /** Reader whose stream starts with a single int (the count field). */
  private static TestableReader readerFor(int countValue) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeInt(countValue);
    dos.flush();
    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
    return new TestableReader(new BinaryFileDataReader(dis));
  }

  @Test
  void testGetOutcomes_RejectsMaxValue() throws IOException {
    assertThrows(IllegalArgumentException.class, readerFor(Integer.MAX_VALUE)::outcomes);
  }

  @Test
  void testGetOutcomePatterns_RejectsMaxValue() throws IOException {
    assertThrows(IllegalArgumentException.class, readerFor(Integer.MAX_VALUE)::outcomePatterns);
  }

  @Test
  void testGetPredicates_RejectsMaxValue() throws IOException {
    assertThrows(IllegalArgumentException.class, readerFor(Integer.MAX_VALUE)::predicates);
  }

  @Test
  void testGetOutcomes_RejectsNegativeCount() throws IOException {
    assertThrows(IllegalArgumentException.class, readerFor(-1)::outcomes);
  }

  @Test
  void testGetOutcomePatterns_RejectsNegativeCount() throws IOException {
    assertThrows(IllegalArgumentException.class, readerFor(-1)::outcomePatterns);
  }

  @Test
  void testGetPredicates_RejectsNegativeCount() throws IOException {
    assertThrows(IllegalArgumentException.class, readerFor(-1)::predicates);
  }

  @Test
  void testGetOutcomes_ValidCountReturnsLabels() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeInt(2);
    dos.writeUTF("label-A");
    dos.writeUTF("label-B");
    dos.flush();

    TestableReader reader = new TestableReader(
        new BinaryFileDataReader(new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))));
    assertArrayEquals(new String[]{"label-A", "label-B"}, reader.outcomes());
  }

  @Test
  void testGetPredicates_ValidCountReturnsLabels() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeInt(3);
    dos.writeUTF("pred-X");
    dos.writeUTF("pred-Y");
    dos.writeUTF("pred-Z");
    dos.flush();

    TestableReader reader = new TestableReader(
        new BinaryFileDataReader(new DataInputStream(new ByteArrayInputStream(baos.toByteArray()))));
    assertArrayEquals(new String[]{"pred-X", "pred-Y", "pred-Z"}, reader.predicates());
  }
}
