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

/**
 * A {@link ZipInputStream} that fails once the archive inflates to more than
 * {@link #MAX_DECOMPRESSED_BYTES} bytes, or once the inflated bytes exceed the bytes read
 * from the archive by more than {@link #MAX_COMPRESSION_RATIO}. The ratio is not checked for
 * archives that inflate to less than {@value #UNCHECKED_BYTES} bytes.
 */
final class BoundedZipInputStream extends ZipInputStream {

  /**
   * System property for overriding {@link #MAX_COMPRESSION_RATIO}.
   * Set at JVM startup, e.g. {@code -Dopennlp.max.compression.ratio=200}.
   * Falls back to {@code 100} if absent or invalid.
   */
  static final String MAX_COMPRESSION_RATIO_PROPERTY = "opennlp.max.compression.ratio";

  /**
   * Upper bound on the ratio between the inflated and the compressed size of a model
   * package. Published models stay well below 10:1.
   */
  static final int MAX_COMPRESSION_RATIO =
      (int) initLimit(MAX_COMPRESSION_RATIO_PROPERTY, 100, Integer.MAX_VALUE);

  /**
   * System property for overriding {@link #MAX_DECOMPRESSED_BYTES}.
   * Set at JVM startup, e.g. {@code -Dopennlp.max.decompressed.bytes=4294967296}.
   * Falls back to 2 GiB if absent or invalid.
   */
  static final String MAX_DECOMPRESSED_BYTES_PROPERTY = "opennlp.max.decompressed.bytes";

  /**
   * Upper bound on the bytes a model package may inflate to in total. Published models
   * inflate to less than 100 MiB.
   */
  static final long MAX_DECOMPRESSED_BYTES =
      initLimit(MAX_DECOMPRESSED_BYTES_PROPERTY, 2L * 1024 * 1024 * 1024, Long.MAX_VALUE);

  static final long UNCHECKED_BYTES = 16L * 1024 * 1024;

  private final CountingInputStream archive;
  private final long maxDecompressedBytes;
  private final int maxCompressionRatio;
  private long inflated;

  /**
   * Initializes a stream with the limits configured through the system properties.
   *
   * @param in The model package to read.
   */
  BoundedZipInputStream(InputStream in) {
    this(in, MAX_DECOMPRESSED_BYTES, MAX_COMPRESSION_RATIO);
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
   * Reads a positive limit from a system property.
   *
   * @param property The system property name.
   * @param defaultValue The value used when the property is absent or invalid.
   * @param maxValue The largest accepted value.
   * @return The configured limit, or {@code defaultValue}.
   */
  private static long initLimit(String property, long defaultValue, long maxValue) {
    final String prop = System.getProperty(property, "").trim();
    if (!prop.isEmpty()) {
      try {
        final long val = Long.parseLong(prop);
        if (val > 0 && val <= maxValue) {
          return val;
        }
      } catch (NumberFormatException ignore) {
        // Use the default value.
      }
    }
    return defaultValue;
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
            + MAX_DECOMPRESSED_BYTES_PROPERTY + "=<n>.");
      }
      if (inflated > UNCHECKED_BYTES && inflated > archive.count * maxCompressionRatio) {
        throw new InvalidFormatException("Model package exceeds the maximum compression ratio of "
            + maxCompressionRatio + " (" + archive.count + " bytes read, "
            + inflated + " bytes inflated). The limit can be raised via -D"
            + MAX_COMPRESSION_RATIO_PROPERTY + "=<n>.");
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
