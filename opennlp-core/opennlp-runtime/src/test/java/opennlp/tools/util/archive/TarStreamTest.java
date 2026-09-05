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
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static opennlp.tools.util.archive.TarArchives.BLOCK;
import static opennlp.tools.util.archive.TarArchives.TERMINATOR_SIZE;
import static opennlp.tools.util.archive.TarArchives.TYPE_DIRECTORY;
import static opennlp.tools.util.archive.TarArchives.TYPE_REGULAR_FILE;
import static opennlp.tools.util.archive.TarArchives.TYPE_REGULAR_FILE_CLASSIC;
import static opennlp.tools.util.archive.TarArchives.entry;
import static opennlp.tools.util.archive.TarArchives.header;

/**
 * Exercises {@link TarStream} against archives built byte by byte in the test, covering
 * regular traversal as well as boundary and corruption cases.
 */
public class TarStreamTest {

  private static final int NAME_LENGTH = 100;
  private static final int SIZE_OFFSET = 124;
  private static final int CHECKSUM_OFFSET = 148;

  /** The largest length eleven octal digits can hold, one byte short of 8 GiB. */
  private static final long LARGEST_OCTAL_SIZE = (1L << 33) - 1;

  private static final String MALFORMED_RECORD = "malformed pax extended header record";
  private static final String SPARSE_REJECTED = "sparse tar entries are not supported: "
      + "the archived bytes describe file holes, not contiguous content";

  /** The metadata records GNU tar and bsdtar write ahead of an ordinary entry. */
  private static final String[][] METADATA = {
      {"mtime", "1786557589.505896519"},
      {"atime", "1786557589.496852337"},
      {"ctime", "1786557589.505896519"},
      {"uid", "1000"},
      {"gname", "krickert"},
      {"SCHILY.dev", "66306"},
      {"hdrcharset", "BINARY"}};

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

  @Test
  void testEmptyStreamHasNoEntries() throws IOException {
    final TarStream stream = new TarStream(new ByteArrayInputStream(new byte[0]));
    Assertions.assertFalse(stream.next());
  }

  @Test
  void testTerminatorOnlyArchiveHasNoEntries() throws IOException {
    final TarStream stream =
        new TarStream(new ByteArrayInputStream(new byte[TERMINATOR_SIZE]));
    Assertions.assertFalse(stream.next());
  }

