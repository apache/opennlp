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

package opennlp.tools.util.archive;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exercises {@link TarStream} against archives built byte by byte in the test, covering
 * regular traversal as well as boundary and corruption cases.
 */
public class TarStreamTest {

  private static final int BLOCK = 512;
  private static final int TERMINATOR_SIZE = 2 * BLOCK;
  private static final int NAME_LENGTH = 100;
  private static final int SIZE_OFFSET = 124;
  private static final int TYPE_OFFSET = 156;
  private static final String SIZE_FORMAT = "%011o";
  private static final String MODE = "0000644 ";
  private static final char TYPE_REGULAR_FILE = '0';
  private static final char TYPE_REGULAR_FILE_CLASSIC = '\0';
  private static final char TYPE_DIRECTORY = '5';

  /**
   * Builds one 512-byte tar header block with the given name, declared content size,
   * and type flag. The mode field is populated with a non-zero octal value so that a
   * name filling the whole 100-byte name field is followed by non-zero bytes, which
   * makes name-boundary tests meaningful.
   *
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param size The content size to declare in the octal size field; must not be
   *             negative.
   * @param typeFlag The tar type flag, for example {@code '0'} for a regular file,
   *                 {@code '5'} for a directory, or {@code '\0'} for an old-style
   *                 regular file.
   * @return The header block. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the name does not fit the name field or
   *         the size is negative.
   */
  private static byte[] header(String name, long size, char typeFlag) {
    final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    if (nameBytes.length > NAME_LENGTH) {
      throw new IllegalArgumentException(
          "entry name exceeds " + NAME_LENGTH + " bytes: " + name);
    }
    if (size < 0) {
      throw new IllegalArgumentException("size must not be negative: " + size);
    }
    final byte[] block = new byte[BLOCK];
    System.arraycopy(nameBytes, 0, block, 0, nameBytes.length);
    final byte[] mode = MODE.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(mode, 0, block, NAME_LENGTH, mode.length);
    final byte[] sizeOctal = String.format(SIZE_FORMAT, size)
        .getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(sizeOctal, 0, block, SIZE_OFFSET, sizeOctal.length);
    block[TYPE_OFFSET] = (byte) typeFlag;
    return block;
  }

  /**
   * Writes one complete tar entry into the given buffer: the header block declaring
   * the content's actual length, the content itself, and zero padding up to the next
   * 512-byte block boundary.
   *
   * @param tar The buffer receiving the entry bytes. Must not be {@code null}.
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param content The entry content. Must not be {@code null}.
   * @param typeFlag The tar type flag for the header.
   * @throws IOException Thrown if writing to the buffer fails.
   */
  private static void entry(ByteArrayOutputStream tar, String name, byte[] content,
      char typeFlag) throws IOException {
    tar.write(header(name, content.length, typeFlag));
    tar.write(content);
    tar.write(new byte[(BLOCK - content.length % BLOCK) % BLOCK]);
  }

  /**
   * Supplies blocks that must not be mistaken for a tar header, each with a description
   * naming the reason.
   *
   * @return The rejection cases. Never {@code null}.
   */
  private static Stream<Arguments> nonHeaderContent() {
    final byte[] filler = new byte[BLOCK];
    Arrays.fill(filler, (byte) 'x');
    return Stream.of(
        Arguments.of("fewer bytes than one block",
            "too short to be a tar header".getBytes(StandardCharsets.US_ASCII)),
        Arguments.of("no ustar magic and a non-octal size field", filler),
        Arguments.of("an all-zero block", new byte[BLOCK]));
  }

  /**
   * Proves that a stream without a single byte is treated as an archive with no
   * entries rather than as an error.
   */
  @Test
  void testEmptyStreamHasNoEntries() throws IOException {
    final TarStream stream = new TarStream(new ByteArrayInputStream(new byte[0]));
    Assertions.assertFalse(stream.next());
  }

  /**
   * Proves that an archive consisting only of the two all-zero terminator blocks
   * reports no entries.
   */
  @Test
  void testTerminatorOnlyArchiveHasNoEntries() throws IOException {
    final TarStream stream =
        new TarStream(new ByteArrayInputStream(new byte[TERMINATOR_SIZE]));
    Assertions.assertFalse(stream.next());
  }

  /**
   * Walks an archive holding a directory, a file whose content is deliberately left
   * unread, a file with an old-style NUL type flag, and a file whose size is an exact
   * block multiple, asserting name, size, type classification, and content for each
   * entry, and the end of the archive after the last one.
   */
  @Test
  void testReadsEntriesSizesTypesAndContent() throws IOException {
    final byte[] blockSized = new byte[BLOCK];
    for (int i = 0; i < blockSized.length; i++) {
      blockSized[i] = (byte) (i % 251);
    }
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "data/", new byte[0], TYPE_DIRECTORY);
    entry(tar, "data/skip.bin", "0123456789".getBytes(StandardCharsets.US_ASCII),
        TYPE_REGULAR_FILE);
    entry(tar, "data/alpha.txt", "alpha\n".getBytes(StandardCharsets.UTF_8),
        TYPE_REGULAR_FILE_CLASSIC);
    entry(tar, "block.bin", blockSized, TYPE_REGULAR_FILE);
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("data/", stream.name());
    Assertions.assertEquals(0, stream.size());
    Assertions.assertFalse(stream.isFile());

