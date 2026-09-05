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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import opennlp.tools.commons.Internal;

/**
 * A forward-only reader for classic v7, POSIX ustar, GNU, and pax tar streams.
 * {@link #next()} advances to the following entry and {@link #entryStream()} exposes
 * only the current entry's bytes.
 *
 * <p>The reader validates header checksums, supports ustar name prefixes, GNU long
 * names, pax {@code path} and {@code size} records, and GNU base-256 sizes. Sparse
 * entries and global pax records that change paths or sizes are rejected because this
 * reader cannot reproduce their content or global semantics.</p>
 *
 * @since 3.0.0
 */
@Internal
public final class TarStream {

  private static final int BLOCK = 512;
  private static final int NAME_LENGTH = 100;
  private static final int CHECKSUM_OFFSET = 148;
  private static final int CHECKSUM_LENGTH = 8;
  private static final int SIZE_OFFSET = 124;
  private static final int SIZE_LENGTH = 12;
  private static final int TYPE_OFFSET = 156;
  private static final int MAGIC_OFFSET = 257;
  private static final int PREFIX_OFFSET = 345;
  private static final int PREFIX_LENGTH = 155;
  private static final String USTAR_MAGIC = "ustar";
  private static final char TYPE_REGULAR_FILE = '0';
  private static final char TYPE_REGULAR_FILE_CLASSIC = '\0';
  private static final char TYPE_GNU_LONG_NAME = 'L';
  private static final char TYPE_GNU_LONG_LINK = 'K';
  private static final char TYPE_GNU_SPARSE = 'S';
  private static final char TYPE_PAX_EXTENDED = 'x';
  private static final char TYPE_PAX_GLOBAL = 'g';
  private static final int MAX_EXTENSION_BYTES = 1 << 20;
  private static final String MALFORMED_RECORD = "malformed pax extended header record";
  private static final String KEYWORD_PATH = "path";
  private static final String KEYWORD_SIZE = "size";
  private static final String SPARSE_PREFIX = "GNU.sparse.";
  private static final int BASE_256_MARKER = 0x80;
  private static final int BASE_256_NEGATIVE = 0x40;
  private static final int BASE_256_FIRST_BYTE_BITS = 0x7F;

  private final InputStream in;
  private final long maxEntries;
  private final byte[] header = new byte[BLOCK];

  private String name;
  private long size;
  private char type;
  private long remaining;
  private long entries;
  private boolean ended;

  /** The name an extension header supplied for the entry that follows it, else null. */
  private String pendingPath;

  /** The size a pax header supplied for the entry that follows it, else {@code -1}. */
  private long pendingSize = -1;

  /**
   * Initializes the reader.
   *
   * @param in The tar content. Not {@code null}. Not closed by this class.
   * @throws IllegalArgumentException Thrown if {@code in} is {@code null}.
   */
  public TarStream(InputStream in) {
    this(in, Long.MAX_VALUE);
  }

