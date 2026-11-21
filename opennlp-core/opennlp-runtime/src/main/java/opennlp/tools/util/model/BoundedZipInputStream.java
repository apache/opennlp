/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.util.model;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipInputStream;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ResourceLimits;

/**
 * A {@link ZipInputStream} that fails once the archive inflates to more than
 * {@link ResourceLimits#MAX_DECOMPRESSED_BYTES} bytes, or once the inflated bytes exceed
 * the bytes read from the archive by more than {@link ResourceLimits#MAX_COMPRESSION_RATIO}.
 * The ratio is not checked for archives that inflate to less than {@value #UNCHECKED_BYTES}
 * bytes.
 */
final class BoundedZipInputStream extends ZipInputStream {

  static final long UNCHECKED_BYTES = 16L * 1024 * 1024;

  private final CountingInputStream archive;
  private final long maxDecompressedBytes;
  private final int maxCompressionRatio;
  private long inflated;

  /**
   * Initializes a stream with the limits of {@link ResourceLimits}.
   *
   * @param in The model package to read.
   */
  BoundedZipInputStream(InputStream in) {
    this(in, ResourceLimits.MAX_DECOMPRESSED_BYTES, ResourceLimits.MAX_COMPRESSION_RATIO);
  }

  /**
   * Initializes a stream with the given limits.
   *
   * @param in The model package to read.
   * @param maxDecompressedBytes The maximum number of bytes the archive may inflate to.
   * @param maxCompressionRatio The maximum ratio of inflated to read bytes.
   */
  BoundedZipInputStream(InputStream in, long maxDecompressedBytes, int maxCompressionRatio) {
    this(new CountingInputStream(in), maxDecompressedBytes, maxCompressionRatio);
  }

  /**
   * Initializes a stream over an archive whose read bytes are counted.
   *
   * @param archive The counted model package.
   * @param maxDecompressedBytes The maximum number of bytes the archive may inflate to.
   * @param maxCompressionRatio The maximum ratio of inflated to read bytes.
   */
  private BoundedZipInputStream(CountingInputStream archive, long maxDecompressedBytes,
      int maxCompressionRatio) {
    super(archive);
    this.archive = archive;
    this.maxDecompressedBytes = maxDecompressedBytes;
    this.maxCompressionRatio = maxCompressionRatio;
  }

  /**
   * {@inheritDoc}
   *
   * @throws InvalidFormatException Thrown if the archive inflates beyond one of the limits.
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    final int n = super.read(b, off, len);
    if (n > 0) {
      inflated += n;
      if (inflated > maxDecompressedBytes) {
        throw new InvalidFormatException("Model package exceeds the maximum decompressed size of "
            + maxDecompressedBytes + " bytes. The limit can be raised via -D"
            + ResourceLimits.MAX_DECOMPRESSED_BYTES_PROPERTY + "=<n>.");
      }
      if (inflated > UNCHECKED_BYTES && inflated > archive.count * maxCompressionRatio) {
        throw new InvalidFormatException("Model package exceeds the maximum compression ratio of "
            + maxCompressionRatio + " (" + archive.count + " bytes read, "
            + inflated + " bytes inflated). The limit can be raised via -D"
            + ResourceLimits.MAX_COMPRESSION_RATIO_PROPERTY + "=<n>.");
      }
    }
    return n;
  }

  /**
   * Counts the bytes read from the wrapped stream.
   */
  private static final class CountingInputStream extends FilterInputStream {

    private long count;

    /**
     * @param in The stream to count.
     */
    private CountingInputStream(InputStream in) {
      super(in);
    }

    /** {@inheritDoc} */
    @Override
    public int read() throws IOException {
      final int b = super.read();
      if (b != -1) {
        count++;
      }
      return b;
    }

    /** {@inheritDoc} */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      final int n = super.read(b, off, len);
      if (n > 0) {
        count += n;
      }
      return n;
    }

    /** {@inheritDoc} */
    @Override
    public long skip(long n) throws IOException {
      final long skipped = super.skip(n);
      count += skipped;
      return skipped;
    }
  }
}
