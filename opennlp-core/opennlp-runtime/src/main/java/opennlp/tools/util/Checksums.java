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

package opennlp.tools.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes and validates the hex-encoded file digests that downloads are verified
 * against.
 */
final class Checksums {

  /** The SHA-256 algorithm name. */
  static final String SHA_256 = "SHA-256";

  /** The SHA-512 algorithm name. */
  static final String SHA_512 = "SHA-512";

  /** The number of hex digits in a SHA-256 digest. */
  static final int SHA_256_HEX_LENGTH = 64;

  /** The number of hex digits in a SHA-512 digest. */
  static final int SHA_512_HEX_LENGTH = 128;

  /** Prevents construction of this utility class. */
  private Checksums() {
  }

  /**
   * Computes the digest of a file's bytes.
   *
   * @param file The file to digest.
   * @param algorithm The {@link MessageDigest} algorithm name, for example
   *                  {@link #SHA_512}.
   * @return The digest as lowercase hex digits. Not {@code null}.
   * @throws IOException Thrown if the file cannot be read or the algorithm is not
   *         available in this runtime.
   */
  static String hexDigest(Path file, String algorithm) throws IOException {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(algorithm + " is unavailable in this runtime", e);
    }
    try (InputStream in = Files.newInputStream(file)) {
      final byte[] buffer = new byte[ResourceInstaller.BUFFER_SIZE];
      int read;
      while ((read = in.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  /**
   * Checks that a string is a hex digest of the given length, in either letter case.
   *
   * @param value The string to inspect.
   * @param length The required number of hex digits.
   * @return {@code true} if {@code value} has {@code length} characters and each is a
   *         hex digit.
   */
  static boolean isHexDigest(String value, int length) {
    if (value.length() != length) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      if (!HexFormat.isHexDigit(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