    // The next call must skip the unread content and padding of this entry.
    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("data/skip.bin", stream.name());
    Assertions.assertEquals(10, stream.size());
    Assertions.assertTrue(stream.isFile());

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("data/alpha.txt", stream.name());
    Assertions.assertEquals(6, stream.size());
    Assertions.assertTrue(stream.isFile());
    Assertions.assertArrayEquals("alpha\n".getBytes(StandardCharsets.UTF_8),
        stream.entryStream().readAllBytes());

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("block.bin", stream.name());
    Assertions.assertEquals(BLOCK, stream.size());
    Assertions.assertTrue(stream.isFile());
    Assertions.assertArrayEquals(blockSized, stream.entryStream().readAllBytes());

    Assertions.assertFalse(stream.next());
  }

  /**
   * Proves that a name occupying all 100 bytes of the name field is read in full and
   * does not bleed into the adjacent, non-zero mode field.
   */
  @Test
  void testNameFillsFullHundredByteField() throws IOException {
    final StringBuilder longName = new StringBuilder("d/");
    while (longName.length() < NAME_LENGTH) {
      longName.append('x');
    }
    final String name = longName.toString();
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, name, "n".getBytes(StandardCharsets.US_ASCII), TYPE_REGULAR_FILE);
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(NAME_LENGTH, stream.name().length());
    Assertions.assertEquals(name, stream.name());
    Assertions.assertArrayEquals("n".getBytes(StandardCharsets.US_ASCII),
        stream.entryStream().readAllBytes());
    Assertions.assertFalse(stream.next());
  }

  /**
   * Proves that a stream ending in the middle of a header block fails loud instead of
   * silently reporting the end of the archive.
   */
  @Test
  void testTruncatedHeaderFailsLoud() {
    final byte[] partial =
        Arrays.copyOf(header("cut.bin", 0, TYPE_REGULAR_FILE), BLOCK / 2);
    final TarStream stream = new TarStream(new ByteArrayInputStream(partial));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("truncated tar header", thrown.getMessage());
  }

  /**
   * Proves that an entry stream fails loud when the underlying stream ends before the
   * declared entry size has been delivered.
   */
  @Test
  void testTruncatedEntryContentFailsLoud() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tar.write(header("data.bin", 10, TYPE_REGULAR_FILE));
    tar.write("1234".getBytes(StandardCharsets.US_ASCII));
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> stream.entryStream().readAllBytes());
    Assertions.assertEquals("truncated tar entry: data.bin", thrown.getMessage());
  }

  /**
   * Proves that advancing past an entry whose content is missing from the stream fails
   * loud during the skip instead of reporting a clean end of the archive.
   */
  @Test
  void testTruncatedArchiveWhenSkippingFailsLoud() throws IOException {
    final TarStream stream = new TarStream(
        new ByteArrayInputStream(header("gone.bin", 600, TYPE_REGULAR_FILE)));

    Assertions.assertTrue(stream.next());
    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("truncated tar archive", thrown.getMessage());
  }

  /**
   * Proves that a header whose size field carries a non-octal digit is rejected with a
   * descriptive exception instead of producing a bogus size.
   */
  @Test
  void testMalformedSizeFieldFailsLoud() {
    final byte[] block = header("bad.bin", 0, TYPE_REGULAR_FILE);
    block[SIZE_OFFSET] = '9';
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("malformed tar size field in entry header",
        thrown.getMessage());
  }

  /**
   * Proves that a stream positioned at a tar header is detected as such and that the
   * detection leaves the position untouched, so the archive can still be read from its
   * first entry.
   */
  @Test
  void testStartsWithHeaderDetectsTarAndKeepsPosition() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "data/alpha.txt", "alpha\n".getBytes(StandardCharsets.UTF_8),
        TYPE_REGULAR_FILE);
    tar.write(new byte[TERMINATOR_SIZE]);
    final InputStream in =
        new BufferedInputStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(TarStream.startsWithHeader(in));

    final TarStream stream = new TarStream(in);
    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("data/alpha.txt", stream.name());
    Assertions.assertArrayEquals("alpha\n".getBytes(StandardCharsets.UTF_8),
        stream.entryStream().readAllBytes());
  }

  /**
   * Proves that content which is not a tar header is reported as such, whatever the
   * reason.
   */
  @ParameterizedTest
  @MethodSource("nonHeaderContent")
  void testStartsWithHeaderRejectsNonTarContent(String description, byte[] content)
      throws IOException {
    Assertions.assertFalse(
        TarStream.startsWithHeader(new ByteArrayInputStream(content)), description);
  }

  /**
   * Proves that detection rejects a missing stream and one that cannot be repositioned,
   * rather than consuming bytes the caller still needs.
   */
  @Test
  void testStartsWithHeaderRejectsUnusableStreams() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> TarStream.startsWithHeader(null));
    final InputStream notMarkable = new InputStream() {
      @Override
      public int read() {
        return -1;
      }
    };
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> TarStream.startsWithHeader(notMarkable));
  }

  /**
   * Proves that the constructor rejects a missing input stream.
   */
  @Test
  void testNullStreamIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new TarStream(null));
  }
}