  /**
   * Checks that repeated reads after the terminator remain at end of archive.
   *
   * @throws IOException Thrown if the fixture archive cannot be read.
   */
  @Test
  void testNextRemainsAtEndAfterAnUnalignedEntry() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "data.txt", "x".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertArrayEquals("x".getBytes(StandardCharsets.UTF_8),
        stream.entryStream().readAllBytes());
    Assertions.assertFalse(stream.next());
    Assertions.assertFalse(stream.next());
  }

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

  @Test
  void testTruncatedHeaderReportsError() {
    final byte[] partial =
        Arrays.copyOf(header("cut.bin", 0, TYPE_REGULAR_FILE), BLOCK / 2);
    final TarStream stream = new TarStream(new ByteArrayInputStream(partial));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("truncated tar header", thrown.getMessage());
  }

  @Test
  void testTruncatedEntryContentReportsError() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tar.write(header("data.bin", 10, TYPE_REGULAR_FILE));
    tar.write("1234".getBytes(StandardCharsets.US_ASCII));
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> stream.entryStream().readAllBytes());
    Assertions.assertEquals("truncated tar entry: data.bin", thrown.getMessage());
  }

  @Test
  void testTruncatedArchiveWhenSkippingReportsError() throws IOException {
    final TarStream stream = new TarStream(
        new ByteArrayInputStream(header("gone.bin", 600, TYPE_REGULAR_FILE)));

    Assertions.assertTrue(stream.next());
    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("truncated tar archive", thrown.getMessage());
  }

  @Test
  void testMalformedSizeFieldReportsError() {
    final byte[] block = header("bad.bin", 0, TYPE_REGULAR_FILE);
    block[SIZE_OFFSET] = '9';
    // Reseal, so the reader reaches the size field instead of stopping at the checksum.
    TarArchives.reseal(block);
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("malformed tar size field in entry header",
        thrown.getMessage());
  }

  @Test
  void testSizeFieldWithEmbeddedPaddingIsRejected() {
    final byte[] block = header("bad.bin", 0, TYPE_REGULAR_FILE);
    Arrays.fill(block, SIZE_OFFSET, SIZE_OFFSET + 12, (byte) 0);
    block[SIZE_OFFSET] = '1';
    block[SIZE_OFFSET + 1] = ' ';
    block[SIZE_OFFSET + 2] = '2';
    TarArchives.reseal(block);
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("malformed tar size field in entry header",
        thrown.getMessage());
  }

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

  @ParameterizedTest
  @MethodSource("nonHeaderContent")
  void testStartsWithHeaderRejectsNonTarContent(String description, byte[] content)
      throws IOException {
    Assertions.assertFalse(
        TarStream.startsWithHeader(new ByteArrayInputStream(content)), description);
  }

  @Test
  void testStartsWithHeaderRejectsUnusableStreams() {
    final InputStream notMarkable = new InputStream() {
      @Override
      public int read() {
        return -1;
      }
    };
    // assertAll so a missing check on one argument does not hide the other.
    Assertions.assertAll(
        () -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> TarStream.startsWithHeader(null)),
        () -> Assertions.assertThrows(IllegalArgumentException.class,
            () -> TarStream.startsWithHeader(notMarkable)));
  }

  @Test
  void testNullStreamIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new TarStream(null));
  }

  /**
   * @param maxEntries The invalid entry limit.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(longs = {0, -1})
  void testInvalidEntryLimitIsRejected(long maxEntries) {
    final IllegalArgumentException thrown = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new TarStream(InputStream.nullInputStream(), maxEntries));
    Assertions.assertEquals("maxEntries must be positive", thrown.getMessage());
  }

  @Test
  void testHeaderWithWrongChecksumIsRejected() {
    final byte[] block = header("tampered.bin", 10, TYPE_REGULAR_FILE);
    block[0] = 'X';
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("malformed tar header checksum", thrown.getMessage());
  }

  @Test
  void testChecksumWithEmbeddedPaddingIsRejected() {
    final byte[] block = header("bad-checksum.bin", 0, TYPE_REGULAR_FILE);
    System.arraycopy(block, CHECKSUM_OFFSET + 1, block, CHECKSUM_OFFSET + 2, 5);
    block[CHECKSUM_OFFSET + 1] = ' ';
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("malformed tar header checksum", thrown.getMessage());
  }

  @Test
  void testStartsWithHeaderRejectsUstarMagicWithoutAChecksum() throws IOException {
    final byte[] block = header("looks-real.bin", 0, TYPE_REGULAR_FILE);
    Arrays.fill(block, CHECKSUM_OFFSET, CHECKSUM_OFFSET + 8, (byte) '0');

    Assertions.assertFalse(TarStream.startsWithHeader(new ByteArrayInputStream(block)));
  }

  @Test
  void testUstarPrefixIsJoinedToTheName() throws IOException {
    final String prefix = "corpus-1.0/" + "d".repeat(120);
    final String tail = "annotations/train.conllu";
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    final byte[] content = "# sent_id = 1\n".getBytes(StandardCharsets.UTF_8);
    tar.write(TarArchives.header(tail, prefix, content.length, TYPE_REGULAR_FILE));
    tar.write(content);
    tar.write(new byte[BLOCK - content.length]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(prefix + "/" + tail, stream.name());
    Assertions.assertTrue(stream.name().length() > 100);
    Assertions.assertArrayEquals(content, stream.entryStream().readAllBytes());
  }

  @Test
  void testEmptyUstarPrefixLeavesTheNameAlone() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "plain.txt", "x".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("plain.txt", stream.name());
  }

  @Test
  void testPaxGlobalHeaderWithOnlyACommentIsConsumed() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "pax_global_header",
        TarArchives.paxRecord("comment", "0123456789abcdef"), 'g');
    entry(tar, "data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("data.txt", stream.name());
    Assertions.assertTrue(stream.isFile());
    Assertions.assertArrayEquals("content".getBytes(StandardCharsets.UTF_8),
        stream.entryStream().readAllBytes());
    Assertions.assertFalse(stream.next());
  }

  /**
   * Supplies pax global headers that must be rejected: the two keywords that would change
   * every entry after them, the sparse family whose content cannot be unpacked, and
   * payloads that are not pax records at all.
   *
   * @return One case per rejected global header. Never {@code null}.
   */
  private static Stream<Arguments> rejectedGlobalHeaders() {
    return Stream.of(
        Arguments.of("path redirects every following entry",
            TarArchives.paxRecord("path", "../escape.txt"),
            "pax global header contains path, which would change every entry after it"),
        Arguments.of("size resizes every following entry",
            TarArchives.paxRecord("size", "999999"),
            "pax global header contains size, which would change every entry after it"),
        Arguments.of("sparse map cannot be unpacked",
            TarArchives.paxRecord("GNU.sparse.name", "holes.bin"),
            SPARSE_REJECTED),
        Arguments.of("no length prefix",
            "comment=0\n".getBytes(StandardCharsets.UTF_8), MALFORMED_RECORD),
        Arguments.of("length longer than the payload",
            "99 comment=0\n".getBytes(StandardCharsets.UTF_8), MALFORMED_RECORD),
        Arguments.of("length shorter than its own prefix",
            "1 comment=0\n".getBytes(StandardCharsets.UTF_8), MALFORMED_RECORD),
        Arguments.of("no keyword before the equals sign",
            "12 =value\n".getBytes(StandardCharsets.UTF_8), MALFORMED_RECORD),
        Arguments.of("no equals sign at all",
            "12 comment0\n".getBytes(StandardCharsets.UTF_8), MALFORMED_RECORD));
  }
  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedGlobalHeaders")
  void testRejectedPaxGlobalHeader(String description, byte[] payload, String message)
      throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "pax_global_header", payload, 'g');
    entry(tar, "data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals(message, thrown.getMessage());
  }

  @Test
  void testClassicHeaderWithoutUstarMagicIsRead() throws IOException {
    final byte[] content = "classic\n".getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tar.write(TarArchives.classicHeader("v7/data.txt", content.length,
        TYPE_REGULAR_FILE));
    tar.write(content);
    tar.write(new byte[BLOCK - content.length]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final byte[] archive = tar.toByteArray();

    Assertions.assertTrue(TarStream.startsWithHeader(
        new BufferedInputStream(new ByteArrayInputStream(archive))));
    final TarStream stream = new TarStream(new ByteArrayInputStream(archive));
    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("v7/data.txt", stream.name());
    Assertions.assertTrue(stream.isFile());
    Assertions.assertArrayEquals(content, stream.entryStream().readAllBytes());
    Assertions.assertFalse(stream.next());
  }

  @Test
  void testHeaderWithAnEmptyNameIsRejected() throws IOException {
    final byte[] block = TarArchives.reseal(header("", 0, TYPE_REGULAR_FILE));
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    Assertions.assertAll(
        () -> {
          final IOException thrown =
              Assertions.assertThrows(IOException.class, stream::next);
          Assertions.assertEquals("tar entry header contains an empty name",
              thrown.getMessage());
        },
        () -> Assertions.assertFalse(
            TarStream.startsWithHeader(new ByteArrayInputStream(block))));
  }

  @Test
  void testHeaderNameWithMalformedUtf8IsRejected() {
    final byte[] block = header("data.txt", 0, TYPE_REGULAR_FILE);
    block[0] = (byte) 0xC3;
    block[1] = 0;
    TarArchives.reseal(block);
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("tar entry name is not valid UTF-8", thrown.getMessage());
  }

  @Test
  void testEntryStreamRejectsInvalidReadRanges() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));
    Assertions.assertTrue(stream.next());
    final InputStream content = stream.entryStream();
    final byte[] buffer = new byte[8];

    Assertions.assertAll(
        () -> Assertions.assertThrows(NullPointerException.class,
            () -> content.read(null, 0, 1)),
        () -> Assertions.assertThrows(NullPointerException.class,
            () -> content.read(null, 0, 0)),
        () -> Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> content.read(buffer, -1, 1)),
        () -> Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> content.read(buffer, 0, -1)),
        () -> Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> content.read(buffer, 0, buffer.length + 1)),
        () -> Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> content.read(buffer, buffer.length, 1)),
        // Zero length at a valid offset is legal and must not be mistaken for an error.
        () -> Assertions.assertEquals(0, content.read(buffer, buffer.length, 0)));
  }

  @Test
  void testZeroLengthReadReturnsZero() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));
    Assertions.assertTrue(stream.next());
    final InputStream content = stream.entryStream();
    final byte[] buffer = new byte[8];

    Assertions.assertEquals(0, content.read(buffer, 0, 0));
    Assertions.assertArrayEquals("content".getBytes(StandardCharsets.UTF_8),
        content.readAllBytes());
    // Exhausted: a zero-length read still reports zero, not end of stream.
    Assertions.assertEquals(0, content.read(buffer, 0, 0));
    Assertions.assertEquals(-1, content.read(buffer, 0, buffer.length));
    // Exhausted, and the range is still checked before the end-of-stream answer.
    Assertions.assertThrows(IndexOutOfBoundsException.class,
        () -> content.read(buffer, 0, buffer.length + 1));
  }

  /**
   * Concatenates byte arrays, so a test can assemble one extension header payload from
   * several records.
   *
   * @param parts The pieces to join. Must not be {@code null}.
   * @return The concatenation. Never {@code null}.
   */
  private static byte[] concat(byte[]... parts) {
    final ByteArrayOutputStream joined = new ByteArrayOutputStream();
    for (final byte[] part : parts) {
      joined.writeBytes(part);
    }
    return joined.toByteArray();
  }

  /**
   * Encodes metadata records emitted by real archive writers, so tests cover their output.
   *
   * @return The encoded records. Never {@code null}.
   */
  private static byte[] metadataRecords() {
    final ByteArrayOutputStream records = new ByteArrayOutputStream();
    for (final String[] record : METADATA) {
      records.writeBytes(TarArchives.paxRecord(record[0], record[1]));
    }
    return records.toByteArray();
  }

  @Test
  void testPaxExtendedHeaderSuppliesTheEntryName() throws IOException {
    final String path = "./corpus-1.0/" + "d".repeat(120) + "/annotations/train.conllu";
    final byte[] content = "content\n".getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "./PaxHeaders/train.conllu",
        concat(TarArchives.paxRecord("path", path), metadataRecords()), 'x');
    // GNU tar stores the truncated name in the entry header.
    entry(tar, path.substring(0, 100), content);
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(path, stream.name());
    Assertions.assertTrue(stream.isFile());
    Assertions.assertArrayEquals(content, stream.entryStream().readAllBytes());
    Assertions.assertFalse(stream.next());
  }

  @Test
  void testPaxPathWithMalformedUtf8IsRejected() throws IOException {
    final byte[] malformedPath = {
        '1', '0', ' ', 'p', 'a', 't', 'h', '=', (byte) 0xC3, '\n'};
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "./PaxHeaders/data.txt", malformedPath, 'x');
    entry(tar, "data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("pax record is not valid UTF-8", thrown.getMessage());
  }

  @Test
  void testPaxMetadataOnlyHeaderLeavesTheEntryAlone() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "./PaxHeaders/short.txt", metadataRecords(), 'x');
    entry(tar, "./short.txt", "short\n".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("./short.txt", stream.name());
    Assertions.assertEquals(6, stream.size());
    Assertions.assertFalse(stream.next());
  }

  @Test
  void testPaxExtendedHeaderAppliesOnlyToTheNextEntry() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "./PaxHeaders/first",
        TarArchives.paxRecord("path", "overridden/first.txt"), 'x');
    entry(tar, "truncated-first", "one".getBytes(StandardCharsets.UTF_8));
    entry(tar, "second.txt", "two".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("overridden/first.txt", stream.name());
    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("second.txt", stream.name());
    Assertions.assertFalse(stream.next());
  }

  @Test
  void testPaxExtendedHeaderSuppliesTheEntrySize() throws IOException {
    final byte[] content = "0123456789".getBytes(StandardCharsets.US_ASCII);
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "./PaxHeaders/data.bin", TarArchives.paxRecord("size", "10"), 'x');
    tar.write(header("data.bin", 0, TYPE_REGULAR_FILE));
    tar.write(content);
    tar.write(new byte[BLOCK - content.length]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(10, stream.size());
    Assertions.assertArrayEquals(content, stream.entryStream().readAllBytes());
  }

  @Test
  void testGnuLongNameHeaderSuppliesTheEntryName() throws IOException {
    final String path = "./corpus-1.0/" + "d".repeat(120) + "/annotations/train.conllu";
    final byte[] content = "content\n".getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "././@LongLink",
        (path + "\0").getBytes(StandardCharsets.UTF_8), 'L');
    entry(tar, path.substring(0, 100), content);
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(path, stream.name());
    Assertions.assertArrayEquals(content, stream.entryStream().readAllBytes());
    Assertions.assertFalse(stream.next());
  }

  @Test
  void testGnuLongNameWithMalformedUtf8IsRejected() throws IOException {
    final byte[] malformedName = {'b', 'a', 'd', (byte) 0xC3, 0};
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "././@LongLink", malformedName, 'L');
    entry(tar, "data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("GNU long name is not valid UTF-8", thrown.getMessage());
  }

  @Test
  void testGnuLongLinkHeaderIsConsumed() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "././@LongLink",
        ("../" + "t".repeat(120) + "/target\0").getBytes(StandardCharsets.UTF_8), 'K');
    entry(tar, "data.txt", "content".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("data.txt", stream.name());
    Assertions.assertArrayEquals("content".getBytes(StandardCharsets.UTF_8),
        stream.entryStream().readAllBytes());
  }

  @Test
  void testGnuHeaderDoesNotReadItsAtimeAsANamePrefix() throws IOException {
    final byte[] content = "short\n".getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tar.write(TarArchives.gnuHeader("./short.txt", content.length, TYPE_REGULAR_FILE,
        "15237132225"));
    tar.write(content);
    tar.write(new byte[BLOCK - content.length]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("./short.txt", stream.name());
    Assertions.assertArrayEquals(content, stream.entryStream().readAllBytes());
  }

  /**
   * Supplies the two sparse entry declarations. Their archived bytes describe file holes,
   * so copying those bytes would produce incorrect content.
   *
   * @return One case per sparse declaration. Never {@code null}.
   */
  private static Stream<Arguments> sparseEntries() {
    return Stream.of(
        Arguments.of("GNU sparse type flag", (Supplier<byte[]>) () -> {
          final ByteArrayOutputStream tar = new ByteArrayOutputStream();
          tar.writeBytes(header("holes.bin", 0, 'S'));
          return tar.toByteArray();
        }),
        Arguments.of("pax sparse records", (Supplier<byte[]>) () -> {
          final ByteArrayOutputStream tar = new ByteArrayOutputStream();
          try {
            entry(tar, "./PaxHeaders/holes.bin",
                concat(TarArchives.paxRecord("GNU.sparse.major", "1"),
                    TarArchives.paxRecord("GNU.sparse.name", "holes.bin")), 'x');
            entry(tar, "holes.bin", "not the content".getBytes(StandardCharsets.UTF_8));
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
          return tar.toByteArray();
        }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sparseEntries")
  void testSparseEntriesAreRejected(String description, Supplier<byte[]> archive) {
    final TarStream stream = new TarStream(new ByteArrayInputStream(archive.get()));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals(SPARSE_REJECTED, thrown.getMessage());
  }

  @Test
  void testBase256SizeFieldIsRead() throws IOException {
    final byte[] content = "0123456789".getBytes(StandardCharsets.US_ASCII);
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    tar.write(TarArchives.base256Header("big.bin", content.length, TYPE_REGULAR_FILE));
    tar.write(content);
    tar.write(new byte[BLOCK - content.length]);
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("big.bin", stream.name());
    Assertions.assertEquals(content.length, stream.size());
    Assertions.assertArrayEquals(content, stream.entryStream().readAllBytes());
  }

  @Test
  void testBase256SizeFieldCarriesLengthsBeyondTheOctalRange() throws IOException {
    final long beyondOctal = 8L * 1024 * 1024 * 1024;
    Assertions.assertTrue(beyondOctal > LARGEST_OCTAL_SIZE);
    final TarStream stream = new TarStream(new ByteArrayInputStream(
        TarArchives.base256Header("huge.bin", beyondOctal, TYPE_REGULAR_FILE)));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(beyondOctal, stream.size());
  }

  @Test
  void testBase256SizeFieldAcceptsTheLargestRepresentableLength() throws IOException {
    final TarStream stream = new TarStream(new ByteArrayInputStream(
        TarArchives.base256Header("max.bin", Long.MAX_VALUE, TYPE_REGULAR_FILE)));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(Long.MAX_VALUE, stream.size());
    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("truncated tar archive", thrown.getMessage());
  }

  @Test
  void testBase256SizeFieldBeyondTheLongRangeIsRejected() {
    final byte[] block = TarArchives.base256Header("huge.bin", 0, TYPE_REGULAR_FILE);
    // One bit above the 63 a long can hold, left of everything the encoder can write.
    block[SIZE_OFFSET + 1] = 1;
    TarArchives.reseal(block);
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals(
        "tar size field exceeds the largest length this reader can represent",
        thrown.getMessage());
  }

  @Test
  void testNegativeBase256SizeFieldIsRejected() {
    final TarStream stream = new TarStream(new ByteArrayInputStream(
        TarArchives.base256Header("negative.bin", -1, TYPE_REGULAR_FILE)));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("tar size field is negative", thrown.getMessage());
  }
}
