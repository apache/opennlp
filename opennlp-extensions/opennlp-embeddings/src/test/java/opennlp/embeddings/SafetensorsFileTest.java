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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetensorsFileTest {

  private static final String MODEL_FILE_NAME = "model.safetensors";

  // Builds the header JSON of a file holding one tensor, for tests that hand-roll headers with
  // deliberately odd dtypes, shapes, or data_offsets.
  private static String singleTensorHeader(String name, String dtype, String shape,
                                           long begin, long end) {
    return "{\"" + name + "\":{\"dtype\":\"" + dtype + "\",\"shape\":" + shape
        + ",\"data_offsets\":[" + begin + "," + end + "]}}";
  }

  // Builds a safetensors file byte for byte: an 8-byte little-endian header length, the header
  // JSON verbatim, then the raw data bytes. Used by the negative tests whose headers
  // SafetensorsTestFiles would refuse to write; well-formed fixtures use that helper instead.
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
    final Path file = dir.resolve(MODEL_FILE_NAME);
    SafetensorsTestFiles.write(file,
        SafetensorsTestFiles.matrix("weight", new float[][] {{1f, 2f, 3f}, {4f, 5f, 6f}}));

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertEquals(1, parsed.size());
    assertEquals(Set.of("weight"), parsed.tensorNames());
    final TensorInfo info = parsed.tensorInfo("weight");
    assertEquals("F32", info.dtype());
    assertArrayEquals(new int[] {2, 3}, info.shape());
    assertEquals(6, info.elementCount());
    assertArrayEquals(new float[] {1f, 2f, 3f, 4f, 5f, 6f}, parsed.readFloat32("weight"));
  }

  @Test
  void testMultipleTensorsPreserveHeaderOrder(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve(MODEL_FILE_NAME);
    SafetensorsTestFiles.write(file,
        SafetensorsTestFiles.vector("first", new float[] {1f, 2f}),
        SafetensorsTestFiles.vector("second", new float[] {3f, 4f, 5f}));

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertEquals(List.of("first", "second"), List.copyOf(parsed.tensorNames()));
    assertArrayEquals(new float[] {1f, 2f}, parsed.readFloat32("first"));
    assertArrayEquals(new float[] {3f, 4f, 5f}, parsed.readFloat32("second"));
  }

  @Test
  void testMetadataMapIsParsed(@TempDir Path dir) throws IOException {
    final byte[] data = floatsToLittleEndianBytes(1f);
    final String header = "{\"__metadata__\":{\"format\":\"pt\",\"note\":\"line\\nbreak\"},"
        + "\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0," + data.length + "]}}";
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, data);

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
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertArrayEquals(new float[] {1f, 2f}, parsed.readFloat32("w"));
  }

  @Test
  void testSingleMatrixTensorNameFindsTheOnly2DFloat32Tensor(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve(MODEL_FILE_NAME);
    SafetensorsTestFiles.write(file,
        SafetensorsTestFiles.vector("bias", new float[] {9f}),
        SafetensorsTestFiles.matrix("embeddings", new float[][] {{1f, 2f}, {3f, 4f}}));

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertEquals("embeddings", parsed.singleMatrixTensorName());
  }

  @Test
  void testSingleMatrixTensorNameRejectsAmbiguity(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve(MODEL_FILE_NAME);
    SafetensorsTestFiles.write(file,
        SafetensorsTestFiles.matrix("a", new float[][] {{1f, 2f}, {3f, 4f}}),
        SafetensorsTestFiles.matrix("b", new float[][] {{5f, 6f}, {7f, 8f}}));

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertThrows(IllegalArgumentException.class, parsed::singleMatrixTensorName);
  }

  @Test
  void testSingleMatrixTensorNameRejectsNoCandidate(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve(MODEL_FILE_NAME);
    SafetensorsTestFiles.write(file, SafetensorsTestFiles.vector("bias", new float[] {1f}));

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    assertThrows(IllegalArgumentException.class, parsed::singleMatrixTensorName);
  }

  @Test
  void testReadFloat32RejectsWrongDtype(@TempDir Path dir) throws IOException {
    final byte[] data = new byte[] {1, 2};
    final String header = singleTensorHeader("ids", "I64", "[1]", 0, 2);
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> parsed.readFloat32("ids"));
    assertTrue(e.getMessage().contains("I64"));
  }

  @Test
  void testTensorInfoRejectsUnknownName(@TempDir Path dir) throws IOException {
    final byte[] data = floatsToLittleEndianBytes(1f);
    final String header = singleTensorHeader("w", "F32", "[1]", 0, 4);
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, data);

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
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, new byte[] {1, 2, 3, 4});

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
    assertTrue(e.getMessage().contains("more than once"));
  }

  @Test
  void testRejectsTensorMissingRequiredField(@TempDir Path dir) throws IOException {
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[1]}}";
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, new byte[0]);

    assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
  }

  @Test
  void testRejectsDataOffsetsOutOfRange(@TempDir Path dir) throws IOException {
    final String header = singleTensorHeader("w", "F32", "[1]", 0, 999);
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, new byte[] {1, 2, 3, 4});

    assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
  }

  @Test
  void testRejectsUnterminatedString(@TempDir Path dir) throws IOException {
    final String header = "{\"w\":{\"dtype\":\"F32";
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, new byte[0]);

    assertThrows(IllegalArgumentException.class, () -> SafetensorsFile.read(file));
  }

  @Test
  void testRejectsTensorLargerThanAJavaArray(@TempDir Path dir) throws IOException {
    // 2_000_000 * 2_000 = 4 billion elements, over the float[] ceiling. The bogus small data
    // range keeps the file tiny; the array-ceiling check fires before the range-mismatch check
    // because it subsumes it for tensors this large.
    final String header = singleTensorHeader("w", "F32", "[2000000,2000]", 0, 4);
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, new byte[] {1, 2, 3, 4});

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
    final String header = singleTensorHeader("w", "F32", "[2]", 0, data.length);
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, data);

    final SafetensorsFile parsed = SafetensorsFile.read(file);
    writeFile(dir, MODEL_FILE_NAME, header, floatsToLittleEndianBytes(1f));

    final IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> parsed.readFloat32("w"));
    assertTrue(e.getMessage().contains("truncated"));
  }

  @Test
  void testReadFloat32RejectsElementCountByteRangeMismatch(@TempDir Path dir) throws IOException {
    // Shape [2] declares two F32 elements (8 bytes) but the data range holds only one.
    final byte[] data = floatsToLittleEndianBytes(1f);
    final String header = singleTensorHeader("w", "F32", "[2]", 0, data.length);
    final Path file = writeFile(dir, MODEL_FILE_NAME, header, data);

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

  // F16 is Model2Vec's default output dtype, so widening is the common downloaded-model case;
  // BF16 takes the same path with a different bit layout.
  @ParameterizedTest
  @ValueSource(strings = {"F16", "BF16"})
  void testReads16BitTensorWidenedToFloat(String dtype, @TempDir Path dir) throws IOException {
    final Path file = dir.resolve(MODEL_FILE_NAME);
    final float[] expected = {1.0f, -2.0f, 0.5f, 3.5f}; // exact in both 16-bit formats
    SafetensorsTestFiles.write(file, dtype, SafetensorsTestFiles.vector("w", expected));

    final SafetensorsFile parsed = SafetensorsFile.read(file);
    assertEquals(dtype, parsed.tensorInfo("w").dtype());
    assertArrayEquals(expected, parsed.readFloats("w"), 1e-3f);
  }

  @Test
  void testSingleMatrixTensorNameAcceptsF16(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve(MODEL_FILE_NAME);
    SafetensorsTestFiles.write(file, "F16",
        SafetensorsTestFiles.matrix("embeddings", new float[][] {{1f, 2f}, {3f, 4f}}));

    final SafetensorsFile parsed = SafetensorsFile.read(file);
    assertEquals("embeddings", parsed.singleMatrixTensorName());
  }

  @Test
  void testReadFloat32StrictlyRejectsF16(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve(MODEL_FILE_NAME);
    SafetensorsTestFiles.write(file, "F16", SafetensorsTestFiles.vector("w", new float[] {1f, 2f}));

    final SafetensorsFile parsed = SafetensorsFile.read(file);
    // readFloats accepts it; the strict readFloat32 must not.
    assertArrayEquals(new float[] {1f, 2f}, parsed.readFloats("w"), 1e-3f);
    assertThrows(IllegalArgumentException.class, () -> parsed.readFloat32("w"));
  }
}
