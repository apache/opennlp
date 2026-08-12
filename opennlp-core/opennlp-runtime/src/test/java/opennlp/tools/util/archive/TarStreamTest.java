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
      + "the archived bytes encode holes rather than the file content";

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
    // Reseal, so the reader reaches the size field instead of stopping at the checksum.
    TarArchives.reseal(block);
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

  /**
   * Proves that the constructor rejects a missing input stream.
   */
  @Test
  void testNullStreamIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new TarStream(null));
  }

  /**
   * Proves that a header whose stored checksum does not match its bytes is rejected.
   * Without the checksum, arbitrary content carrying the ustar magic would be read as an
   * archive and its size field trusted.
   */
  @Test
  void testHeaderWithWrongChecksumIsRejected() {
    final byte[] block = header("tampered.bin", 10, TYPE_REGULAR_FILE);
    block[0] = 'X';
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("malformed tar header checksum", thrown.getMessage());
  }

  /**
   * Proves that detection is not fooled by content that merely carries the ustar magic
   * at the right offset without a matching checksum.
   */
  @Test
  void testStartsWithHeaderRejectsUstarMagicWithoutAChecksum() throws IOException {
    final byte[] block = header("looks-real.bin", 0, TYPE_REGULAR_FILE);
    Arrays.fill(block, CHECKSUM_OFFSET, CHECKSUM_OFFSET + 8, (byte) '0');

    Assertions.assertFalse(TarStream.startsWithHeader(new ByteArrayInputStream(block)));
  }

  /**
   * Proves that a ustar name prefix is joined to the name field, which is how a path
   * longer than the 100-byte name field is stored. Without it the entry would surface
   * under the truncated tail of its own path.
   */
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

  /**
   * Proves that an empty ustar prefix leaves the name field alone rather than producing
   * a leading slash.
   */
  @Test
  void testEmptyUstarPrefixLeavesTheNameAlone() throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "plain.txt", "x".getBytes(StandardCharsets.UTF_8));
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals("plain.txt", stream.name());
  }
  /**
   * Proves that a pax global header carrying only a {@code comment} record is consumed
   * and the entry after it is delivered normally. A tar written by {@code git archive}
   * starts with exactly this header.
   */
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
            "pax global header carries path, which would change every entry after it"),
        Arguments.of("size resizes every following entry",
            TarArchives.paxRecord("size", "999999"),
            "pax global header carries size, which would change every entry after it"),
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
  /**
   * Proves that a pax global header is checked rather than skipped. A global header
   * applies to every entry that follows it, so one carrying {@code path} or {@code size}
   * would silently change what those entries are.
   */
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

  /**
   * Proves that a classic v7 archive, which carries no ustar magic at all, is read.
   * Keying detection off the magic rather than the header checksum would miss it.
   */
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

  /**
   * Proves that a header with a valid checksum but no name is refused rather than
   * yielding a nameless entry, and that detection refuses the same block, so the two
   * agree.
   */
  @Test
  void testHeaderWithAnEmptyNameIsRejected() throws IOException {
    final byte[] block = TarArchives.reseal(header("", 0, TYPE_REGULAR_FILE));
    final TarStream stream = new TarStream(new ByteArrayInputStream(block));

    Assertions.assertAll(
        () -> {
          final IOException thrown =
              Assertions.assertThrows(IOException.class, stream::next);
          Assertions.assertEquals("tar entry header carries an empty name",
              thrown.getMessage());
        },
        () -> Assertions.assertFalse(
            TarStream.startsWithHeader(new ByteArrayInputStream(block))));
  }

  /**
   * Pins the {@link InputStream#read(byte[], int, int)} range contract: the arguments are
   * checked before anything else, so an invalid range is reported even when the entry is
   * exhausted or the length is zero, rather than being masked by an early return.
   */
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
  /**
   * Pins the {@link java.io.InputStream#read(byte[], int, int)} contract for a
   * zero-length read: it must report zero bytes read, both mid-entry and at the end of
   * the entry, and must never be mistaken for end of stream.
   */
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
   * Encodes the metadata records a real writer emits alongside the ones that matter, so
   * tests exercise the shape an archive actually has rather than a minimal one.
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

  /**
   * Proves that a pax extended header supplies the name of the entry after it. The entry
   * header carries only a truncated name, so ignoring the extended header delivers the
   * wrong path, and refusing it rejects every archive {@code bsdtar} or
   * {@code tar --format=posix} writes.
   */
  @Test
  void testPaxExtendedHeaderSuppliesTheEntryName() throws IOException {
    final String path = "./corpus-1.0/" + "d".repeat(120) + "/annotations/train.conllu";
    final byte[] content = "content\n".getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    entry(tar, "./PaxHeaders/train.conllu",
        concat(TarArchives.paxRecord("path", path), metadataRecords()), 'x');
    // The entry header holds the truncated name, exactly as GNU tar writes it.
    entry(tar, path.substring(0, 100), content);
    tar.write(new byte[TERMINATOR_SIZE]);
    final TarStream stream = new TarStream(new ByteArrayInputStream(tar.toByteArray()));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(path, stream.name());
    Assertions.assertTrue(stream.isFile());
    Assertions.assertArrayEquals(content, stream.entryStream().readAllBytes());
    Assertions.assertFalse(stream.next());
  }

  /**
   * Proves that a pax header carrying nothing but metadata leaves the entry alone. A pax
   * archive has one of these ahead of every entry, including entries needing no override,
   * so this is the common case rather than an edge one.
   */
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

  /**
   * Proves that a pax extended header applies to the entry immediately after it and to no
   * other, so a name override cannot leak onto later entries.
   */
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

  /**
   * Proves that a pax {@code size} record overrides the header's octal size field, which
   * is how an entry too large for eleven octal digits states its length.
   */
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

  /**
   * Proves that a GNU long-name header supplies the name of the entry after it. Its
   * payload is the name terminated by a NUL, which must not survive into the name.
   */
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

  /**
   * Proves that a GNU long-link header is consumed without disturbing the entry after it.
   * It names a link target, which this reader does not expose.
   */
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

  /**
   * Proves that a GNU header is not read as a ustar one. GNU puts {@code atime} at the
   * offset ustar gives to the name prefix, so consulting it there would deliver every
   * entry of a GNU incremental archive under a directory named after an octal timestamp.
   */
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
   * Supplies the two ways an archive declares a sparse entry, whose archived bytes encode
   * holes rather than the content, so unpacking them literally would write a wrong file.
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

  /**
   * Proves that a size field in the base-256 encoding is read, content and all. GNU
   * writes this form when a length does not fit the eleven octal digits the field holds.
   */
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

  /**
   * Proves that a length beyond what eleven octal digits can hold is read correctly,
   * which is the only reason the encoding exists. Eleven octal digits stop one byte short
   * of 8 GiB, so the octal path cannot express this size at all.
   */
  @Test
  void testBase256SizeFieldCarriesLengthsBeyondTheOctalRange() throws IOException {
    final long beyondOctal = 8L * 1024 * 1024 * 1024;
    Assertions.assertTrue(beyondOctal > LARGEST_OCTAL_SIZE);
    final TarStream stream = new TarStream(new ByteArrayInputStream(
        TarArchives.base256Header("huge.bin", beyondOctal, TYPE_REGULAR_FILE)));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(beyondOctal, stream.size());
  }

  /**
   * Proves that the largest length a {@code long} can hold round-trips, so the overflow
   * guard rejects only what genuinely does not fit. Advancing afterward must still
   * detect that the declared content is absent rather than overflowing the skip count
   * and reporting a clean end of archive.
   */
  @Test
  void testBase256SizeFieldAcceptsTheLargestRepresentableLength() throws IOException {
    final TarStream stream = new TarStream(new ByteArrayInputStream(
        TarArchives.base256Header("max.bin", Long.MAX_VALUE, TYPE_REGULAR_FILE)));

    Assertions.assertTrue(stream.next());
    Assertions.assertEquals(Long.MAX_VALUE, stream.size());
    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("truncated tar archive", thrown.getMessage());
  }

  /**
   * Proves that a base-256 length too large for a {@code long} is refused rather than
   * silently truncated to a small one, which would make the reader stop early inside the
   * entry and read the rest of it as headers.
   */
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

  /**
   * Proves that a negative base-256 length is refused. The sign lives in the bit below
   * the marker, so ignoring it would turn a negative value into an enormous positive one.
   */
  @Test
  void testNegativeBase256SizeFieldIsRejected() {
    final TarStream stream = new TarStream(new ByteArrayInputStream(
        TarArchives.base256Header("negative.bin", -1, TYPE_REGULAR_FILE)));

    final IOException thrown = Assertions.assertThrows(IOException.class, stream::next);
    Assertions.assertEquals("tar size field is negative", thrown.getMessage());
  }
}
