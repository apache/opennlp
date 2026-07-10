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
package opennlp.embeddings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetensorsFileTest {

  // Builds a well-formed safetensors file: an 8-byte little-endian header length, the header
  // JSON verbatim, then the raw data bytes. The header's data_offsets are expected to already
  // be correct for the given data layout; callers construct both together.
  private static Path writeFile(Path dir, String name, String headerJson, byte[] data)
      throws IOException {
    final byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(headerBytes.length).array());
    out.write(headerBytes);
    out.write(data);
    final Path file = dir.resolve(name);
    Files.write(file, out.toByteArray());
    return file;
  }

  private static byte[] floatsToLittleEndianBytes(float... values) {
    final ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (float value : values) {
      buffer.putFloat(value);
    }
    return buffer.array();
  }

  @Test
  void testRoundTripsAFloat32Matrix(@TempDir Path dir) throws IOException {
    final float[] values = {1f, 2f, 3f, 4f, 5f, 6f};
    final byte[] data = floatsToLittleEndianBytes(values);
    final String header = "{\"weight\":{\"dtype\":\"F32\",\"shape\":[2,3],"
        + "\"data_offsets\":[0," + data.length + "]}}";
    final Path file = writeFile(dir, "model.safetensors", header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertEquals(1, parsed.size());
    assertEquals(Set.of("weight"), parsed.tensorNames());
    final TensorInfo info = parsed.tensorInfo("weight");
    assertEquals("F32", info.dtype());
    assertArrayEquals(new int[] {2, 3}, info.shape());
    assertEquals(6, info.elementCount());
    assertArrayEquals(values, parsed.readFloat32("weight"));
  }

  @Test
  void testMultipleTensorsPreserveHeaderOrder(@TempDir Path dir) throws IOException {
    final byte[] a = floatsToLittleEndianBytes(1f, 2f);
    final byte[] b = floatsToLittleEndianBytes(3f, 4f, 5f);
    final String header = "{\"first\":{\"dtype\":\"F32\",\"shape\":[2],"
        + "\"data_offsets\":[0," + a.length + "]},"
        + "\"second\":{\"dtype\":\"F32\",\"shape\":[3],"
        + "\"data_offsets\":[" + a.length + "," + (a.length + b.length) + "]}}";
    final ByteArrayOutputStream data = new ByteArrayOutputStream();
    data.write(a);
    data.write(b);
    final Path file = writeFile(dir, "model.safetensors", header, data.toByteArray());

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertEquals(java.util.List.of("first", "second"), java.util.List.copyOf(parsed.tensorNames()));
    assertArrayEquals(new float[] {1f, 2f}, parsed.readFloat32("first"));
    assertArrayEquals(new float[] {3f, 4f, 5f}, parsed.readFloat32("second"));
  }

  @Test
  void testMetadataMapIsParsed(@TempDir Path dir) throws IOException {
    final byte[] data = floatsToLittleEndianBytes(1f);
    final String header = "{\"__metadata__\":{\"format\":\"pt\",\"note\":\"line\\nbreak\"},"
        + "\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0," + data.length + "]}}";
    final Path file = writeFile(dir, "model.safetensors", header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertEquals("pt", parsed.metadata().get("format"));
    assertEquals("line\nbreak", parsed.metadata().get("note"));
    assertEquals(1, parsed.size());
  }

  @Test
  void testUnknownHeaderFieldsAreSkipped(@TempDir Path dir) throws IOException {
    final byte[] data = floatsToLittleEndianBytes(1f, 2f);
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[2],"
        + "\"data_offsets\":[0," + data.length + "],\"future_field\":{\"nested\":[1,2,3]}}}";
    final Path file = writeFile(dir, "model.safetensors", header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertArrayEquals(new float[] {1f, 2f}, parsed.readFloat32("w"));
  }

  @Test
  void testSingleMatrixTensorNameFindsTheOnly2DFloat32Tensor(@TempDir Path dir) throws IOException {
    final byte[] scalar = floatsToLittleEndianBytes(9f);
    final byte[] matrix = floatsToLittleEndianBytes(1f, 2f, 3f, 4f);
    final String header = "{\"bias\":{\"dtype\":\"F32\",\"shape\":[1],"
        + "\"data_offsets\":[0," + scalar.length + "]},"
        + "\"embeddings\":{\"dtype\":\"F32\",\"shape\":[2,2],"
        + "\"data_offsets\":[" + scalar.length + "," + (scalar.length + matrix.length) + "]}}";
    final ByteArrayOutputStream data = new ByteArrayOutputStream();
    data.write(scalar);
    data.write(matrix);
    final Path file = writeFile(dir, "model.safetensors", header, data.toByteArray());

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertEquals("embeddings", parsed.singleMatrixTensorName());
  }

  @Test
  void testSingleMatrixTensorNameRejectsAmbiguity(@TempDir Path dir) throws IOException {
    final byte[] a = floatsToLittleEndianBytes(1f, 2f, 3f, 4f);
    final byte[] b = floatsToLittleEndianBytes(5f, 6f, 7f, 8f);
    final String header = "{\"a\":{\"dtype\":\"F32\",\"shape\":[2,2],"
        + "\"data_offsets\":[0," + a.length + "]},"
        + "\"b\":{\"dtype\":\"F32\",\"shape\":[2,2],"
        + "\"data_offsets\":[" + a.length + "," + (a.length + b.length) + "]}}";
    final ByteArrayOutputStream data = new ByteArrayOutputStream();
    data.write(a);
    data.write(b);
    final Path file = writeFile(dir, "model.safetensors", header, data.toByteArray());

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertThrows(IllegalArgumentException.class, parsed::singleMatrixTensorName);
  }

  @Test
  void testSingleMatrixTensorNameRejectsNoCandidate(@TempDir Path dir) throws IOException {
    final byte[] data = floatsToLittleEndianBytes(1f);
    final String header = "{\"bias\":{\"dtype\":\"F32\",\"shape\":[1],"
        + "\"data_offsets\":[0," + data.length + "]}}";
    final Path file = writeFile(dir, "model.safetensors", header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertThrows(IllegalArgumentException.class, parsed::singleMatrixTensorName);
  }

  @Test
  void testReadFloat32RejectsWrongDtype(@TempDir Path dir) throws IOException {
    final byte[] data = new byte[] {1, 2};
    final String header = "{\"ids\":{\"dtype\":\"I64\",\"shape\":[1],"
        + "\"data_offsets\":[0,2]}}";
    final Path file = writeFile(dir, "model.safetensors", header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> parsed.readFloat32("ids"));
    assertTrue(e.getMessage().contains("I64"));
  }

  @Test
  void testTensorInfoRejectsUnknownName(@TempDir Path dir) throws IOException {
    final byte[] data = floatsToLittleEndianBytes(1f);
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4]}}";
    final Path file = writeFile(dir, "model.safetensors", header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertThrows(IllegalArgumentException.class, () -> parsed.tensorInfo("missing"));
  }

  @Test
  void testRejectsNullAndMissingFile(@TempDir Path dir) {
    assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(null));
    assertThrows(IllegalArgumentException.class,
        () -> SafetensorsFile.read(dir.resolve("absent.safetensors")));
  }

  @Test
  void testRejectsFileShorterThanTheLengthPrefix(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("truncated.safetensors");
    Files.write(file, new byte[] {1, 2, 3});

    assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
  }

  @Test
  void testRejectsHeaderLengthLargerThanTheFile(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("bad-length.safetensors");
    final byte[] prefix = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(1000L).array();
    Files.write(file, prefix);

    assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
  }

  @Test
  void testRejectsDuplicateTensorName(@TempDir Path dir) throws IOException {
    // The same key twice is syntactically valid JSON (just semantically ambiguous), so the
    // header parser itself does not reject it; SafetensorsFile's post-parse check does.
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4]},"
        + "\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4]}}";
    final Path file = writeFile(dir, "model.safetensors", header, new byte[] {1, 2, 3, 4});

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
    assertTrue(e.getMessage().contains("more than once"));
  }

  @Test
  void testRejectsTensorMissingRequiredField(@TempDir Path dir) throws IOException {
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[1]}}";
    final Path file = writeFile(dir, "model.safetensors", header, new byte[0]);

    assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
  }

  @Test
  void testRejectsDataOffsetsOutOfRange(@TempDir Path dir) throws IOException {
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,999]}}";
    final Path file = writeFile(dir, "model.safetensors", header, new byte[] {1, 2, 3, 4});

    assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
  }

  @Test
  void testRejectsUnterminatedString(@TempDir Path dir) throws IOException {
    final String header = "{\"w\":{\"dtype\":\"F32";
    final Path file = writeFile(dir, "model.safetensors", header, new byte[0]);

    assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
  }

  @Test
  void testRejectsTensorLargerThanAJavaArray(@TempDir Path dir) throws IOException {
    // 2_000_000 * 2_000 = 4 billion elements, over the float[] ceiling. The bogus small data
    // range keeps the file tiny; the array-ceiling check fires before the range-mismatch check
    // because it subsumes it for tensors this large.
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[2000000,2000],"
        + "\"data_offsets\":[0,4]}}";
    final Path file = writeFile(dir, "model.safetensors", header, new byte[] {1, 2, 3, 4});

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> parsed.readFloat32("w"));
    assertTrue(e.getMessage().contains("more than a Java array can hold"));
  }

  @Test
  void testFailsLoudWhenTheFileIsTruncatedAfterRead(@TempDir Path dir) throws IOException {
    // Tensor data is streamed on demand rather than held in memory, so a file that shrinks
    // between read() and readFloat32() must fail loud, not return partial data.
    final byte[] data = floatsToLittleEndianBytes(1f, 2f);
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[2],"
        + "\"data_offsets\":[0," + data.length + "]}}";
    final Path file = writeFile(dir, "model.safetensors", header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);
    writeFile(dir, "model.safetensors", header, floatsToLittleEndianBytes(1f));

    final IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> parsed.readFloat32("w"));
    assertTrue(e.getMessage().contains("truncated"));
  }

  @Test
  void testReadFloat32RejectsElementCountByteRangeMismatch(@TempDir Path dir) throws IOException {
    // Shape [2] declares two F32 elements (8 bytes) but the data range holds only one.
    final byte[] data = floatsToLittleEndianBytes(1f);
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[2],"
        + "\"data_offsets\":[0," + data.length + "]}}";
    final Path file = writeFile(dir, "model.safetensors", header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> parsed.readFloat32("w"));
    assertTrue(e.getMessage().contains("2 F32 elements"), e.getMessage());
  }

  @Test
  void testTensorInfoShapeIsDefensivelyCopied() {
    final int[] shape = {2, 3};
    final TensorInfo info = new TensorInfo("t", "F32", shape, 0, 24);
    shape[0] = 99;
    assertEquals(2, info.shape()[0], "construction must copy the caller's array");
    info.shape()[0] = 99;
    assertEquals(2, info.shape()[0], "the accessor must return a copy");
    assertEquals(6, info.elementCount());
  }

  @Test
  void testTensorInfoEqualsByValue() {
    final TensorInfo a = new TensorInfo("t", "F32", new int[] {2, 3}, 0, 24);
    final TensorInfo b = new TensorInfo("t", "F32", new int[] {2, 3}, 0, 24);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void testTensorInfoElementCountOverflowFailsLoudly() {
    final TensorInfo crafted = new TensorInfo("t", "F32",
        new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, 0, 8);
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, crafted::elementCount);
    assertTrue(e.getMessage().contains("overflows"), e.getMessage());
  }
}
