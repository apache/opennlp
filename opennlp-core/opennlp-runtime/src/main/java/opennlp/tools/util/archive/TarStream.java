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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import opennlp.tools.commons.Internal;

/**
 * A forward-only reader over a tar stream: {@link #next()} advances to the following
 * entry, skipping whatever remains of the current one, and {@link #entryStream()}
 * exposes exactly the current entry's bytes. No external archive library is involved.
 *
 * <p>Reads classic and ustar headers: entry name, octal size, and type flag. Long-name
 * extensions are not interpreted; such entries appear under their extension header
 * names and are skippable like any other.</p>
 */
@Internal
public final class TarStream {

  private static final int BLOCK = 512;
  private static final int NAME_LENGTH = 100;
  private static final int SIZE_OFFSET = 124;
  private static final int SIZE_LENGTH = 12;
  private static final int TYPE_OFFSET = 156;

  private final InputStream in;
  private final byte[] header = new byte[BLOCK];

  private String name;
  private long size;
  private char type;
  private long remaining;

  /**
   * Initializes the reader.
   *
   * @param in The tar content. Must not be {@code null}. Not closed by this class.
   * @throws IllegalArgumentException Thrown if {@code in} is {@code null}.
   */
  public TarStream(InputStream in) {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    this.in = in;
  }

  /**
   * Advances to the next entry.
   *
   * @return {@code true} if an entry is available, {@code false} at the end of the
   *         archive.
   * @throws IOException Thrown if the archive is truncated or a header is malformed.
   */
  public boolean next() throws IOException {
    skip(remaining + padding(size));
    if (!readBlock()) {
      return false;
    }
    if (isEndBlock()) {
      return false;
    }
    int nameEnd = 0;
    while (nameEnd < NAME_LENGTH && header[nameEnd] != 0) {
      nameEnd++;
    }
    name = new String(header, 0, nameEnd, StandardCharsets.UTF_8);
    size = parseOctal();
    remaining = size;
    type = (char) header[TYPE_OFFSET];
    return true;
  }

  /**
   * @return The current entry's name as stored in the archive. Never {@code null}
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
    return type == '0' || type == 0;
  }

  /**
   * Opens the current entry's content.
   *
   * @return A stream over exactly this entry's bytes; reading past the end returns end
   *         of stream. Never {@code null}. Closing it is not required.
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

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
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

  private boolean readBlock() throws IOException {
    int filled = 0;
    while (filled < header.length) {
      final int read = in.read(header, filled, header.length - filled);
      if (read < 0) {
        if (filled == 0) {
          return false;
        }
        throw new IOException("truncated tar header");
      }
      filled += read;
    }
    return true;
  }

  private boolean isEndBlock() {
    for (final byte b : header) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  private long parseOctal() throws IOException {
    long value = 0;
    for (int i = SIZE_OFFSET; i < SIZE_OFFSET + SIZE_LENGTH; i++) {
      final byte b = header[i];
      if (b == 0 || b == ' ') {
        continue;
      }
      if (b < '0' || b > '7') {
        throw new IOException("malformed tar size field in entry header");
      }
      value = value * 8 + (b - '0');
    }
    return value;
  }

  private long padding(long entrySize) {
    final long remainder = entrySize % BLOCK;
    return remainder == 0 ? 0 : BLOCK - remainder;
  }

  private void skip(long bytes) throws IOException {
    long toSkip = bytes;
    final byte[] buffer = new byte[8192];
    while (toSkip > 0) {
      final int read = in.read(buffer, 0, (int) Math.min(buffer.length, toSkip));
      if (read < 0) {
        throw new IOException("truncated tar archive");
      }
      toSkip -= read;
    }
  }
}
