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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * Builds tar archives byte by byte, so tests can assemble well-formed, boundary, and
 * malformed archives without an extra library. Each header has the ustar
 * magic and a valid checksum, as a real archive does; {@link #reseal(byte[])} restores
 * the checksum after a test has corrupted some other field on purpose.
 */
public final class TarArchives {

  /** The tar block size; headers, contents, and terminators are multiples of it. */
  public static final int BLOCK = 512;

  /** The size of the two all-zero blocks that terminate a tar archive. */
  public static final int TERMINATOR_SIZE = 2 * BLOCK;

  /** The type flag of a regular file. */
  public static final char TYPE_REGULAR_FILE = '0';

  /** The type flag an old-style archive leaves NUL for a regular file. */
  public static final char TYPE_REGULAR_FILE_CLASSIC = '\0';

  /** The type flag of a directory. */
  public static final char TYPE_DIRECTORY = '5';

  /** The offset of the 155-byte ustar name prefix field. */
  public static final int PREFIX_OFFSET = 345;

  private static final int NAME_LENGTH = 100;
  private static final int PREFIX_LENGTH = 155;
  private static final int MODE_OFFSET = 100;
  private static final int SIZE_OFFSET = 124;
  private static final int SIZE_LENGTH = 12;
  private static final int BASE_256_MARKER = 0x80;
  private static final int BASE_256_NEGATIVE = 0x40;
  private static final int CHECKSUM_OFFSET = 148;
  private static final int CHECKSUM_LENGTH = 8;
  private static final int TYPE_OFFSET = 156;
  private static final int MAGIC_OFFSET = 257;
  private static final int BLANK = ' ';
  private static final String SIZE_FORMAT = "%011o";
  private static final String CHECKSUM_FORMAT = "%06o";
  private static final String MODE = "0000644 ";

  /** The ustar magic and version fields: {@code "ustar"}, NUL, then {@code "00"}. */
  private static final byte[] USTAR_MAGIC = {'u', 's', 't', 'a', 'r', 0, '0', '0'};

  /** The GNU magic and version fields: {@code "ustar"}, two blanks, then NUL. */
  private static final byte[] GNU_MAGIC = {'u', 's', 't', 'a', 'r', ' ', ' ', 0};

  private TarArchives() {
  }

  /**
   * Builds one 512-byte tar header block with the given name, declared content size,
   * and type flag. The mode field is populated with a non-zero octal value so that a
   * name filling the whole 100-byte name field is followed by non-zero bytes, which
   * makes name-boundary tests meaningful.
   *
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param size The content size to declare in the octal size field; must not be
   *             negative.
   * @param typeFlag The tar type flag, for example {@link #TYPE_REGULAR_FILE}.
   * @return The header block. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the name does not fit the name field or
   *         the size is negative.
   */
  public static byte[] header(String name, long size, char typeFlag) {
    return header(name, "", size, typeFlag, true);
  }

  /**
   * Builds one 512-byte classic v7 header block, which carries no ustar magic and no
   * name prefix field. Real archives in this format still exist, and a reader that keys
   * off the magic rather than the checksum will not see them.
   *
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param size The content size to declare in the octal size field; must not be
   *             negative.
   * @param typeFlag The tar type flag, for example {@link #TYPE_REGULAR_FILE}.
   * @return The header block. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the name does not fit the name field or
   *         the size is negative.
   */
  public static byte[] classicHeader(String name, long size, char typeFlag) {
    return header(name, "", size, typeFlag, false);
  }

  /**
   * Builds one 512-byte GNU header block. GNU writes {@code "ustar"} followed by two
   * blanks and a NUL where ustar writes {@code "ustar"}, a NUL, and the version, and it
   * uses the offset ustar gives to the name prefix for {@code atime} instead.
   *
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param size The content size to declare in the octal size field; must not be
   *             negative.
   * @param typeFlag The tar type flag, for example {@link #TYPE_REGULAR_FILE}.
   * @param atime The octal {@code atime} to write at {@link #PREFIX_OFFSET}, as a GNU
   *              incremental archive does, or empty to leave it NUL.
   * @return The header block. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the name does not fit the name field or
   *         the size is negative.
   */
  public static byte[] gnuHeader(String name, long size, char typeFlag, String atime) {
    final byte[] block = header(name, "", size, typeFlag, false);
    System.arraycopy(GNU_MAGIC, 0, block, MAGIC_OFFSET, GNU_MAGIC.length);
    write(block, PREFIX_OFFSET, atime);
    return reseal(block);
  }

  /**
   * Builds one 512-byte header block whose size field uses the base-256 encoding, which
   * GNU writes when a length does not fit the eleven octal digits the field holds.
   *
   * <p>The leading bit marks the encoding, the next bit carries the sign, and the
   * remaining bits of that byte followed by every later byte form a big-endian two's
   * complement number.</p>
   *
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param size The content size to encode. May be negative, which no real writer emits
   *             for a size, so that the reader's rejection of it can be exercised.
   * @param typeFlag The tar type flag, for example {@link #TYPE_REGULAR_FILE}.
   * @return The header block. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the name does not fit the name field.
   */
  public static byte[] base256Header(String name, long size, char typeFlag) {
    final byte[] block = header(name, 0, typeFlag);
    long remaining = size;
    for (int i = SIZE_OFFSET + SIZE_LENGTH - 1; i >= SIZE_OFFSET; i--) {
      block[i] = (byte) (remaining & 0xFF);
      remaining >>= Byte.SIZE;
    }
    block[SIZE_OFFSET] = (byte) (size < 0
        ? block[SIZE_OFFSET] | BASE_256_MARKER | BASE_256_NEGATIVE
        : block[SIZE_OFFSET] & ~BASE_256_NEGATIVE | BASE_256_MARKER);
    return reseal(block);
  }

  /**
   * Encodes one pax extended header record, {@code "<length> <keyword>=<value>\n"}. The
   * length includes all record bytes: digits, the blank, and the newline.
   *
   * @param keyword The pax keyword.
   * @param value The keyword's value.
   * @return The encoded record. Never {@code null}.
   */
  public static byte[] paxRecord(String keyword, String value) {
    final byte[] body = (keyword + "=" + value + "\n").getBytes(StandardCharsets.UTF_8);
    int digits = 1;
    while (Integer.toString(body.length + 1 + digits).length() > digits) {
      digits++;
    }
    final byte[] prefix = ((body.length + 1 + digits) + " ")
        .getBytes(StandardCharsets.US_ASCII);
    final byte[] record = new byte[prefix.length + body.length];
    System.arraycopy(prefix, 0, record, 0, prefix.length);
    System.arraycopy(body, 0, record, prefix.length, body.length);
    return record;
  }

  /**
   * Builds one 512-byte ustar header block whose entry name is split across the name
   * prefix field and the name field, which is how a real archive stores a name longer
   * than 100 bytes.
   *
   * @param name The name field content; at most 100 bytes when encoded as UTF-8.
   * @param prefix The name prefix field content; at most 155 bytes when encoded as
   *               UTF-8. Empty for a header without a prefix.
   * @param size The content size to declare in the octal size field; must not be
   *             negative.
   * @param typeFlag The tar type flag, for example {@link #TYPE_REGULAR_FILE}.
   * @return The header block. Never {@code null}.
   * @throws IllegalArgumentException Thrown if either name part does not fit its field
   *         or the size is negative.
   */
  public static byte[] header(String name, String prefix, long size, char typeFlag) {
    return header(name, prefix, size, typeFlag, true);
  }

  /**
   * Builds one 512-byte header block, in either the ustar or the classic v7 format.
   *
   * @param name The name field content; at most 100 bytes when encoded as UTF-8.
   * @param prefix The name prefix field content; at most 155 bytes when encoded as
   *               UTF-8. Empty for a header without a prefix, and required to be empty
   *               for a classic header, which has no prefix field.
   * @param size The content size to declare in the octal size field; must not be
   *             negative.
   * @param typeFlag The tar type flag, for example {@link #TYPE_REGULAR_FILE}.
   * @param ustar Whether to write the ustar magic and version fields.
   * @return The header block. Never {@code null}.
   * @throws IllegalArgumentException Thrown if either name part does not fit its field,
   *         the size is negative, or a classic header is asked for with a prefix.
   */
  private static byte[] header(String name, String prefix, long size, char typeFlag,
      boolean ustar) {
    final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    if (nameBytes.length > NAME_LENGTH) {
      throw new IllegalArgumentException(
          "entry name exceeds " + NAME_LENGTH + " bytes: " + name);
    }
    final byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
    if (prefixBytes.length > PREFIX_LENGTH) {
      throw new IllegalArgumentException(
          "entry name prefix exceeds " + PREFIX_LENGTH + " bytes: " + prefix);
    }
    if (size < 0) {
      throw new IllegalArgumentException("size must not be negative: " + size);
    }
    if (!ustar && prefixBytes.length > 0) {
      throw new IllegalArgumentException("a classic header has no name prefix field");
    }
    final byte[] block = new byte[BLOCK];
    System.arraycopy(nameBytes, 0, block, 0, nameBytes.length);
    System.arraycopy(prefixBytes, 0, block, PREFIX_OFFSET, prefixBytes.length);
    write(block, MODE_OFFSET, MODE);
    write(block, SIZE_OFFSET, String.format(SIZE_FORMAT, size));
    if (ustar) {
      System.arraycopy(USTAR_MAGIC, 0, block, MAGIC_OFFSET, USTAR_MAGIC.length);
    }
    block[TYPE_OFFSET] = (byte) typeFlag;
    return reseal(block);
  }

  /**
   * Recomputes and writes the header checksum of the given block, so a test can corrupt
   * a field on purpose and still hand the reader an otherwise well-formed header.
   *
   * @param block The header block to seal. Must be 512 bytes long.
   * @return The same block, with its checksum field filled in. Never {@code null}.
   */
  public static byte[] reseal(byte[] block) {
    for (int i = CHECKSUM_OFFSET; i < CHECKSUM_OFFSET + CHECKSUM_LENGTH; i++) {
      block[i] = BLANK;
    }
    int sum = 0;
    for (final byte b : block) {
      sum += b & 0xFF;
    }
    write(block, CHECKSUM_OFFSET, String.format(CHECKSUM_FORMAT, sum));
    block[CHECKSUM_OFFSET + CHECKSUM_LENGTH - 1] = BLANK;
    return block;
  }

  /**
   * Writes US-ASCII text into a header field.
   *
   * @param block The header block to write into.
   * @param offset The field's offset in the block.
   * @param text The text to write.
   */
  private static void write(byte[] block, int offset, String text) {
    final byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(bytes, 0, block, offset, bytes.length);
  }

  /**
   * Writes one complete tar entry into the given buffer: the header block declaring the
   * content's actual length, the content itself, and zero padding up to the next
   * 512-byte block boundary.
   *
   * @param tar The buffer receiving the entry bytes. Must not be {@code null}.
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param content The entry content. Must not be {@code null}.
   * @param typeFlag The tar type flag for the header.
   * @throws IOException Thrown if writing to the buffer fails.
   * @throws IllegalArgumentException Thrown if the name does not fit the name field.
   */
  public static void entry(ByteArrayOutputStream tar, String name, byte[] content,
      char typeFlag) throws IOException {
    tar.write(header(name, content.length, typeFlag));
    tar.write(content);
    tar.write(new byte[(BLOCK - content.length % BLOCK) % BLOCK]);
  }

  /**
   * Writes one complete regular-file tar entry into the given buffer.
   *
   * @param tar The buffer receiving the entry bytes. Must not be {@code null}.
   * @param name The entry name; at most 100 bytes when encoded as UTF-8.
   * @param content The entry content. Must not be {@code null}.
   * @throws IOException Thrown if writing to the buffer fails.
   * @throws IllegalArgumentException Thrown if the name does not fit the name field.
   */
  public static void entry(ByteArrayOutputStream tar, String name, byte[] content)
      throws IOException {
    entry(tar, name, content, TYPE_REGULAR_FILE);
  }

  /**
   * Builds a gzip-compressed tar archive from name and content pairs, terminated by
   * the two all-zero blocks that end a tar archive.
   *
   * @param entries The entries as {@code {name, content}} pairs of UTF-8 text. Must not
   *                be {@code null}.
   * @return The compressed archive bytes. Never {@code null}.
   * @throws IOException Thrown if writing to the in-memory streams fails.
   */
  public static byte[] gzippedTar(String[][] entries) throws IOException {
    final ByteArrayOutputStream tar = new ByteArrayOutputStream();
    for (final String[] entry : entries) {
      entry(tar, entry[0], entry[1].getBytes(StandardCharsets.UTF_8));
    }
    tar.write(new byte[TERMINATOR_SIZE]);
    return gzip(tar.toByteArray());
  }

  /**
   * Compresses raw tar bytes the way a {@code .tar.gz} distribution is shipped, so
   * tests can wrap hand-built or deliberately truncated tar content.
   *
   * @param content The raw tar bytes. Must not be {@code null}.
   * @return The gzip-compressed bytes. Never {@code null}.
   * @throws IOException Thrown if writing to the in-memory stream fails.
   */
  public static byte[] gzip(byte[] content) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream compressed = new GZIPOutputStream(out)) {
      compressed.write(content);
    }
    return out.toByteArray();
  }
}