  /**
   * Initializes a reader with an archive-entry limit. Extension headers count toward the
   * limit.
   *
   * @param in The tar content. Not {@code null}. Not closed by this class.
   * @param maxEntries The maximum number of archive headers to read. Must be positive.
   * @throws IllegalArgumentException Thrown if {@code in} is {@code null} or
   *         {@code maxEntries} is not positive.
   */
  public TarStream(InputStream in, long maxEntries) {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries must be positive");
    }
    this.in = in;
    this.maxEntries = maxEntries;
  }

  /**
   * Checks whether the given stream is positioned at a tar entry header, leaving its
   * position unchanged.
   *
   * @param in The stream to inspect. Not {@code null} and must support
   *           {@link InputStream#mark(int) mark} and {@link InputStream#reset() reset}.
   * @return {@code true} if the next 512 bytes read as a tar header, {@code false} if
   *         they do not or if fewer than 512 bytes are available.
   * @throws IOException Thrown if reading from or repositioning the stream fails.
   * @throws IllegalArgumentException Thrown if {@code in} is {@code null} or does not
   *         support mark and reset.
   */
  public static boolean startsWithHeader(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    if (!in.markSupported()) {
      throw new IllegalArgumentException("in must support mark and reset");
    }
    in.mark(BLOCK);
    try {
      final byte[] block = new byte[BLOCK];
      return in.readNBytes(block, 0, BLOCK) == BLOCK && isHeader(block);
    } finally {
      in.reset();
    }
  }

  /**
   * Advances to the next entry.
   *
   * @return {@code true} if an entry is available, {@code false} at the end of the
   *         archive.
   * @throws IOException Thrown if the archive is truncated or a header is malformed.
   */
  public boolean next() throws IOException {
    if (ended) {
      return false;
    }
    skip(remaining);
    skip(padding(size));
    while (true) {
      if (!readBlock() || isEndBlock()) {
        ended = true;
        return false;
      }
      entries++;
      if (entries > maxEntries) {
        throw new IOException("archive entry count exceeds the limit of "
            + maxEntries + " entries");
      }
      if (!hasValidChecksum(header)) {
        throw new IOException("malformed tar header checksum");
      }
      type = (char) header[TYPE_OFFSET];
      size = parseSize();
      remaining = size;
      if (type == TYPE_GNU_SPARSE) {
        throw new IOException("sparse tar entries are not supported: "
            + "the archived bytes describe file holes, not contiguous content");
      }
      if (type == TYPE_PAX_EXTENDED || type == TYPE_PAX_GLOBAL) {
        readRecords(readExtensionPayload(), type == TYPE_PAX_GLOBAL);
        continue;
      }
      if (type == TYPE_GNU_LONG_NAME) {
        final byte[] payload = readExtensionPayload();
        pendingPath = trimNul(decodeUtf8(payload, 0, payload.length, "GNU long name"));
        continue;
      }
      if (type == TYPE_GNU_LONG_LINK) {
        // The link target of an entry this reader does not expose.
        readExtensionPayload();
        continue;
      }
      name = pendingPath != null ? pendingPath : readName();
      pendingPath = null;
      if (pendingSize >= 0) {
        size = pendingSize;
        remaining = size;
        pendingSize = -1;
      }
      if (name.isEmpty()) {
        throw new IOException("tar entry header contains an empty name");
      }
      return true;
    }
  }

  /**
   * Reads the payload of the extension header just read, leaving the stream positioned on
   * the header that follows it.
   *
   * @return The payload bytes. Not {@code null}.
   * @throws IOException Thrown if the payload is larger than
   *         {@link #MAX_EXTENSION_BYTES} or the archive ends inside it.
   */
  private byte[] readExtensionPayload() throws IOException {
    if (size > MAX_EXTENSION_BYTES) {
      throw new IOException("tar extension header of " + size + " bytes exceeds the "
          + MAX_EXTENSION_BYTES + " byte limit");
    }
    final byte[] payload = new byte[(int) size];
    if (in.readNBytes(payload, 0, payload.length) < payload.length) {
      throw new IOException("truncated tar archive");
    }
    skip(padding(size));
    size = 0;
    remaining = 0;
    return payload;
  }

  /**
   * Reads a pax extended header payload, which is a sequence of
   * {@code "<length> <keyword>=<value>\n"} records. Each length counts the complete
   * record, including the length digits, blank, and newline. Records are parsed from raw
   * bytes because the length is a byte count and a multibyte value would shift later records.
   *
   * @param payload The raw extended header payload.
   * @param global Whether this is a global header, which applies to all following entries.
   * @throws IOException Thrown if a record is malformed, if a global header contains a
   *         keyword that would change the entries after it, or if the entry is sparse.
   */
  private void readRecords(byte[] payload, boolean global) throws IOException {
    int offset = 0;
    while (offset < payload.length) {
      int blank = offset;
      while (blank < payload.length && payload[blank] != ' ') {
        blank++;
      }
      if (blank == payload.length || blank == offset) {
        throw new IOException(MALFORMED_RECORD);
      }
      int length = 0;
      for (int i = offset; i < blank; i++) {
        final byte b = payload[i];
        if (b < '0' || b > '9') {
          throw new IOException(MALFORMED_RECORD);
        }
        length = length * 10 + (b - '0');
        if (length > payload.length) {
          throw new IOException(MALFORMED_RECORD);
        }
      }
      final int end = offset + length;
      if (length <= blank - offset || end > payload.length || payload[end - 1] != '\n') {
        throw new IOException(MALFORMED_RECORD);
      }
      int equals = blank + 1;
      while (equals < end - 1 && payload[equals] != '=') {
        equals++;
      }
      if (equals >= end - 1 || equals == blank + 1) {
        throw new IOException(MALFORMED_RECORD);
      }
      apply(decodeUtf8(payload, blank + 1, equals - blank - 1, "pax record"),
          decodeUtf8(payload, equals + 1, end - equals - 2, "pax record"), global);
      offset = end;
    }
  }

  /**
   * Decodes archive text without replacing malformed input, which would silently
   * change an archive path.
   *
   * @param bytes The bytes containing the text.
   * @param offset The first byte to decode.
   * @param length The number of bytes to decode.
   * @param subject The field name to use in an error message.
   * @return The decoded text. Not {@code null}.
   * @throws IOException Thrown if the bytes are not valid UTF-8.
   */
  private static String decodeUtf8(byte[] bytes, int offset, int length, String subject)
      throws IOException {
    try {
      return StandardCharsets.UTF_8.newDecoder()
          .decode(ByteBuffer.wrap(bytes, offset, length)).toString();
    } catch (CharacterCodingException e) {
      throw new IOException(subject + " is not valid UTF-8", e);
    }
  }

  /**
   * Applies one pax record. Only {@code path} and {@code size} change what this reader
   * reports, so other keywords are ignored, except sparse entries, which cannot be
   * unpacked.
   *
   * @param keyword The record's keyword.
   * @param value The record's value.
   * @param global Whether the record came from a global header.
   * @throws IOException Thrown if the entry is sparse, if a global header contains a
   *         keyword that would change the entries after it, or if {@code size} is not a
   *         number.
   */
  private void apply(String keyword, String value, boolean global) throws IOException {
    if (keyword.startsWith(SPARSE_PREFIX)) {
      throw new IOException("sparse tar entries are not supported: "
          + "the archived bytes describe file holes, not contiguous content");
    }
    if (!KEYWORD_PATH.equals(keyword) && !KEYWORD_SIZE.equals(keyword)) {
      return;
    }
    if (global) {
      throw new IOException("pax global header contains " + keyword
          + ", which would change every entry after it");
    }
    if (KEYWORD_PATH.equals(keyword)) {
      pendingPath = value;
      return;
    }
    try {
      pendingSize = Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IOException("pax size record is not a number: " + value, e);
    }
    if (pendingSize < 0) {
      throw new IOException("pax size record is negative: " + value);
    }
  }

  /**
   * Drops everything from the first NUL onward, which is how a GNU long-name header
   * terminates the name it contains.
   *
   * @param value The decoded payload.
   * @return The name without its terminator. Not {@code null}.
   */
  private static String trimNul(String value) {
    final int nul = value.indexOf('\0');
    return nul < 0 ? value : value.substring(0, nul);
  }

  /**
   * Reads the current header's entry name. On a POSIX ustar header a non-empty name
   * prefix is joined to the name field with {@code /}, which is how a name longer than
   * the 100-byte name field is stored when no extension header contains it.
   *
   * @return The entry name. Not {@code null}.
   * @throws IOException Thrown if the stored name or prefix is not valid UTF-8.
   */
  private String readName() throws IOException {
    final String stored = field(0, NAME_LENGTH, "tar entry name");
    if (!hasPosixUstarMagic(header)) {
      return stored;
    }
    final String prefix = field(PREFIX_OFFSET, PREFIX_LENGTH, "tar entry name prefix");
    return prefix.isEmpty() ? stored : prefix + "/" + stored;
  }

  /**
   * Reads a NUL-terminated text field of the current header.
   *
   * @param offset The field's offset in the header block.
   * @param length The field's length in bytes.
   * @param subject The field name to use in an error message.
   * @return The field content up to its first NUL, decoded as UTF-8. Not {@code null}.
   * @throws IOException Thrown if the field is not valid UTF-8.
   */
  private String field(int offset, int length, String subject) throws IOException {
    int end = 0;
    while (end < length && header[offset + end] != 0) {
      end++;
    }
    return decodeUtf8(header, offset, end, subject);
  }

  /**
   * @return The current entry's name as stored in the archive. Not {@code null}
   *         after a successful {@link #next()}.
   */
  public String name() {
    return name;
  }

  /**
   * @return The current entry's size in bytes.
   */
  public long size() {
    return size;
  }

  /**
   * @return {@code true} if the current entry is a regular file.
   */
  public boolean isFile() {
    return type == TYPE_REGULAR_FILE || type == TYPE_REGULAR_FILE_CLASSIC;
  }

  /**
   * Opens the current entry's content.
   *
   * @return A stream over exactly this entry's bytes; reading past the end returns end
   *         of stream, and a zero-length read returns {@code 0} as
   *         {@link InputStream#read(byte[], int, int)} requires. Not {@code null}.
   *         Closing it is not required.
   */
  public InputStream entryStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        if (remaining <= 0) {
          return -1;
        }
        final int b = in.read();
        if (b < 0) {
          throw new IOException("truncated tar entry: " + name);
        }
        remaining--;
        return b;
      }

      /**
       * {@inheritDoc}
       *
       * <p>The range is checked before the entry state, so invalid arguments are reported
       * when the entry is exhausted or the requested length is zero. This override uses
       * the exceptions specified by {@link InputStream}.</p>
       *
       * @throws NullPointerException Thrown if {@code buffer} is {@code null}.
       * @throws IndexOutOfBoundsException Thrown if {@code offset} or {@code length} is
       *         negative, or {@code length} is greater than
       *         {@code buffer.length - offset}.
       */
      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) {
          return 0;
        }
        if (remaining <= 0) {
          return -1;
        }
        final int read = in.read(buffer, offset, (int) Math.min(length, remaining));
        if (read < 0) {
          throw new IOException("truncated tar entry: " + name);
        }
        remaining -= read;
        return read;
      }
    };
  }

  /**
   * Checks whether a full 512-byte block reads as a tar entry header, which it does when
   * it starts with a name and its stored checksum matches the block. Both classic and
   * ustar headers contain that checksum, so it identifies both without relying on the
   * ustar magic, which arbitrary content can also contain.
   *
   * @param block The block to inspect. Must be 512 bytes long.
   * @return {@code true} if the block reads as a tar header.
   */
  private static boolean isHeader(byte[] block) {
    return block[0] != 0 && hasValidChecksum(block);
  }

  /**
   * Verifies a header block against the checksum stored in it. The checksum is the sum
   * of every header byte with the checksum field itself read as eight blanks. Historical
   * writers used signed-byte sums, so both totals are accepted.
   *
   * @param block The header block to verify. Must be 512 bytes long.
   * @return {@code true} if the stored checksum is well formed and matches the block.
   */
  private static boolean hasValidChecksum(byte[] block) {
    long stored = 0;
    boolean digits = false;
    boolean trailingPadding = false;
    for (int i = CHECKSUM_OFFSET; i < CHECKSUM_OFFSET + CHECKSUM_LENGTH; i++) {
      final byte b = block[i];
      if (b == 0 || b == ' ') {
        if (digits) {
          trailingPadding = true;
        }
        continue;
      }
      if (!isOctalDigit(b) || trailingPadding) {
        return false;
      }
      stored = stored * 8 + (b - '0');
      digits = true;
    }
    if (!digits) {
      return false;
    }
    int unsigned = 0;
    int signed = 0;
    for (int i = 0; i < BLOCK; i++) {
      final byte b = i >= CHECKSUM_OFFSET && i < CHECKSUM_OFFSET + CHECKSUM_LENGTH
          ? (byte) ' ' : block[i];
      unsigned += b & 0xFF;
      signed += b;
    }
    return stored == unsigned || stored == signed;
  }

  /**
   * Checks for the POSIX ustar magic specifically, which is {@code "ustar"} followed by a
   * NUL. GNU writes {@code "ustar"} followed by two blanks and a NUL in the same field,
   * and its headers must not be read as ustar: GNU stores {@code atime} at the offset
   * ustar gives to the name prefix, so a GNU incremental archive would otherwise deliver
   * every entry under a directory named after an octal timestamp.
   *
   * @param block The block to inspect. Must be 512 bytes long.
   * @return {@code true} if the block contains the POSIX ustar magic.
   */
  private static boolean hasPosixUstarMagic(byte[] block) {
    for (int i = 0; i < USTAR_MAGIC.length(); i++) {
      if (block[MAGIC_OFFSET + i] != USTAR_MAGIC.charAt(i)) {
        return false;
      }
    }
    return block[MAGIC_OFFSET + USTAR_MAGIC.length()] == 0;
  }

  /**
   * @param b The byte to classify.
   * @return {@code true} if the byte is one of the digits {@code 0} to {@code 7}.
   */
  private static boolean isOctalDigit(byte b) {
    return b >= '0' && b <= '7';
  }

  /**
   * Fills the header buffer with the next 512-byte block.
   *
   * @return {@code true} when a full block was read, {@code false} at a clean end of
   *         the stream before any byte of the block.
   * @throws IOException Thrown if the stream ends inside the block.
   */
  private boolean readBlock() throws IOException {
    final int filled = in.readNBytes(header, 0, header.length);
    if (filled == 0) {
      return false;
    }
    if (filled < header.length) {
      throw new IOException("truncated tar header");
    }
    return true;
  }

  /**
   * @return {@code true} if the current header buffer is one of the all-zero blocks
   *         that terminate a tar archive.
   */
  private boolean isEndBlock() {
    for (final byte b : header) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Parses the size field of the current header, tolerating NUL and blank padding around
   * the octal digits. A field with its leading bit set is in the base-256 encoding
   * instead, and is read by {@link #parseBase256Size()}.
   *
   * @return The entry size in bytes.
   * @throws IOException Thrown if the field contains a character that is not an octal
   *         digit, a blank, or NUL padding, or if the base-256 form is negative or too
   *         large for a {@code long}.
   */
  private long parseSize() throws IOException {
    if ((header[SIZE_OFFSET] & BASE_256_MARKER) != 0) {
      return parseBase256Size();
    }
    long value = 0;
    boolean digitSeen = false;
    boolean trailingPadding = false;
    for (int i = SIZE_OFFSET; i < SIZE_OFFSET + SIZE_LENGTH; i++) {
      final byte b = header[i];
      if (b == 0 || b == ' ') {
        if (digitSeen) {
          trailingPadding = true;
        }
        continue;
      }
      if (!isOctalDigit(b) || trailingPadding) {
        throw new IOException("malformed tar size field in entry header");
      }
      digitSeen = true;
      value = value * 8 + (b - '0');
    }
    return value;
  }

  /**
   * Reads a size field in the base-256 encoding, which GNU writes when a value does not
   * fit the 11 octal digits the field otherwise contains, so entries of 8 GiB and above
   * can state their length.
   *
   * <p>The leading bit marks the encoding, the next bit is the sign, and the remaining
   * bits of that byte followed by all later bytes form a big-endian two's complement
   * number. Negative sizes are rejected.</p>
   *
   * @return The entry size in bytes.
   * @throws IOException Thrown if the encoded value is negative or does not fit a
   *         {@code long}.
   */
  private long parseBase256Size() throws IOException {
    if ((header[SIZE_OFFSET] & BASE_256_NEGATIVE) != 0) {
      throw new IOException("tar size field is negative");
    }
    long value = 0;
    for (int i = SIZE_OFFSET; i < SIZE_OFFSET + SIZE_LENGTH; i++) {
      final int b = i == SIZE_OFFSET
          ? header[i] & BASE_256_FIRST_BYTE_BITS : header[i] & 0xFF;
      if (value >>> (Long.SIZE - Byte.SIZE - 1) != 0) {
        throw new IOException(
            "tar size field exceeds the largest length this reader can represent");
      }
      value = value << Byte.SIZE | b;
    }
    return value;
  }

  /**
   * @param entrySize The size of an entry's content in bytes.
   * @return The number of padding bytes that align the entry to the next 512-byte
   *         block boundary.
   */
  private long padding(long entrySize) {
    final long remainder = entrySize % BLOCK;
    return remainder == 0 ? 0 : BLOCK - remainder;
  }

  /**
   * Consumes and discards the given number of bytes from the underlying stream.
   *
   * @param bytes The number of bytes to discard.
   * @throws IOException Thrown if the stream ends before all bytes were consumed.
   */
  private void skip(long bytes) throws IOException {
    try {
      in.skipNBytes(bytes);
    } catch (EOFException e) {
      throw new IOException("truncated tar archive", e);
    }
  }
}
